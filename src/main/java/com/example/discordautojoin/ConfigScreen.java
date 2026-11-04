package com.example.discordautojoin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig cfg = DiscordAutoJoinClient.config;

    private TextFieldWidget token, guild, channel, host, port, keyword;
    private ButtonWidget anyBtn, noiseBtn;
    private boolean anyMessage = cfg.joinOnAnyMessage;
    private boolean noises = cfg.noteblockNoises;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Discord AutoJoin"));
        this.parent = parent;
    }

    private TextFieldWidget field(int x, int y, int w, String value, int max) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 20, Text.empty());
        f.setMaxLength(max);
        f.setText(value);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int colW = 170;
        int gap = 10;
        int left = width / 2 - colW - gap / 2;
        int right = width / 2 + gap / 2;
        int y0 = Math.max(40, height / 2 - 100);
        int row = 38;

        token = field(left, y0 + 12, colW, cfg.token, 200);
        // hide the token on screen
        token.setRenderTextProvider((s, i) -> OrderedText.styledForwardsVisitedString("*".repeat(s.length()), Style.EMPTY));
        guild = field(left, y0 + 12 + row, colW, cfg.guildId, 32);
        channel = field(left, y0 + 12 + row * 2, colW, cfg.channelId, 32);

        host = field(right, y0 + 12, colW, cfg.serverHost, 128);
        port = field(right, y0 + 12 + row, colW, cfg.serverPort, 5);
        keyword = field(right, y0 + 12 + row * 2, colW, cfg.keyword, 100);

        int by = y0 + 12 + row * 3;
        anyBtn = addDrawableChild(ButtonWidget.builder(anyLabel(), b -> {
            anyMessage = !anyMessage;
            b.setMessage(anyLabel());
        }).dimensions(left, by, colW, 20).build());
        noiseBtn = addDrawableChild(ButtonWidget.builder(noiseLabel(), b -> {
            noises = !noises;
            b.setMessage(noiseLabel());
        }).dimensions(right, by, colW, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save & reconnect"), b -> {
            save();
            close();
        }).dimensions(left, by + 28, colW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(right, by + 28, colW, 20).build());
    }

    private Text anyLabel() {
        return Text.literal("Join on ANY message: " + (anyMessage ? "ON" : "OFF (use keyword)"));
    }

    private Text noiseLabel() {
        return Text.literal("Noteblock noises: " + (noises ? "ON" : "OFF"));
    }

    private void save() {
        cfg.token = token.getText().trim();
        cfg.guildId = guild.getText().trim();
        cfg.channelId = channel.getText().trim();
        cfg.serverHost = host.getText().trim();
        cfg.serverPort = port.getText().trim();
        cfg.keyword = keyword.getText().trim();
        cfg.joinOnAnyMessage = anyMessage;
        cfg.noteblockNoises = noises;
        cfg.save();
        DiscordAutoJoinClient.gateway.start(cfg);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int colW = 170;
        int gap = 10;
        int left = width / 2 - colW - gap / 2;
        int right = width / 2 + gap / 2;
        int y0 = Math.max(40, height / 2 - 100);
        int row = 38;
        int white = 0xFFFFFFFF;
        int grey = 0xFFAAAAAA;

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, y0 - 22, white);
        ctx.drawTextWithShadow(textRenderer, "Bot token", left, y0, grey);
        ctx.drawTextWithShadow(textRenderer, "Discord server ID", left, y0 + row, grey);
        ctx.drawTextWithShadow(textRenderer, "Discord channel ID", left, y0 + row * 2, grey);
        ctx.drawTextWithShadow(textRenderer, "Minecraft server IP", right, y0, grey);
        ctx.drawTextWithShadow(textRenderer, "Port", right, y0 + row, grey);
        ctx.drawTextWithShadow(textRenderer, "Trigger keyword (if not ANY)", right, y0 + row * 2, grey);
        ctx.drawCenteredTextWithShadow(textRenderer, "Discord: " + DiscordAutoJoinClient.gateway.status,
                width / 2, y0 + 12 + row * 3 + 62, grey);
    }
}
