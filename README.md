# Discord AutoJoin (Fabric, client-side, Minecraft 1.21.11)

Watches ONE Discord channel. When a (matching) message arrives, your Minecraft client connects to the server you configured. After you join a multiplayer server it plays chaotic noteblock sounds (only you hear them; toggle in config).

## Build
Requires JDK 21 and Gradle 9.x (or generate the wrapper from the Fabric example mod).

    gradle build

The jar is in `build/libs/discordautojoin-1.0.0.jar`. Put it in `.minecraft/mods/` with Fabric Loader (0.18.1+) and Fabric API.

If Gradle says a version in `gradle.properties` doesn't exist, copy the current 1.21.11 values from https://fabricmc.net/develop.

## Discord setup
1. https://discord.com/developers/applications -> New Application -> Bot -> Reset Token (copy it).
2. Invite the bot to your Discord server (OAuth2 -> URL Generator -> scope `bot`, permission "View Channels"/"Read Message History").
3. Make sure the bot can see the channel.
4. Only if you use a keyword: Bot -> enable **Message Content Intent**. "Join on ANY message" does not need it.
5. Enable Developer Mode in Discord -> right-click server / channel -> Copy ID.

## In-game config
Button "Discord AutoJoin" top-left on the title screen and pause menu, or the `/discordautojoin` command.
Fields: bot token, server ID, channel ID, Minecraft IP, port, keyword, "join on ANY message", noteblock noises.
Config file: `config/discordautojoin.json` (also has `cooldownSeconds`, default 60).

## Behaviour
- Only the configured guild + channel are used; the bot's own messages are ignored.
- After a trigger, further messages are ignored for `cooldownSeconds`; it also won't connect if you're already in a world/server or already connecting.
- Discord connection auto-reconnects with backoff (heartbeat timeout, drops, reconnect requests). Bad token / missing intent stops it and shows the reason in the config screen.
- Works while Minecraft is in the background as long as the game is running and the OS isn't suspending it. The token is stored in plain text in the config file.
