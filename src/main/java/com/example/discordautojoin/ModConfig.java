package com.example.discordautojoin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("discordautojoin.json");

    /** Discord bot token (the bot must be in your server and able to read the channel). */
    public String token = "";
    public String guildId = "";
    public String channelId = "";

    public String serverHost = "";
    public String serverPort = "25565";

    /** If true, any new message in the channel triggers a join. */
    public boolean joinOnAnyMessage = true;
    /** Only used when joinOnAnyMessage is false. Case-insensitive "contains" match. */
    public String keyword = "";

    /** Ignore further messages for this many seconds after a trigger. */
    public int cooldownSeconds = 60;

    /** Play weird noteblock noises after joining a multiplayer server. */
    public boolean noteblockNoises = true;

    public boolean needsKeyword() {
        return !joinOnAnyMessage && keyword != null && !keyword.isBlank();
    }

    public boolean isUsable() {
        return !token.isBlank() && !guildId.isBlank() && !channelId.isBlank();
    }

    public String fullAddress() {
        String host = serverHost.trim();
        String port = serverPort.trim();
        return port.isEmpty() ? host : host + ":" + port;
    }

    public static ModConfig load() {
        try {
            if (Files.exists(PATH)) {
                ModConfig c = GSON.fromJson(Files.readString(PATH), ModConfig.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            DiscordAutoJoinClient.LOGGER.warn("Could not read config, using defaults", e);
        }
        ModConfig c = new ModConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            DiscordAutoJoinClient.LOGGER.warn("Could not save config", e);
        }
    }
}
