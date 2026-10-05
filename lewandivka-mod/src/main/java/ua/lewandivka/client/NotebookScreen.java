package ua.lewandivka.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Районний блокнот: мінімалістичний «пожмаканий папір». */
public class NotebookScreen extends Screen {
    private final String title;
    private final String objective;
    private final String clue;
    private final List<String> done;

    public NotebookScreen(String title, String objective, String clue, List<String> done) {
        super(Text.literal("Районний блокнот"));
        this.title = title;
        this.objective = objective;
        this.clue = clue;
        this.done = done;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int w = 220, h = 190;
        int x = (width - w) / 2, y = (height - h) / 2;
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF5A3E22);
        ctx.fill(x, y, x + w, y + h, 0xFFE9DBB0);
        for (int ly = y + 28; ly < y + h - 4; ly += 11) {
            ctx.fill(x + 8, ly, x + w - 8, ly + 1, 0x22000000);
        }
        int ink = 0xFF2B2113;
        ctx.drawText(textRenderer, Text.literal("Районний блокнот"), x + 8, y + 7, 0xFF8A2A2A, false);
        ctx.drawText(textRenderer, Text.literal(title), x + 8, y + 19, ink, false);
        int ty = y + 33;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(objective), w - 16)) {
            ctx.drawText(textRenderer, line, x + 8, ty, ink, false);
            ty += 10;
        }
        ty += 4;
        if (!clue.isEmpty()) {
            for (OrderedText line : textRenderer.wrapLines(Text.literal("Підказка: " + clue), w - 16)) {
                ctx.drawText(textRenderer, line, x + 8, ty, 0xFF5A4A8A, false);
                ty += 10;
            }
        }
        ty += 6;
        ctx.drawText(textRenderer, Text.literal("Вже зроблено:"), x + 8, ty, 0xFF6B6B6B, false);
        ty += 11;
        int shown = 0;
        for (int i = done.size() - 1; i >= 0 && shown < 5 && ty < y + h - 10; i--, shown++) {
            ctx.drawText(textRenderer, Text.literal("✔ " + done.get(i)), x + 8, ty, 0xFF3D6B3D, false);
            ty += 10;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
