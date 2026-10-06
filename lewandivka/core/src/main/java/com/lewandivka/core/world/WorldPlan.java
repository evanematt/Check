package com.lewandivka.core.world;

import com.lewandivka.core.structure.StructurePlacement;

import java.util.List;

/**
 * Everything a chunk generator needs to know about one custom dimension. A plan is a pure
 * description: terrain columns, fixed structures, scatter decoration, markers and biomes.
 */
public interface WorldPlan {

    /** Dimension this plan belongs to, e.g. {@code lewandivka:district}. */
    String dimensionId();

    int minY();

    /** Total build height of the dimension (multiple of 16). */
    int height();

    /** Fills {@code out} with the terrain description of the column (x, z). */
    void column(int x, int z, TerrainColumn out);

    /** Biome id for a block position, e.g. {@code lewandivka:district}. */
    String biomeAt(int x, int y, int z);

    /** All distinct biome ids the plan can return (the biome source must know them all). */
    List<String> biomes();

    /** Fixed, quest-critical structures. They are always generated, never randomly. */
    List<StructurePlacement> fixedPlacements();

    /**
     * Deterministic decoration (trees, mushrooms, crystals, lamp posts ...) intersecting the
     * given inclusive XZ rectangle. May return large lists for large rectangles; the generator calls it
     * per chunk with a small margin.
     */
    List<StructurePlacement> scatterIn(int minX, int minZ, int maxX, int maxZ);

    /** Absolute markers that are not part of any blueprint (points of interest, waypoints ...). */
    List<StructurePlacement.MarkerPos> planMarkers();

    /** Where new players appear. */
    int[] spawn();
}
