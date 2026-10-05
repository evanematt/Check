package ua.lewandivka.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.world.CrystalSpikeFeature;
import ua.lewandivka.world.FloatingIslandFeature;
import ua.lewandivka.world.GiantGlowshroomFeature;

public final class ModWorldgen {
    /** Вимір «Хромандівка» (самі налаштування — у data/lewandivka/dimension). */
    public static final RegistryKey<World> CHROMA = RegistryKey.of(RegistryKeys.WORLD, Lewandivka.id("chromandivka"));

    public static final Feature<DefaultFeatureConfig> CRYSTAL_SPIKE = new CrystalSpikeFeature(DefaultFeatureConfig.CODEC);
    public static final Feature<DefaultFeatureConfig> GIANT_GLOWSHROOM = new GiantGlowshroomFeature(DefaultFeatureConfig.CODEC);
    public static final Feature<DefaultFeatureConfig> FLOATING_ISLAND = new FloatingIslandFeature(DefaultFeatureConfig.CODEC);

    public static void init() {
        Registry.register(Registries.FEATURE, Lewandivka.id("crystal_spike"), CRYSTAL_SPIKE);
        Registry.register(Registries.FEATURE, Lewandivka.id("giant_glowshroom"), GIANT_GLOWSHROOM);
        Registry.register(Registries.FEATURE, Lewandivka.id("floating_island"), FLOATING_ISLAND);
    }

    private ModWorldgen() {
    }
}
