package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.structure.StructurePlacement.MarkerPos;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code lewandivka:chromandivka}: the surreal fantasy world. Turquoise meadows, glowing mushroom
 * basins, orange rivers, crystal shelves, floating islands with waterfalls and a grey scar leading to
 * the Head of District Tower. Landmarks are close enough to be seen from the base; the terrain is a
 * pure function of position, quest structures sit on flattened pads.
 */
public final class ChromaPlan implements WorldPlan {

    public static final String ID = "lewandivka:chromandivka";
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 320;
    public static final int WATER_Y = 66;
    private static final long SEED = 0xC4A0D1BAL;

    public static final String MEADOW = "lewandivka:chromatic_meadow";
    public static final String BASIN = "lewandivka:glowshroom_basin";
    public static final String RIVERS = "lewandivka:orange_riverlands";
    public static final String SHELF = "lewandivka:crystal_shelf";
    public static final String GARDEN = "lewandivka:floating_garden";
    public static final String SCAR = "lewandivka:grey_scar";
    public static final String DRY_LAKE = "lewandivka:dry_lake";
    public static final String TOWER_BIOME = "lewandivka:tower";

    // ---- landmark centres (x, z) and pad heights
    public static final int BASE_X = 0;
    public static final int BASE_Z = 0;
    public static final int BASE_Y = 70;
    public static final int GARAGE_X = 122;
    public static final int GARAGE_Z = -10;
    public static final int GARAGE_Y = 70;
    public static final int SHELTER_X = -98;
    public static final int SHELTER_Z = 90;
    public static final int SHELTER_Y = 72;
    public static final int LAKE_X = 80;
    public static final int LAKE_Z = 112;
    public static final int LAKE_Y = 56;
    public static final int TOWER_X = 0;
    public static final int TOWER_Z = -195;
    public static final int TOWER_Y = 72;
    public static final int ASCENT_X = -78;
    public static final int ASCENT_Z = -72;

    /** Circular flat areas under quest structures: {x, z, radius, y}. */
    private static final int[][] PADS = {
            {BASE_X, BASE_Z, 20, BASE_Y},
            {GARAGE_X, GARAGE_Z, 48, GARAGE_Y},
            {SHELTER_X, SHELTER_Z, 30, SHELTER_Y},
            {TOWER_X, TOWER_Z, 36, TOWER_Y},
            {ASCENT_X, ASCENT_Z, 14, 72},
    };

    private final List<StructurePlacement> placements = new ArrayList<>();
    private final List<MarkerPos> markers = new ArrayList<>();

    private static ChromaPlan instance;

    public static synchronized ChromaPlan get() {
        if (instance == null) {
            instance = new ChromaPlan();
        }
        return instance;
    }

    public ChromaPlan() {
        layout();
    }

    // ================================================================== WorldPlan

    @Override
    public String dimensionId() {
        return ID;
    }

    @Override
    public int minY() {
        return MIN_Y;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public List<String> biomes() {
        return List.of(MEADOW, BASIN, RIVERS, SHELF, GARDEN, SCAR, DRY_LAKE, TOWER_BIOME);
    }

    @Override
    public List<StructurePlacement> fixedPlacements() {
        return placements;
    }

    @Override
    public List<MarkerPos> planMarkers() {
        return markers;
    }

    @Override
    public int[] spawn() {
        return new int[] {BASE_X, BASE_Y + 3, BASE_Z + 4};
    }

    // ================================================================== terrain

    private static double edgeFactor(int x, int z) {
        double ex = Math.abs(x) / 250.0;
        double ez = z < 0 ? -z / 275.0 : z / 235.0;
        return Math.max(ex, ez);
    }

    private double rawHeight(int x, int z) {
        double n1 = Noise.fbm2(SEED, x / 95.0, z / 95.0, 3);
        double n2 = Noise.fbm2(SEED + 1, x / 26.0, z / 26.0, 2);
        double h = 68 + (n1 - 0.5) * 22 + (n2 - 0.5) * 5;
        // crystal shelf: a raised plateau in the north-east
        h += 11 * Noise.smoothstep(60, 80, x) * Noise.smoothstep(-36, -58, z);
        // glowshroom basin: a shallow bowl in the south-west
        h -= 5 * Noise.smoothstep(-28, -52, x) * Noise.smoothstep(8, 26, z) * Noise.smoothstep(130, 108, z);
        // grey scar: flat strip between the tower and the base
        double scar = scarFactor(x, z);
        h = Noise.lerp(h, 71, scar);
        // aquapark crater
        double dl = Math.hypot(x - LAKE_X, z - LAKE_Z);
        h = Noise.lerp(h, LAKE_Y + 2, Noise.smoothstep(66, 40, dl) * 0.97);
        // pads under quest structures
        for (int[] p : PADS) {
            double d = Math.hypot(x - p[0], z - p[1]);
            h = Noise.lerp(h, p[3], Noise.smoothstep(p[2] + 14, p[2], d));
        }
        return h;
    }

    private static double scarFactor(int x, int z) {
        return scarStrip(x, z);
    }

    private static double scarStrip(int x, int z) {
        // strip |x| < 26 for z in [-170, -55]
        return Noise.smoothstep(34, 24, Math.abs(x)) * Noise.smoothstep(-48, -62, z) * Noise.smoothstep(-172, -160, z);
    }

    /** Distance factor 0..1 of the river network (1 in the middle of the water). */
    private double riverFactor(int x, int z) {
        double zc = 52 + 14 * Math.sin(x * 0.035 + 0.6);
        double d1 = Math.abs(z - zc);
        double f1 = (x > -150 && x < 190) ? Noise.smoothstep(9, 3.5, d1) : 0;
        double xc = -48 + 10 * Math.sin(z * 0.04);
        double d2 = Math.abs(x - xc);
        double f2 = (z > -95 && z < 145) ? Noise.smoothstep(9, 3.5, d2) : 0;
        double f = Math.max(f1, f2);
        // never cut rivers through the quest pads
        for (int[] p : PADS) {
            if (Math.hypot(x - p[0], z - p[1]) < p[2] + 6) {
                return 0;
            }
        }
        if (Math.hypot(x - LAKE_X, z - LAKE_Z) < 70) {
            return 0;
        }
        return f;
    }

    private String biomeFor(int x, int z) {
        if (Math.hypot(x - TOWER_X, z - TOWER_Z) < 34) {
            return SCAR;
        }
        if (scarStrip(x, z) > 0.45) {
            return SCAR;
        }
        if (Math.hypot(x - LAKE_X, z - LAKE_Z) < 58) {
            return DRY_LAKE;
        }
        if (riverFactor(x, z) > 0.12) {
            return RIVERS;
        }
        if (x > 62 && z < -38) {
            return SHELF;
        }
        if (x < -30 && z > 12 && z < 128) {
            return BASIN;
        }
        return MEADOW;
    }

    @Override
    public String biomeAt(int x, int y, int z) {
        if (y >= 118) {
            if (Math.hypot(x - TOWER_X, z - TOWER_Z) < 44) {
                return TOWER_BIOME;
            }
            return GARDEN;
        }
        if (Math.hypot(x - TOWER_X, z - TOWER_Z) < 30) {
            return TOWER_BIOME;
        }
        return biomeFor(x, z);
    }

    @Override
    public void column(int x, int z, TerrainColumn out) {
        double e = edgeFactor(x, z);
        if (e > 1.0) {
            int h = (int) Math.min(250, 120 + (e - 1.0) * 400);
            out.reset(h, "minecraft:stone", "minecraft:stone", 3, "minecraft:deepslate");
            return;
        }
        double h = rawHeight(x, z);
        double rf = riverFactor(x, z);
        if (rf > 0) {
            h = Noise.lerp(h, 62, rf);
        }
        if (e > 0.88) {
            h += (e - 0.88) / 0.12 * 55;
        }
        int ih = (int) Math.round(h);
        String biome = biomeFor(x, z);
        String top;
        String sub = Pal.DIRT;
        int subDepth = 3;
        long hash = Noise.hash(SEED, x, z);
        switch (biome) {
            case SCAR -> {
                top = (hash & 7) == 0 ? Pal.ANDESITE : (hash & 7) == 1 ? Pal.GRAVEL : (hash & 7) == 2 ? "minecraft:light_gray_terracotta" : "minecraft:smooth_stone";
                sub = "minecraft:andesite";
            }
            case DRY_LAKE -> {
                top = (hash & 7) == 0 ? "minecraft:red_sand" : (hash & 7) == 1 ? "minecraft:terracotta" : (hash & 7) == 2 ? "minecraft:orange_terracotta" : "minecraft:red_sandstone";
                sub = "minecraft:red_sandstone";
            }
            case SHELF -> {
                top = (hash & 7) == 0 ? Pal.CALCITE : (hash & 7) == 1 ? "minecraft:smooth_basalt" : (hash & 15) == 2 ? Pal.AMETHYST : Pal.GRASS;
                sub = Pal.TUFF;
            }
            case RIVERS -> {
                top = rf > 0.2 ? ((hash & 3) == 0 ? "minecraft:clay" : "minecraft:red_sand") : (hash & 7) == 0 ? "minecraft:coarse_dirt" : Pal.GRASS;
                sub = "minecraft:terracotta";
            }
            case BASIN -> {
                top = (hash & 7) == 0 ? "minecraft:moss_block" : (hash & 7) == 1 ? "minecraft:podzol" : Pal.GRASS;
            }
            default -> {
                top = (hash & 31) == 0 ? "minecraft:coarse_dirt" : Pal.TURQ_GRASS;
            }
        }
        out.reset(ih, top, sub, subDepth, e > 0.9 ? "minecraft:deepslate" : "minecraft:stone");
        if (rf > 0 && ih < WATER_Y) {
            out.fluid(WATER_Y, Pal.WATER);
        }
        // decoration directly above the surface
        if (top.equals(Pal.GRASS) && ih >= WATER_Y && (biome.equals(MEADOW) || biome.equals(BASIN))) {
            int roll = (int) Math.floorMod(Noise.hash(SEED + 5, x, z), 100L);
            if (roll < 14) {
                out.decor = "minecraft:short_grass";
            } else if (roll < 17) {
                out.decor = new String[] {"minecraft:allium", "minecraft:pink_tulip", "minecraft:azure_bluet", "minecraft:cornflower"}[roll % 4];
            }
        }
    }

    // ================================================================== scatter

    private boolean nearPad(int x, int z, int extra) {
        for (int[] p : PADS) {
            if (Math.hypot(x - p[0], z - p[1]) < p[2] + extra) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<StructurePlacement> scatterIn(int minX, int minZ, int maxX, int maxZ) {
        List<StructurePlacement> out = new ArrayList<>();
        TerrainColumn col = new TerrainColumn();
        int cell = 11;
        for (int cx = Math.floorDiv(minX - 14, cell); cx <= Math.floorDiv(maxX + 14, cell); cx++) {
            for (int cz = Math.floorDiv(minZ - 14, cell); cz <= Math.floorDiv(maxZ + 14, cell); cz++) {
                int tx = cx * cell + Noise.range(SEED + 11, cx, cz, cell);
                int tz = cz * cell + Noise.range(SEED + 12, cx, cz, cell);
                if (edgeFactor(tx, tz) > 0.9 || nearPad(tx, tz, 6)) {
                    continue;
                }
                String biome = biomeFor(tx, tz);
                double roll = Noise.hash01(SEED + 13, cx, cz);
                Blueprint bp;
                switch (biome) {
                    case MEADOW -> {
                        if (roll > 0.42) {
                            continue;
                        }
                        bp = roll < 0.06 ? Nature.glowshroom((int) (roll * 1000)) : Nature.rainbowTree(Noise.range(SEED + 14, cx, cz, Nature.RAINBOW_TREE_VARIANTS));
                    }
                    case BASIN -> {
                        if (roll > 0.8) {
                            continue;
                        }
                        bp = roll < 0.1 ? Nature.rainbowTree(Noise.range(SEED + 14, cx, cz, 4)) : Nature.glowshroom(Noise.range(SEED + 15, cx, cz, Nature.MUSHROOM_VARIANTS));
                    }
                    case SHELF -> {
                        if (roll > 0.5) {
                            continue;
                        }
                        bp = Nature.crystals(Noise.range(SEED + 16, cx, cz, Nature.CRYSTAL_VARIANTS));
                    }
                    case RIVERS -> {
                        if (roll > 0.18) {
                            continue;
                        }
                        bp = Nature.rainbowTree(Noise.range(SEED + 14, cx, cz, 4));
                    }
                    case DRY_LAKE -> {
                        if (roll > 0.05) {
                            continue;
                        }
                        bp = Nature.crystals(Noise.range(SEED + 17, cx, cz, Nature.CRYSTAL_VARIANTS));
                    }
                    default -> {
                        continue;
                    }
                }
                column(tx, tz, col);
                if (col.fluidY > col.height) {
                    continue; // never plant in water
                }
                int half = bp.sizeX() / 2;
                StructurePlacement sp = new StructurePlacement("scatter", bp, tx - half, col.height + 1, tz - half);
                if (sp.intersectsXZ(minX, minZ, maxX, maxZ)) {
                    out.add(sp);
                }
            }
        }
        return out;
    }

    // ================================================================== layout

    private void marker(String name, int x, int y, int z, String data) {
        markers.add(new MarkerPos("chromandivka:" + name, x, y, z, 1, 1, 1, data));
    }

    private void layout() {
        // base house on its pad: bp (C, 0, C) sits on the pad centre
        placements.add(new StructurePlacement("base", BaseHouse.base(), BASE_X - BaseHouse.C, BASE_Y - 1, BASE_Z - BaseHouse.C));
        // decorative floating islands with waterfalls (the "waterfalls falling from islands")
        int[][] isles = {
                {-40, 150, -30, 12, 1, 1}, {60, 172, -95, 14, 2, 1}, {132, 142, 62, 10, 3, -1}, {-120, 162, 22, 13, 4, 1},
                {22, 192, 66, 9, 5, 0}, {-12, 132, -126, 11, 6, -1}, {172, 182, -62, 12, 7, 1}, {-70, 142, -152, 8, 8, 0},
                {98, 158, 42, 10, 9, 1}, {-150, 190, -90, 9, 10, 0}, {40, 130, 150, 12, 11, 1}
        };
        for (int[] i : isles) {
            Blueprint bp = Nature.island(i[3], i[4], i[5], 1 + i[4] % 2);
            int half = bp.sizeX() / 2;
            int depth = Nature.islandDepth(i[3]);
            int fall = i[5] != 0 ? 28 : 0;
            placements.add(new StructurePlacement("isle" + i[4], bp, i[0] - half, i[1] - depth - fall, i[2] - half));
        }
        marker("spawn", BASE_X, BASE_Y, BASE_Z + 2, "");
    }

    /** Hook for the structure builders to register quest structures (added by the individual plans). */
    void add(StructurePlacement sp) {
        placements.add(sp);
    }

    void addMarker(MarkerPos m) {
        markers.add(m);
    }
}
