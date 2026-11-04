package com.example.discordautojoin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class DiscordAutoJoinClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("discordautojoin");

    public static ModConfig config;
    public static DiscordGateway gateway;

    private static final AtomicBoolean PENDING_JOIN = new AtomicBoolean(false);
    private static volatile long lastTriggerMs = 0;

    @Override
    public void onInitializeClient() {
        config = ModConfig.load();
        gateway = new DiscordGateway(DiscordAutoJoinClient::onDiscordMessage);
        gateway.start(config);

        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> gateway.stop());

        // Runs on the client thread; keeps ticking while the window is in the background.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NoteNoises.tick(client);
            if (PENDING_JOIN.getAndSet(false)) joinConfiguredServer(client);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (config.noteblockNoises && !client.isInSingleplayer()) NoteNoises.start();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> NoteNoises.stop());

        // Config access: button on the title screen + pause menu, and /discordautojoin
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Discord AutoJoin"),
                        b -> client.setScreen(new ConfigScreen(screen))).dimensions(6, 6, 110, 20).build());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("discordautojoin").executes(ctx -> {
                    MinecraftClient mc = ctx.getSource().getClient();
                    mc.send(() -> mc.setScreen(new ConfigScreen(null)));
                    return 1;
                })));
    }

    /** Called on the gateway thread. Only flags a join; the actual connect happens on the client thread. */
    private static void onDiscordMessage(String content) {
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0, config.cooldownSeconds) * 1000L;
        if (now - lastTriggerMs < cooldownMs) return; // already triggered recently -> ignore
        lastTriggerMs = now;
        PENDING_JOIN.set(true);
    }

    private static void joinConfiguredServer(MinecraftClient client) {
        if (config.serverHost.isBlank()) return;
        if (client.world != null || client.getNetworkHandler() != null) return; // already in a world/server
        if (client.currentScreen instanceof ConnectScreen) return;              // already connecting

        String address = config.fullAddress();
        LOGGER.info("Discord trigger received, connecting to {}", address);
        Screen parent = client.currentScreen != null ? client.currentScreen : new TitleScreen();
        ServerInfo info = new ServerInfo("Discord AutoJoin", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, ServerAddress.parse(address), info, false, null);
    }
}
