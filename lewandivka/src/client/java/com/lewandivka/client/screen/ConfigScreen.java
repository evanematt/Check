package com.lewandivka.client.screen;

import com.lewandivka.config.LewandivkaConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/** Accessibility and comfort settings (Mod Menu and the notebook open it). Saved when the screen is closed. */
public final class ConfigScreen extends Screen {

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("config.lewandivka.title"));
        this.parent = parent;
    }

    private static final class Slider extends SliderWidget {
        private final String key;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;

        Slider(int x, int y, int w, String key, double min, double max, double value, DoubleConsumer setter) {
            super(x, y, w, 20, Text.empty(), (value - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private double current() {
            return min + (max - min) * value;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(key).append(Text.literal(": ")).append(Text.translatable("config.lewandivka.percent", Math.round(current() * 100))));
        }

        @Override
        protected void applyValue() {
            setter.accept(MathHelper.clamp(current(), min, max));
        }
    }

    @Override
    protected void init() {
        LewandivkaConfig c = LewandivkaConfig.get();
        int w = 220;
        int x = width / 2 - w / 2;
        int y = height / 6;
        addDrawableChild(new Slider(x, y, w, "config.lewandivka.screen_shake", 0.0, 1.5, c.screenShake, v -> c.screenShake = v));
        y += 24;
        addDrawableChild(new Slider(x, y, w, "config.lewandivka.particle_density", 0.0, 1.0, c.particleDensity, v -> c.particleDensity = v));
        y += 24;
        addDrawableChild(new Slider(x, y, w, "config.lewandivka.boss_difficulty", 0.5, 1.5, c.bossDifficulty, v -> c.bossDifficulty = v));
        y += 28;
        toggle(x, y, w, "config.lewandivka.flash_reduction", () -> !c.flashEffects, v -> c.flashEffects = !v);
        y += 24;
        toggle(x, y, w, "config.lewandivka.cinematic_replay", () -> c.cinematicReplay, v -> c.cinematicReplay = v);
        y += 24;
        toggle(x, y, w, "config.lewandivka.quest_hints", () -> c.questHints, v -> c.questHints = v);
        y += 24;
        toggle(x, y, w, "config.lewandivka.checkpoint_debug", () -> c.checkpointDebug, v -> c.checkpointDebug = v);
        y += 24;
        toggle(x, y, w, "config.lewandivka.developer_logs", () -> c.developerLogs, v -> c.developerLogs = v);
        y += 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("config.lewandivka.reset"), b -> {
            LewandivkaConfig fresh = new LewandivkaConfig();
            c.screenShake = fresh.screenShake;
            c.flashEffects = fresh.flashEffects;
            c.particleDensity = fresh.particleDensity;
            c.bossDifficulty = fresh.bossDifficulty;
            c.cinematicReplay = fresh.cinematicReplay;
            c.questHints = fresh.questHints;
            c.checkpointDebug = fresh.checkpointDebug;
            c.developerLogs = fresh.developerLogs;
            clearChildren();
            init();
        }).dimensions(x, y, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("config.lewandivka.done"), b -> close()).dimensions(x + 114, y, 106, 20).build());
    }

    private void toggle(int x, int y, int w, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        addDrawableChild(ButtonWidget.builder(label(key, getter.getAsBoolean()), b -> {
            boolean v = !getter.getAsBoolean();
            setter.accept(v);
            b.setMessage(label(key, getter.getAsBoolean()));
        }).dimensions(x, y, w, 20).build());
    }

    private static Text label(String key, boolean on) {
        return Text.translatable(key).append(Text.literal(": ")).append(Text.translatable(on ? "config.lewandivka.on" : "config.lewandivka.off"));
    }

    @Override
    public void close() {
        LewandivkaConfig cfg = LewandivkaConfig.get();
        cfg.sanitize();
        cfg.save();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        g.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(g, mouseX, mouseY, delta);
    }
}
