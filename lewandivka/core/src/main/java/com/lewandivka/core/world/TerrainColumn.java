package com.lewandivka.core.world;

/**
 * Description of one terrain column, reused by the chunk generator to avoid allocations.
 * The column is solid from y = minY up to {@link #height} (inclusive): bedrock at minY, {@link #base}
 * below the topsoil, {@link #sub} for {@link #subDepth} blocks below the surface and {@link #top} on
 * the surface itself. If {@link #fluidY} is above the surface the gap is filled with {@link #fluid}.
 */
public final class TerrainColumn {

    public int height;
    public String top = "minecraft:grass_block";
    public String sub = "minecraft:dirt";
    public int subDepth = 3;
    public String base = "minecraft:stone";
    public int fluidY = Integer.MIN_VALUE;
    public String fluid = "minecraft:water";
    /** Optional one-block decoration placed directly above the surface (rails, grass tufts, flowers). */
    public String decor;

    public TerrainColumn reset(int height, String top, String sub, int subDepth, String base) {
        this.height = height;
        this.top = top;
        this.sub = sub;
        this.subDepth = subDepth;
        this.base = base;
        this.fluidY = Integer.MIN_VALUE;
        this.fluid = "minecraft:water";
        this.decor = null;
        return this;
    }

    public TerrainColumn fluid(int topY, String key) {
        this.fluidY = topY;
        this.fluid = key;
        return this;
    }
}
