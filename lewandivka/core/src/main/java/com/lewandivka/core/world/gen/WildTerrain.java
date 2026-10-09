package com.lewandivka.core.world.gen;

import com.lewandivka.core.world.Noise;

/**
 * The wilderness around the district: an endless survival landscape of lowlands, hills, mountains, rivers, lakes, seas
 * and caves, described as a pure function of the position (any chunk can be generated at any time, in any order).
 *
 * <p>The land is shaped by a few large-scale gradient noises: continentalness (sea or land), relief (plains, hills,
 * mountains), a ridged layer for the mountains, zero-crossings of a noise for rivers, a basin noise for lakes and two
 * climate noises (temperature and humidity) which pick one of the vanilla biomes. The game then decorates every biome
 * with its own trees, flowers, ores and animals, so the wilderness plays like the ordinary overworld. Around the city the
 * land is kept gentle and temperate (the first steps of the story happen there), further out it gets wild.</p>
 */
public final class WildTerrain {

    public static final int SEA = 62;
    private static final long SEED = 0x57A71CC0DEL;

    private static final long S_CONT = SEED + 1;
    private static final long S_HILL = SEED + 2;
    private static final long S_HILL_MASK = SEED + 3;
    private static final long S_MOUNT = SEED + 4;
    private static final long S_RIDGE = SEED + 5;
    private static final long S_DETAIL = SEED + 6;
    private static final long S_RIVER = SEED + 7;
    private static final long S_RIVER_B = SEED + 8;
    private static final long S_LAKE = SEED + 9;
    private static final long S_TEMP = SEED + 10;
    private static final long S_HUMID = SEED + 11;
    private static final long S_VARIETY = SEED + 12;
    private static final long S_CAVE_A = SEED + 20;
    private static final long S_CAVE_B = SEED + 21;
    private static final long S_CAVE_C = SEED + 22;
    private static final long S_ENTRANCE = SEED + 23;
    private static final long S_WARP = SEED + 30;
    private static final long S_MICRO = SEED + 31;

    /** Every biome the wilderness can produce (the biome source of the dimension has to know them all). */
    public static final String[] BIOMES = {
            "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:flower_forest",
            "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga", "minecraft:snowy_plains", "minecraft:snowy_taiga",
            "minecraft:savanna", "minecraft:desert", "minecraft:jungle", "minecraft:swamp",
            "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:snowy_slopes", "minecraft:grove",
            "minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks",
            "minecraft:river", "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
            "minecraft:ocean", "minecraft:deep_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
            "minecraft:warm_ocean", "minecraft:lukewarm_ocean"
    };

    private WildTerrain() {
    }

    /** What the wilderness looks like in one column. */
    public static final class Sample {
        public int height;
        /** The height before it is rounded to a block. */
        public double exactHeight;
        public String biome = "minecraft:plains";
        public String top = "minecraft:grass_block";
        public String sub = "minecraft:dirt";
        public int subDepth = 3;
        public String base = "minecraft:stone";
        /** Top of the water in the column, or {@link Integer#MIN_VALUE} when it is dry. */
        public int fluidY = Integer.MIN_VALUE;
        /** 0 = deep sea .. 1 = inland. */
        public double land;
        /** 0..1: how much of a river the column is. */
        public double river;
        /** 0..1: how mountainous the area is. */
        public double mountains;
        public double temperature;
        public double humidity;
    }

    private static double near(int cityEdge, int r) {
        return 1.0 - Noise.smoothstep(cityEdge + 90, cityEdge + 520, r);
    }

    /**
     * Fills {@code out} for the column (x, z). {@code cityEdge} is the half width of the city: the land next to it stays
     * calm (no rivers, lakes, mountains or extreme climates) and gets wilder with the distance.
     */
    public static void sample(int px, int pz, int cityEdge, Sample out) {
        int r = Math.max(Math.abs(px), Math.abs(pz));
        double near = near(cityEdge, r);
        // domain warp: bends every feature a little, which takes the straight edges of the noise grid out of the land
        double x = px + 60.0 * Noise.perlin2(S_WARP, px / 260.0, pz / 260.0);
        double z = pz + 60.0 * Noise.perlin2(S_WARP + 1, px / 260.0 + 17.3, pz / 260.0 - 9.1);

        // ---- climate
        double temp = Noise.fbmPerlin2(S_TEMP, x / 650.0, z / 650.0, 3) * 1.75;
        double humid = Noise.fbmPerlin2(S_HUMID, x / 520.0, z / 520.0, 3) * 1.75;
        temp = Noise.lerp(temp, 0.12, near * 0.85);
        humid = Noise.lerp(humid, 0.16, near * 0.85);
        out.temperature = temp;
        out.humidity = humid;

        // ---- sea or land
        double cont = Noise.fbmPerlin2(S_CONT, x / 1500.0, z / 1500.0, 3) * 1.3 + 0.95 * near;
        double land = Noise.smoothstep(-0.30, 0.16, cont);
        out.land = land;

        // ---- relief
        double hills = Noise.fbmPerlin2(S_HILL, x / 170.0, z / 170.0, 4);
        double detail = Noise.fbmPerlin2(S_DETAIL, x / 36.0, z / 36.0, 2);
        double hillMask = Noise.smoothstep(-0.20, 0.40, Noise.fbmPerlin2(S_HILL_MASK, x / 640.0, z / 640.0, 3));
        double mountains = Noise.smoothstep(0.12, 0.50, Noise.fbmPerlin2(S_MOUNT, x / 950.0, z / 950.0, 3) * 1.4) * (1.0 - near);
        out.mountains = mountains;
        double ridge = 1.0 - Math.abs(Noise.fbmPerlin2(S_RIDGE, x / 310.0, z / 310.0, 3) * 1.6);
        ridge = Noise.clamp(ridge, 0, 1);
        ridge *= ridge;

        double landHeight = SEA + 2.5 + 2.5 * hills + 1.2 * detail
                + hillMask * (0.5 + 0.5 * hills) * 20.0
                + mountains * (ridge * 72.0 + hills * 9.0);
        // small bumps (a block or so) that break the long straight contour lines of a slowly rising land into a ragged, natural edge
        landHeight += 0.75 * Noise.perlin2(S_MICRO, px / 9.0, pz / 9.0) + 0.4 * Noise.perlin2(S_MICRO + 1, px / 3.9, pz / 3.9);
        double seaFloor = SEA - 5 - 26.0 * (1.0 - land) * (1.0 - land) + 2 * detail;
        double h = Noise.lerp(seaFloor, landHeight, Noise.smoothstep(0.30, 0.62, land));

        // ---- rivers (zero lines of a noise) and lakes (basins), never next to the city
        double awayFromCity = Noise.smoothstep(cityEdge + 40, cityEdge + 170, r);
        double rv = Math.abs(Noise.perlin2(S_RIVER, x / 430.0, z / 430.0) + 0.28 * Noise.perlin2(S_RIVER_B, x / 150.0, z / 150.0));
        double river = (1.0 - Noise.smoothstep(0.010, 0.050, rv)) * Noise.smoothstep(0.45, 0.65, land) * awayFromCity;
        out.river = river;
        double lakeNoise = Noise.fbmPerlin2(S_LAKE, x / 120.0, z / 120.0, 3);
        double lake = Noise.smoothstep(0.38, 0.62, lakeNoise) * Noise.smoothstep(0.50, 0.70, land) * (1.0 - mountains) * awayFromCity;
        double carve = Math.max(river, lake * 0.9);
        if (carve > 0) {
            double bed = SEA - 3.5 - 2.0 * lake;
            h = Noise.lerp(h, Math.min(h, bed), Noise.smoothstep(0.0, 0.85, carve));
        }
        out.exactHeight = h;
        out.height = (int) Math.round(h);

        // ---- biome and surface
        classify(px, pz, h, near, out);
    }

    private static void classify(int x, int z, double h, double near, Sample o) {
        int height = o.height;
        double t = o.temperature;
        double hm = o.humidity;
        boolean cold = t < -0.45;
        boolean hot = t > 0.36;
        boolean wet = height < SEA;
        o.fluidY = wet ? SEA : Integer.MIN_VALUE;
        o.top = "minecraft:grass_block";
        o.sub = "minecraft:dirt";
        o.subDepth = 3;
        o.base = "minecraft:stone";
        double variety = Noise.fbmPerlin2(S_VARIETY, x / 260.0, z / 260.0, 2);

        // seas
        if (o.land < 0.42 || (wet && height < SEA - 7 && o.river < 0.5)) {
            boolean deep = height < SEA - 24;
            if (cold) {
                o.biome = deep ? "minecraft:deep_frozen_ocean" : "minecraft:frozen_ocean";
            } else if (hot) {
                o.biome = deep ? "minecraft:lukewarm_ocean" : "minecraft:warm_ocean";
            } else {
                o.biome = deep ? "minecraft:deep_ocean" : "minecraft:ocean";
            }
            seaBed(x, z, height, o);
            return;
        }
        if (o.river > 0.45 && wet) {
            o.biome = cold ? "minecraft:frozen_river" : "minecraft:river";
            seaBed(x, z, height, o);
            return;
        }
        // the shore of any water
        if (height <= SEA + 1 && o.land < 0.80 && !(hm > 0.58 && !cold && !hot)) {
            if (o.mountains > 0.35) {
                o.biome = "minecraft:stony_shore";
                o.top = "minecraft:gravel";
                o.sub = "minecraft:stone";
            } else {
                o.biome = cold ? "minecraft:snowy_beach" : "minecraft:beach";
                o.top = "minecraft:sand";
                o.sub = "minecraft:sand";
                o.subDepth = 3;
            }
            return;
        }
        if (wet) {
            // a lake inside a land biome: the land biome decides the rest, the bed is sand or gravel
            landBiome(x, z, t, hm, variety, o);
            seaBed(x, z, height, o);
            return;
        }
        // mountains
        if (height > 128) {
            o.biome = cold || height > 148 ? "minecraft:frozen_peaks" : "minecraft:stony_peaks";
            if (height > 138 && !cold && variety > 0.25) {
                o.biome = "minecraft:jagged_peaks";
            }
            o.top = o.biome.equals("minecraft:stony_peaks") ? "minecraft:stone" : "minecraft:snow_block";
            o.sub = "minecraft:stone";
            o.subDepth = 2;
            return;
        }
        if (height > 98 && o.mountains > 0.15) {
            if (cold) {
                o.biome = "minecraft:snowy_slopes";
                o.top = "minecraft:snow_block";
                o.sub = "minecraft:stone";
                o.subDepth = 2;
            } else if (height > 110) {
                o.biome = variety > 0.1 ? "minecraft:grove" : "minecraft:windswept_hills";
                o.top = o.biome.equals("minecraft:grove") ? "minecraft:snow_block" : "minecraft:stone";
                o.sub = "minecraft:stone";
                o.subDepth = 2;
            } else {
                o.biome = hm > 0.2 ? "minecraft:windswept_forest" : "minecraft:windswept_hills";
                o.top = height > 104 ? "minecraft:stone" : "minecraft:grass_block";
                o.sub = height > 104 ? "minecraft:stone" : "minecraft:dirt";
                o.subDepth = height > 104 ? 2 : 3;
            }
            return;
        }
        landBiome(x, z, t, hm, variety, o);
    }

    /** The biome of dry lowland and hills by climate; it also sets the surface blocks. */
    private static void landBiome(int x, int z, double t, double hm, double variety, Sample o) {
        if (t < -0.45) {
            o.biome = hm > 0.0 ? "minecraft:snowy_taiga" : "minecraft:snowy_plains";
        } else if (t < -0.18) {
            o.biome = hm > 0.05 ? "minecraft:taiga" : hm > -0.25 ? "minecraft:forest" : "minecraft:plains";
        } else if (t < 0.22) {
            if (hm > 0.60 && o.height <= SEA + 4) {
                o.biome = "minecraft:swamp";
            } else if (hm > 0.45) {
                o.biome = variety > 0.0 ? "minecraft:dark_forest" : "minecraft:forest";
            } else if (hm > 0.05) {
                o.biome = variety > 0.35 ? "minecraft:flower_forest" : variety > -0.15 ? "minecraft:forest" : "minecraft:birch_forest";
            } else if (hm > -0.25) {
                o.biome = variety > 0.35 ? "minecraft:sunflower_plains" : variety < -0.35 ? "minecraft:meadow" : "minecraft:plains";
            } else {
                o.biome = "minecraft:plains";
            }
        } else if (t < 0.36) {
            o.biome = hm > 0.30 ? "minecraft:forest" : hm > -0.20 ? "minecraft:savanna" : "minecraft:plains";
        } else {
            o.biome = hm > 0.22 ? "minecraft:jungle" : hm > -0.15 ? "minecraft:savanna" : "minecraft:desert";
        }
        if (o.biome.equals("minecraft:desert")) {
            o.top = "minecraft:sand";
            o.sub = "minecraft:sand";
            o.subDepth = 4;
        }
    }

    /** Sand and gravel on the bottom of seas, rivers and lakes (with a few patches of clay). */
    private static void seaBed(int x, int z, int height, Sample o) {
        long h = Noise.hash(SEED, x >> 2, z >> 2);
        if (height < SEA - 18) {
            o.top = (h & 3) == 0 ? "minecraft:clay" : "minecraft:gravel";
        } else if ((h & 7) == 0) {
            o.top = "minecraft:clay";
        } else {
            o.top = (Noise.fbmPerlin2(SEED + 31, x / 22.0, z / 22.0, 2) > 0.15) ? "minecraft:gravel" : "minecraft:sand";
        }
        o.sub = o.top.equals("minecraft:clay") ? "minecraft:clay" : "minecraft:sand";
        o.subDepth = 3;
    }

    // ================================================================== caves

    /** Where the caves stop being a wall of rock: nearest to the surface they may start. */
    private static final int ROOF = 6;

    /**
     * Whether the block at (x, y, z) belongs to a cave. Wide caverns (rare), winding tunnels (the line where two noises
     * are both near zero) and a few openings to the daylight. Never at the very bottom (bedrock) and never under water.
     */
    public static boolean cave(int x, int y, int z, int surface) {
        if (y < 3 || y > surface) {
            return false;
        }
        boolean open = y > surface - ROOF;
        if (open && !(y <= surface && entrance(x, z) > 0.9)) {
            return false;
        }
        if (surface < SEA && y > surface - 10) {
            return false;
        }
        double widen = 1.0 + 0.5 * Noise.smoothstep(40, 8, y);
        double cavern = Noise.perlin3(S_CAVE_C, x / 64.0, y / 40.0, z / 64.0);
        if (cavern > 0.60) {
            return true;
        }
        double a = Noise.perlin3(S_CAVE_A, x / 36.0, y / 24.0, z / 36.0);
        double limit = 0.0105 * widen;
        if (a * a >= limit) {
            return false;
        }
        double b = Noise.perlin3(S_CAVE_B, x / 36.0 + 311, y / 24.0, z / 36.0 + 727);
        return a * a + b * b < limit;
    }

    /** A noise that is high at the few places where the tunnels break through to the daylight. */
    private static double entrance(int x, int z) {
        return Noise.perlin2(S_ENTRANCE, x / 55.0, z / 55.0) + 0.35 * Noise.perlin2(S_ENTRANCE + 1, x / 17.0, z / 17.0);
    }
}
