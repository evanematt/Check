package ua.lewandivka.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ua.lewandivka.Lewandivka;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Налаштування, що не можуть зламати стан кампанії: комфорт і візуальні ефекти. */
public final class LewConfig {
    public boolean screenShake = true;
    public boolean reduceFlashing = false;
    /** 0.0–1.0 */
    public float ambientParticles = 1.0f;
    /** Множник складності босів 0.5–1.5 (HP і шкода). */
    public float bossDifficulty = 1.0f;
    public boolean questHints = true;
    public boolean developerLogs = false;

    private static LewConfig instance = new LewConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static LewConfig get() {
        return instance;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("lewandivka.json");
        try {
            if (Files.exists(file)) {
                instance = GSON.fromJson(Files.readString(file), LewConfig.class);
                if (instance == null) {
                    instance = new LewConfig();
                }
            }
            instance.ambientParticles = Math.max(0f, Math.min(1f, instance.ambientParticles));
            instance.bossDifficulty = Math.max(0.5f, Math.min(1.5f, instance.bossDifficulty));
            Files.writeString(file, GSON.toJson(instance));
        } catch (IOException | RuntimeException e) {
            Lewandivka.LOG.warn("Не вдалося прочитати конфіг, беру типовий", e);
            instance = new LewConfig();
        }
    }

    private LewConfig() {
    }
}
