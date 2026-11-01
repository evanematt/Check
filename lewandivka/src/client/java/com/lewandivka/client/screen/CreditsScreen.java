package com.lewandivka.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/** The short credits after the epilogue. Esc (or the end of the scroll) returns to the game. */
public final class CreditsScreen extends Screen {

    private static final int LINES = 9;

    private final Screen parent;
    private float scroll;

    public CreditsScreen(Screen parent) {
        super(Text.translatable("credits.lewandivka.title"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void tick() {
        scroll += 0.55f;
        if (scroll > height + LINES * 34 + 60) {
            close();
        }
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        g.fillGradient(0, 0, width, height, 0xE0101018, 0xE0301830);
        float y = height - scroll + delta * 0.55f;
        g.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, (int) y, 0xFFFFFF);
        y += 40;
        for (int i = 1; i <= LINES; i++) {
            int line = (int) y;
            int a = (int) (MathHelper.clamp(Math.min(line / 40.0f, (height - line) / 40.0f), 0, 1) * 255);
            if (a > 8 && line > -12 && line < height) {
                g.drawCenteredTextWithShadow(textRenderer, Text.translatable("credits.lewandivka." + i), width / 2, line, 0x00FFFFFF | (a << 24));
            }
            y += 30;
        }
        g.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.lewandivka.notebook.close"), width / 2, height - 14, 0x909090);
    }
}
