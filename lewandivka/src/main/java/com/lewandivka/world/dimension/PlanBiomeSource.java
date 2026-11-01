package com.lewandivka.world.dimension;

import com.lewandivka.core.world.WorldPlan;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Biomes come straight from the deterministic world plan. */
public final class PlanBiomeSource extends BiomeSource {

    public static final Codec<PlanBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("plan").forGetter(source -> source.planName),
            RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)
    ).apply(instance, instance.stable(PlanBiomeSource::new)));

    private final String planName;
    private final WorldPlan plan;
    private final Map<String, RegistryEntry<Biome>> entries = new LinkedHashMap<>();
    private final RegistryEntry<Biome> fallback;

    public PlanBiomeSource(String planName, RegistryEntryLookup<Biome> lookup) {
        this.planName = planName;
        this.plan = Plans.get(planName);
        RegistryEntry<Biome> first = null;
        for (String id : plan.biomes()) {
            RegistryEntry<Biome> entry = lookup.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, new Identifier(id)));
            entries.put(id, entry);
            if (first == null) {
                first = entry;
            }
        }
        this.fallback = first;
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return entries.values().stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        // biome coordinates are in 4x4x4 cells
        RegistryEntry<Biome> entry = entries.get(plan.biomeAt(x << 2, y << 2, z << 2));
        return entry != null ? entry : fallback;
    }
}
