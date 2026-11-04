package com.example.discordautojoin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Discord Gateway (v10) client built on the JDK's WebSocket, so no extra libraries are needed.
 * All state changes run on one single-threaded executor. Reconnects automatically with backoff.
 */
public class DiscordGateway {
    private static final String URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    public interface Listener {
        void onMessage(String content);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DiscordAutoJoin-Gateway");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private final Listener listener;

    private ModConfig cfg;
    private boolean running;
    private WebSocket ws;
    private Integer lastSeq;
    private String botUserId;
    private boolean ackReceived;
    private ScheduledFuture<?> heartbeat;
    private int attempts;
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

    public volatile String status = "Stopped";

    public DiscordGateway(Listener listener) {
        this.listener = listener;
    }

    public void start(ModConfig config) {
        exec.execute(() -> {
            teardown();
            this.cfg = config;
            if (!config.isUsable()) {
                running = false;
                status = "Not configured (need token, server ID and channel ID)";
                return;
            }
            running = true;
            attempts = 0;
            openConnection();
        });
    }

    public void stop() {
        exec.execute(() -> {
            running = false;
            teardown();
            status = "Stopped";
        });
    }

    // ---- everything below runs on exec ----

    private void teardown() {
        generation.incrementAndGet();
        if (heartbeat != null) heartbeat.cancel(false);
        heartbeat = null;
        if (ws != null) ws.abort();
        ws = null;
        lastSeq = null;
    }

    private void openConnection() {
        final int g = generation.incrementAndGet();
        lastSeq = null;
        status = "Connecting to Discord...";
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(URL), new Handler(g))
                .whenComplete((w, err) -> {
                    if (err != null) {
                        exec.execute(() -> scheduleReconnect(g, "connect failed: " + rootMessage(err)));
                    }
                });
    }

    private void scheduleReconnect(int g, String reason) {
        if (g != generation.get() || !running) return;
        teardown();
        long delay = Math.min(60, 1L << Math.min(attempts++, 6));
        status = "Disconnected (" + reason + "), retrying in " + delay + "s";
        final int epoch = generation.get();
        exec.schedule(() -> {
            if (running && generation.get() == epoch) openConnection();
        }, delay, TimeUnit.SECONDS);
    }

    private void send(String json) {
        final WebSocket w = ws;
        if (w == null) return;
        sendChain = sendChain.handle((v, e) -> null).thenCompose(v -> w.sendText(json, true));
    }

    private void startHeartbeat(int g, int intervalMs) {
        if (heartbeat != null) heartbeat.cancel(false);
        ackReceived = true;
        long initial = (long) (intervalMs * Math.random());
        heartbeat = exec.scheduleAtFixedRate(() -> {
            if (g != generation.get()) return;
            if (!ackReceived) {
                scheduleReconnect(g, "heartbeat timeout");
                return;
            }
            ackReceived = false;
            sendHeartbeat();
        }, initial, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        send("{\"op\":1,\"d\":" + (lastSeq == null ? "null" : lastSeq.toString()) + "}");
    }

    private void identify() {
        int intents = (1) | (1 << 9); // GUILDS | GUILD_MESSAGES
        if (cfg.needsKeyword()) intents |= (1 << 15); // MESSAGE_CONTENT (privileged)
        JsonObject props = new JsonObject();
        props.addProperty("os", "linux");
        props.addProperty("browser", "discordautojoin");
        props.addProperty("device", "discordautojoin");
        JsonObject d = new JsonObject();
        d.addProperty("token", cfg.token.trim());
        d.addProperty("intents", intents);
        d.add("properties", props);
        JsonObject o = new JsonObject();
        o.addProperty("op", 2);
        o.add("d", d);
        send(o.toString());
    }

    private void handle(int g, String text) {
        if (g != generation.get()) return;
        JsonObject o;
        try {
            o = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        int op = o.get("op").getAsInt();
        if (o.has("s") && !o.get("s").isJsonNull()) lastSeq = o.get("s").getAsInt();
        switch (op) {
            case 10 -> {
                int interval = o.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
                startHeartbeat(g, interval);
                identify();
            }
            case 11 -> ackReceived = true;
            case 1 -> sendHeartbeat();
            case 7 -> scheduleReconnect(g, "Discord asked us to reconnect");
            case 9 -> scheduleReconnect(g, "invalid session");
            case 0 -> dispatch(o);
            default -> { }
        }
    }

    private void dispatch(JsonObject o) {
        String t = str(o.get("t"));
        JsonObject d = o.has("d") && o.get("d").isJsonObject() ? o.getAsJsonObject("d") : null;
        if (t == null || d == null) return;
        if (t.equals("READY")) {
            JsonObject user = d.getAsJsonObject("user");
            botUserId = str(user.get("id"));
            attempts = 0;
            status = "Connected as " + str(user.get("username"));
        } else if (t.equals("MESSAGE_CREATE")) {
            if (!cfg.guildId.trim().equals(str(d.get("guild_id")))) return;
            if (!cfg.channelId.trim().equals(str(d.get("channel_id")))) return;
            JsonObject author = d.has("author") && d.get("author").isJsonObject() ? d.getAsJsonObject("author") : null;
            if (author != null && botUserId != null && botUserId.equals(str(author.get("id")))) return;
            String content = str(d.get("content"));
            if (content == null) content = "";
            if (cfg.needsKeyword() && !content.toLowerCase().contains(cfg.keyword.trim().toLowerCase())) return;
            listener.onMessage(content);
        }
    }

    private static String str(JsonElement e) {
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static String fatalReason(int code) {
        return switch (code) {
            case 4004 -> "Discord rejected the bot token";
            case 4013 -> "invalid intents";
            case 4014 -> "MESSAGE CONTENT intent not enabled - turn it on in the Discord developer portal, or use 'any message' mode";
            case 4010, 4011, 4012 -> "gateway refused the connection (code " + code + ")";
            default -> null;
        };
    }

    private class Handler implements WebSocket.Listener {
        private final int g;
        private final StringBuilder buf = new StringBuilder();

        Handler(int g) {
            this.g = g;
        }

        @Override
        public void onOpen(WebSocket w) {
            exec.execute(() -> {
                if (g == generation.get()) ws = w;
                else w.abort();
            });
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                final String msg = buf.toString();
                buf.setLength(0);
                exec.execute(() -> handle(g, msg));
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            exec.execute(() -> {
                if (g != generation.get()) return;
                String fatal = fatalReason(code);
                if (fatal != null) {
                    running = false;
                    teardown();
                    status = "Stopped: " + fatal;
                } else {
                    scheduleReconnect(g, "closed with code " + code);
                }
            });
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            exec.execute(() -> scheduleReconnect(g, "error: " + rootMessage(error)));
        }
    }
}
