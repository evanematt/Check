package com.lewandivka.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lewandivka.LewandivkaMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player-facing settings ({@code config/lewandivka.json}). Nothing in here can corrupt the campaign state: the options
 * only change presentation or how hard the bosses hit. Client-only options are simply ignored by a dedicated server.
 */
public final class LewandivkaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static LewandivkaConfig instance = new LewandivkaConfig();

    /** 0 = no screen shake, 1 = default. */
    public double screenShake = 1.0;
    /** false replaces bright full-screen flashes by a calm tint. */
    public boolean flashEffects = true;
    /** Scales ambient particles (0..1). */
    public double particleDensity = 1.0;
    /** Multiplies the damage of bosses and their helpers (0.5 .. 1.5). */
    public double bossDifficulty = 1.0;
    /** Lets players replay cinematics (the transition) from the notebook. */
    public boolean cinematicReplay = true;
    /** Shows the dry one-line hint under the objective in the HUD. */
    public boolean questHints = true;
    /** Extra chat messages for checkpoints and encounter resets (server). */
    public boolean checkpointDebug = false;
    /** Verbose logs of the campaign and encounter systems (server). */
    public boolean developerLogs = false;

    public static LewandivkaConfig get() {
        return instance;
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lewandivka.json");
    }

    public static void load() {
        Path path = file();
        LewandivkaConfig loaded = null;
        if (Files.isRegularFile(path)) {
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(in, LewandivkaConfig.class);
            } catch (IOException | RuntimeException e) {
                LewandivkaMod.LOGGER.warn("Could not read {} ({}); using defaults", path, e.toString());
            }
        }
        instance = loaded == null ? new LewandivkaConfig() : loaded;
        instance.sanitize();
        instance.save();
    }

    public void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, out);
            }
        } catch (IOException e) {
            LewandivkaMod.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
    }

    public void sanitize() {
        screenShake = clamp(screenShake, 0.0, 1.0);
        particleDensity = clamp(particleDensity, 0.0, 1.0);
        bossDifficulty = clamp(bossDifficulty, 0.5, 1.5);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
