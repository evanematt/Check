package com.lewandivka.world.dimension;

import com.lewandivka.util.Ids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Registers the codecs that the dimension JSON files refer to ({@code lewandivka:plan}). */
public final class DimensionRegistry {

    private DimensionRegistry() {
    }

    public static void register() {
        Registry.register(Registries.BIOME_SOURCE, Ids.of("plan"), PlanBiomeSource.CODEC);
        Registry.register(Registries.CHUNK_GENERATOR, Ids.of("plan"), PlanChunkGenerator.CODEC);
    }
}
