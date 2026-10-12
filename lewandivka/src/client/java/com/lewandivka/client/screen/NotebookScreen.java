package com.lewandivka.client.screen;

import com.lewandivka.client.ClientState;
import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.Reputation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** «Районний блокнот»: the current quest, what was done, the notes that were read and the abilities. */
public final class NotebookScreen extends Screen {

    private enum Tab {
        QUEST, HISTORY, NOTES, ABILITIES
    }

    private static final int PAGE_W = 296;
    private static final int PAGE_H = 186;

    private Tab tab = Tab.QUEST;
    private int notePage;
    private boolean showHint;

    public NotebookScreen() {
        super(Text.translatable("screen.lewandivka.notebook"));
    }

    @Override
    protected void init() {
        int left = (width - PAGE_W) / 2;
        int top = (height - PAGE_H) / 2;
        int y = top + 8;
        for (Tab t : Tab.values()) {
            Text label = Text.translatable("screen.lewandivka.notebook.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
            addDrawableChild(ButtonWidget.builder(label, b -> {
                tab = t;
                showHint = false;
                notePage = 0;
                clearChildren();
                init();
            }).dimensions(left - 84, y, 80, 20).build());
            y += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.lewandivka.notebook.close"), b -> close())
                .dimensions(left - 84, top + PAGE_H - 22, 80, 20).build());
        if (tab == Tab.QUEST) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.lewandivka.notebook.hint"), b -> showHint = !showHint)
                    .dimensions(left + PAGE_W - 86, top + PAGE_H - 26, 78, 20).build());
        }
        if (tab == Tab.NOTES && ClientState.notes.size() > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> notePage = Math.floorMod(notePage - 1, ClientState.notes.size()))
                    .dimensions(left + 12, top + PAGE_H - 26, 24, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> notePage = Math.floorMod(notePage + 1, ClientState.notes.size()))
                    .dimensions(left + 40, top + PAGE_H - 26, 24, 20).build());
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        int left = (width - PAGE_W) / 2;
        int top = (height - PAGE_H) / 2;
        // the page: paper, a darker border and a spine
        g.fill(left - 3, top - 3, left + PAGE_W + 3, top + PAGE_H + 3, 0xFF3A2A1C);
        g.fill(left, top, left + PAGE_W, top + PAGE_H, 0xFFE9DCC0);
        g.fill(left + 3, top + 3, left + 5, top + PAGE_H - 3, 0x30000000);
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook").formatted(Formatting.DARK_RED, Formatting.BOLD), left + 12, top + 8, 0x5A1010, false);
        switch (tab) {
            case QUEST -> quest(g, left, top);
            case HISTORY -> history(g, left, top);
            case NOTES -> notes(g, left, top);
            case ABILITIES -> abilities(g, left, top);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    private void lines(DrawContext g, Text text, int x, int y, int maxWidth, int color) {
        List<OrderedText> wrapped = textRenderer.wrapLines(text, maxWidth);
        int line = 0;
        for (OrderedText t : wrapped) {
            g.drawText(textRenderer, t, x, y + line * 10, color, false);
            line++;
        }
    }

    private void quest(DrawContext g, int left, int top) {
        if (!ClientState.synced) {
            lines(g, Text.translatable("screen.lewandivka.notebook.empty"), left + 12, top + 28, PAGE_W - 24, 0x3A2A1C);
            return;
        }
        QuestStep step = ClientState.step;
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.stage", Text.translatable("stage.lewandivka." + step.stage.key())).formatted(Formatting.DARK_GRAY), left + 12, top + 24, 0x4A4A4A, false);
        g.drawText(textRenderer, Text.translatable(step.titleKey()).formatted(Formatting.DARK_GREEN, Formatting.BOLD), left + 12, top + 40, 0x1A5A2A, false);
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.active"), left + 12, top + 56, 0x7A4A00, false);
        Text objective = step.counterMax > 0
                ? Text.translatable(step.objectiveKey(), ClientState.counter, step.counterMax)
                : Text.translatable(step.objectiveKey());
        lines(g, objective, left + 12, top + 68, PAGE_W - 24, 0x202020);
        if (showHint && LewandivkaConfig.get().questHints) {
            Text hint = Text.translatable(step.hintKey());
            Text shown = hint.getString().isEmpty() ? Text.translatable("screen.lewandivka.notebook.no_hint") : hint;
            lines(g, shown.copy().formatted(Formatting.DARK_BLUE, Formatting.ITALIC), left + 12, top + 104, PAGE_W - 24, 0x203070);
        }
        Reputation rep = Reputation.byLevel(ClientState.reputation);
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.reputation", Text.translatable(rep.langKey())), left + 12, top + PAGE_H - 36, 0x4A4A4A, false);
        g.drawText(textRenderer, Text.translatable("hud.lewandivka.ring", ClientState.rings), left + 12, top + PAGE_H - 22, 0x2A5A7A, false);
    }

    private void history(DrawContext g, int left, int top) {
        if (ClientState.history.isEmpty()) {
            lines(g, Text.translatable("screen.lewandivka.notebook.no_history"), left + 12, top + 28, PAGE_W - 24, 0x3A2A1C);
            return;
        }
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.history").formatted(Formatting.DARK_GRAY), left + 12, top + 24, 0x4A4A4A, false);
        int max = 14;
        int from = Math.max(0, ClientState.history.size() - max);
        int y = top + 38;
        for (int i = from; i < ClientState.history.size(); i++) {
            QuestStep s = QuestStep.byKey(ClientState.history.get(i));
            if (s == null) {
                continue;
            }
            g.drawText(textRenderer, Text.literal("✓ ").append(Text.translatable(s.titleKey())), left + 12, y, 0x2A6A2A, false);
            y += 10;
        }
    }

    private void notes(DrawContext g, int left, int top) {
        if (ClientState.notes.isEmpty()) {
            lines(g, Text.translatable("screen.lewandivka.notebook.no_notes"), left + 12, top + 28, PAGE_W - 24, 0x3A2A1C);
            return;
        }
        int index = ClientState.notes.get(Math.floorMod(notePage, ClientState.notes.size()));
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.notes").formatted(Formatting.DARK_GRAY), left + 12, top + 24, 0x4A4A4A, false);
        lines(g, Text.translatable("note.lewandivka." + index), left + 12, top + 42, PAGE_W - 24, 0x202020);
        g.drawText(textRenderer, Text.translatable("screen.lewandivka.notebook.page", Math.floorMod(notePage, ClientState.notes.size()) + 1, ClientState.notes.size()), left + 76, top + PAGE_H - 21, 0x4A4A4A, false);
    }

    private void abilities(DrawContext g, int left, int top) {
        int y = top + 28;
        for (Ability a : Ability.values()) {
            boolean has = ClientState.has(a);
            Text name = Text.translatable("ability.lewandivka." + a.key()).formatted(has ? Formatting.DARK_GREEN : Formatting.DARK_GRAY, Formatting.BOLD);
            g.drawText(textRenderer, Text.literal(has ? "● " : "○ ").append(name), left + 12, y, 0x202020, false);
            Text desc = has ? Text.translatable("ability.lewandivka." + a.key() + ".desc") : Text.translatable("ability.lewandivka.locked");
            lines(g, desc, left + 24, y + 12, PAGE_W - 40, has ? 0x303030 : 0x7A7A7A);
            y += 40;
        }
    }
}
