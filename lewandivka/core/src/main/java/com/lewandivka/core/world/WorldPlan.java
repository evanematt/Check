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

    /**
     * Whether the block (x, y, z) is carved out of the rock (a cave); {@code surface} is the height of its column. Caves
     * are part of the terrain, not of a structure, so they come before the structures are stamped.
     */
    default boolean carved(int x, int y, int z, int surface) {
        return false;
    }

    /**
     * Whether the chunk with this centre column is open country that the game may populate like the ordinary overworld
     * (animals when the chunk is generated; trees, ores and flowers come from the biomes' own features).
     */
    default boolean wilderness(int x, int z) {
        return false;
    }

    /** Whether the column is so far from the structures of the plan that a big vanilla structure starting there cannot reach them. */
    default boolean farFromTheCity(int x, int z) {
        return false;
    }
}
