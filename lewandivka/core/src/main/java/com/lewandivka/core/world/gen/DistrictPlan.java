package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.structure.StructurePlacement.MarkerPos;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code lewandivka:district}: a fictionalised post-Soviet residential district, roughly 300 x 300
 * blocks. Origin (0, 0) is the tram stop. Streets form a grid; five-storey panel blocks surround two
 * courtyards, private-sector plaster houses with gardens fill the east, a garage cooperative hides
 * Garage No. 13 in the south-west.
 *
 * <p>The layout is a pure function: the same plan always produces the same world, and every
 * quest-critical object (stashes, clues, kiosk foundation, tram stop, garages) is placed explicitly.</p>
 */
public final class DistrictPlan implements WorldPlan {

    public static final String ID = "lewandivka:district";
    public static final String BIOME = "lewandivka:district";
    public static final int GROUND = 63;
    public static final int EDGE = 150;
    /** The depth of the ordinary overworld: the underground has its deepslate, its diamond level and its lava lakes. */
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    private static final long SEED = 0x1E7AD1C4L;

    /** Waypoint graph of the Debtor chase: positions come from markers {@code debtor_wp_<i>}. */
    public static final int[][] DEBTOR_EDGES = {
            {0, 1}, {1, 7}, {7, 6}, {6, 5}, {5, 4}, {4, 3}, {3, 2}, {2, 1}, {7, 4}, {2, 8}, {5, 9}
    };
    public static final int[][] DEBTOR_NODES = {
            {-110, 66}, {-72, 66}, {-72, 36}, {-30, 36}, {10, 36}, {43, 36}, {43, 66}, {-10, 66}, {-100, 36}, {75, 36}
    };
    public static final int[] DEBTOR_DEAD_ENDS = {8, 9};

    private record Rect(int x1, int z1, int x2, int z2) {
        boolean has(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        Rect grow(int n) {
            return new Rect(x1 - n, z1 - n, x2 + n, z2 + n);
        }
    }

    private static final Rect MAIN = new Rect(-29, -5, 150, 5);
    private static final Rect PLAZA = new Rect(-42, -10, -30, 10);
    private static final List<Rect> ROADS = List.of(
            MAIN,
            new Rect(-75, -148, -67, 148),
            new Rect(45, -148, 53, 148),
            new Rect(-148, -70, 148, -62),
            new Rect(-148, 62, 148, 70));
    private static final List<Rect> LANES = List.of(
            new Rect(100, -58, 102, -12),
            new Rect(57, -38, 148, -36),
            new Rect(100, 12, 102, 58),
            new Rect(57, 34, 148, 36));
    private static final Rect TRAM_BED = new Rect(-43, -1, 150, 1);

    private final List<StructurePlacement> placements = new ArrayList<>();
    private final List<MarkerPos> markers = new ArrayList<>();
    private final List<Rect> occupied = new ArrayList<>();
    private final CityGround ground;
    /** Placements that stand on the ground, whose height is only known when every plateau is registered: reference column and offset. */
    private final Map<StructurePlacement, int[]> onGround = new IdentityHashMap<>();
    private final List<Object[]> pendingMarkers = new ArrayList<>();
    /** Where fixed structures reach deep under the ground: no caves, no ore, no dungeons around them. */
    private final List<Rect> deepStructures = new ArrayList<>();
    /** Buildings that do not fit where the layout puts them (empty in a good plan; checked by the tests). */
    private final List<String> layoutProblems = new ArrayList<>();

    private static DistrictPlan instance;

    public static synchronized DistrictPlan get() {
        if (instance == null) {
            instance = new DistrictPlan();
        }
        return instance;
    }

    public DistrictPlan() {
        this.ground = new CityGround(GROUND, roadAreas());
        layout();
        ground.freeze();
        resolve();
    }

    /** Roads with their sidewalks, the lanes, the tram bed and the plaza: the land stays level around them. */
    private static List<int[]> roadAreas() {
        List<int[]> out = new ArrayList<>();
        for (Rect r : ROADS) {
            Rect g = r.grow(3);
            out.add(new int[] {g.x1, g.z1, g.x2, g.z2});
        }
        for (Rect r : List.of(PLAZA, TRAM_BED)) {
            Rect g = r.grow(1);
            out.add(new int[] {g.x1, g.z1, g.x2, g.z2});
        }
        for (Rect r : LANES) {
            out.add(new int[] {r.x1, r.z1, r.x2, r.z2});
        }
        return out;
    }

    /** What is wrong with the layout of the district: buildings on streets, on each other or on the places of the quests. */
    public List<String> layoutProblems() {
        return List.copyOf(layoutProblems);
    }

    /** The height of the ground of the district at a column (the same function the terrain uses). */
    public int groundAt(int x, int z) {
        return ground.at(x, z);
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

    /** The city is a square of {@code EDGE + 2} blocks around the tram stop; the wilderness starts right behind it. */
    public static final int CITY_EDGE = EDGE + 2;
    /** Width of the strip in which the flat ground of the city grows into the land of the wilderness. */
    public static final int BLEND = 64;

    private static final ThreadLocal<WildTerrain.Sample> WILD = ThreadLocal.withInitial(WildTerrain.Sample::new);

    @Override
    public void column(int x, int z, TerrainColumn out) {
        int r = Math.max(Math.abs(x), Math.abs(z));
        if (r > CITY_EDGE) {
            wildColumn(x, z, r, out);
            return;
        }
        String top = topBlock(x, z);
        out.reset(ground.at(x, z), top, top.equals(Pal.GRASS) || top.equals(Pal.COARSE_DIRT) || top.equals(Pal.PATH) ? Pal.DIRT : Pal.GRAVEL,
                top.equals(Pal.GRASS) || top.equals(Pal.COARSE_DIRT) || top.equals(Pal.PATH) ? 3 : 2, "minecraft:stone");
        out.decor = decorAt(x, z, top);
    }

    /** The wilderness: natural land, blended into the ground of the city over {@link #BLEND} blocks. */
    private void wildColumn(int x, int z, int r, TerrainColumn out) {
        WildTerrain.Sample s = WILD.get();
        WildTerrain.sample(x, z, EDGE, s);
        double w = Noise.smoothstep(CITY_EDGE, CITY_EDGE + BLEND, r);
        int height = (int) Math.round(Noise.lerp(w >= 1.0 ? s.exactHeight : ground.exact(x, z), s.exactHeight, w));
        out.reset(height, s.top, s.sub, s.subDepth, s.base);
        // a hollow of the city's hills that reaches out into the land stays dry; only the water of the wilderness itself is water
        if (height < WildTerrain.SEA && s.exactHeight < WildTerrain.SEA) {
            out.fluid(WildTerrain.SEA, "minecraft:water");
        }
    }

    /** How far under the surface of the city the ordinary underground (ores, monsters, lava lakes) begins. */
    private static final int UNDERGROUND = 8;

    @Override
    public String biomeAt(int x, int y, int z) {
        int r = Math.max(Math.abs(x), Math.abs(z));
        if (r <= CITY_EDGE) {
            // the streets have no features and no monsters of their own; below them the rock is the rock of the ordinary world
            return y < ground.at(x, z) - UNDERGROUND && !deepStructure(x, z) ? UNDERGROUND_BIOME : BIOME;
        }
        WildTerrain.Sample s = WILD.get();
        WildTerrain.sample(x, z, EDGE, s);
        return s.biome;
    }

    /** The biome of the rock under the city: the ordinary plains, whose underground features and monsters it brings along. */
    public static final String UNDERGROUND_BIOME = "minecraft:plains";

    private boolean deepStructure(int x, int z) {
        for (Rect r : deepStructures) {
            if (r.has(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static final List<String> BIOME_LIST;

    static {
        List<String> all = new ArrayList<>();
        all.add(BIOME);
        all.addAll(List.of(WildTerrain.BIOMES));
        BIOME_LIST = List.copyOf(all);
    }

    @Override
    public List<String> biomes() {
        return BIOME_LIST;
    }

    @Override
    public boolean carved(int x, int y, int z, int surface) {
        int r = Math.max(Math.abs(x), Math.abs(z));
        if (r > CITY_EDGE + 24) {
            return WildTerrain.cave(x, y, z, surface, MIN_Y, WildTerrain.ROOF, true);
        }
        // under the city there are caves too, but never one that reaches the surface or the foundations of the quest places
        return !deepStructure(x, z) && WildTerrain.cave(x, y, z, surface, MIN_Y, UNDERGROUND + 1, false);
    }

    @Override
    public boolean wilderness(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) > CITY_EDGE + 8;
    }

    @Override
    public boolean farFromTheCity(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) > CITY_EDGE + BLEND + 150;
    }

    @Override
    public List<StructurePlacement> fixedPlacements() {
        return placements;
    }

    @Override
    public List<StructurePlacement> scatterIn(int minX, int minZ, int maxX, int maxZ) {
        List<StructurePlacement> out = new ArrayList<>();
        // forest belt around the district: jittered grid of broadleaf trees on the embankment
        int cell = 5;
        for (int cx = Math.floorDiv(minX - 5, cell); cx <= Math.floorDiv(maxX + 5, cell); cx++) {
            for (int cz = Math.floorDiv(minZ - 5, cell); cz <= Math.floorDiv(maxZ + 5, cell); cz++) {
                int tx = cx * cell + Noise.range(SEED + 3, cx, cz, cell);
                int tz = cz * cell + Noise.range(SEED + 4, cx, cz, cell);
                int r = Math.max(Math.abs(tx), Math.abs(tz));
                if (r < EDGE - 6 || r > EDGE + 36) {
                    continue;
                }
                if (Noise.hash01(SEED + 5, cx, cz) > 0.82) {
                    continue;
                }
                Blueprint tree = Props.tree(Noise.range(SEED + 6, cx, cz, Props.TREE_VARIANTS));
                TerrainColumn col = new TerrainColumn();
                column(tx, tz, col);
                if (col.fluidY > col.height) {
                    continue;
                }
                int half = tree.sizeX() / 2;
                StructurePlacement sp = new StructurePlacement("forest", tree, tx - half, col.height + 1, tz - half);
                if (sp.intersectsXZ(minX, minZ, maxX, maxZ) && !onBuilding(sp)) {
                    out.add(sp);
                }
            }
        }
        return out;
    }

    /** Whether the crown of a tree of the forest belt would grow into a building. */
    private boolean onBuilding(StructurePlacement tree) {
        for (StructurePlacement p : placements) {
            if (!p.id().equals("tree") && p.intersectsXZ(tree.x() - 1, tree.z() - 1, tree.maxX() + 1, tree.maxZ() + 1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<MarkerPos> planMarkers() {
        return markers;
    }

    @Override
    public int[] spawn() {
        return new int[] {2, GROUND + 3, -3};
    }

    /** Every placement and marker that stands on the ground gets its height now that the plateaus are final. */
    private void resolve() {
        List<StructurePlacement> done = new ArrayList<>(placements.size());
        for (StructurePlacement sp : placements) {
            int[] on = onGround.get(sp);
            done.add(on == null ? sp : new StructurePlacement(sp.id(), sp.blueprint(), sp.x(), ground.at(on[0], on[1]) + on[2], sp.z()));
        }
        placements.clear();
        placements.addAll(done);
        for (Object[] m : pendingMarkers) {
            int x = (Integer) m[1];
            int y = (Integer) m[2];
            int z = (Integer) m[3];
            if (Math.abs(y - GROUND) <= 2) {
                y = ground.at(x, z) + (y - GROUND);
            }
            markers.add(new MarkerPos("district:" + m[0], x, y, z, 1, 1, 1, (String) m[4]));
        }
        pendingMarkers.clear();
        // the places that reach deep under the street keep the ordinary underground away from them
        for (StructurePlacement sp : placements) {
            int surface = ground.at((sp.x() + sp.maxX()) / 2, (sp.z() + sp.maxZ()) / 2);
            if (sp.y() < surface - UNDERGROUND) {
                deepStructures.add(new Rect(sp.x() - 16, sp.z() - 16, sp.maxX() + 16, sp.maxZ() + 16));
            }
        }
    }

    // ================================================================== ground

    static boolean inAny(List<Rect> rects, int x, int z) {
        for (Rect r : rects) {
            if (r.has(x, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoad(int x, int z) {
        return inAny(ROADS, x, z);
    }

    private boolean isSidewalk(int x, int z) {
        for (Rect r : ROADS) {
            if (r.grow(3).has(x, z) && !r.has(x, z)) {
                return true;
            }
        }
        return false;
    }

    private String topBlock(int x, int z) {
        long h = Noise.hash(SEED, x, z);
        if (PLAZA.has(x, z)) {
            int d = Math.max(Math.abs(x + 36), Math.abs(z));
            if (d <= 1) {
                return Pal.GRASS; // flowerbed in the middle of the turning loop
            }
            return (h & 7) == 0 ? Pal.BRICKS_MOSSY : (h & 7) == 1 ? Pal.COBBLE : Pal.BRICKS;
        }
        if (TRAM_BED.has(x, z)) {
            return (h & 3) == 0 ? Pal.COBBLE : Pal.GRAVEL;
        }
        if (isRoad(x, z)) {
            return (h & 15) == 0 ? "minecraft:stone" : (h & 15) == 1 ? Pal.CONCRETE_GREY : (h & 31) == 2 ? Pal.ANDESITE : Pal.CONCRETE_DARK;
        }
        if (isSidewalk(x, z)) {
            return (h & 7) == 0 ? Pal.BRICKS_CRACKED : (h & 15) == 1 ? Pal.BRICKS_MOSSY : (h & 15) == 2 ? Pal.ANDESITE : Pal.BRICKS;
        }
        if (inAny(LANES, x, z)) {
            return (h & 3) == 0 ? Pal.COBBLE : (h & 3) == 1 ? Pal.PATH : Pal.GRAVEL;
        }
        double patch = Noise.fbm2(SEED + 1, x * 0.06, z * 0.06, 2);
        if (patch > 0.74) {
            return Pal.COARSE_DIRT;
        }
        if (patch < 0.22 && (h & 3) == 0) {
            return Pal.PATH;
        }
        return Pal.GRASS;
    }

    private String decorAt(int x, int z, String top) {
        if (z == 0 && x >= -43 && x <= 150) {
            // tram rails; east of the stop the line is broken and overgrown ("the track looked unusable")
            if (x < 60) {
                return "minecraft:rail[shape=east_west]";
            }
            int seg = Math.floorDiv(x, 5);
            double broken = Noise.hash01(SEED + 21, seg, 0);
            if (broken < 0.62) {
                return (Noise.hash(SEED, x, 9) & 3) == 0 ? "minecraft:grass" : null;
            }
            return "minecraft:rail[shape=east_west]";
        }
        if (!top.equals(Pal.GRASS)) {
            return null;
        }
        long h = Noise.hash(SEED + 30, x, z);
        int roll = (int) Math.floorMod(h, 100L);
        if (roll < 16) {
            return "minecraft:grass";
        }
        if (roll < 18) {
            return "minecraft:dandelion";
        }
        if (roll < 19) {
            return "minecraft:poppy";
        }
        if (roll < 20) {
            return "minecraft:cornflower";
        }
        return null;
    }

    // ================================================================== layout

    private void layout() {
        tramStop();
        northCourtyard();
        southCourtyard();
        westSide();
        westFlank();
        northEast();
        market();
        southEast();
        northStrip();
        southStrip();
        utilities();
        garageCooperative();
        streetFurniture();
        questMarkers();
        postgame();
    }

    /** Where the quests put people: no building may stand on these cells (the cats, the groups of the night, the chase, the portal). */
    private static final int[][] CATS = {
            {-52, -41}, {-2, -52}, {30, -33}, {-72, 20}, {-20, 60}, {36, 52}, {70, -28},
            {118, -30}, {144, 22}, {-124, 30}, {-98, 86}, {58, 82}
    };
    private static final int[][] NEUTRALS = {{-20, -30}, {30, 40}, {-45, 38}, {95, -45}};
    private static final int[][] POIS = {{-30, -36}, {-17, 38}, {70, -9}, {-110, 72}, {110, -45}, {130, 45}};
    private static final int[][] OTHER_SPOTS = {{-101, 37}, {61, -9}, {59, -10}, {16, -10}, {2, -3}, {-11, -1}};

    /** The cells that stay free of buildings: the spots above, the nodes of the chase and the ways between them. */
    private static List<int[]> keepFree() {
        List<int[]> out = new ArrayList<>();
        for (int[][] group : new int[][][] {CATS, NEUTRALS, POIS, OTHER_SPOTS, DEBTOR_NODES}) {
            out.addAll(List.of(group));
        }
        for (int[] e : DEBTOR_EDGES) {
            int[] a = DEBTOR_NODES[e[0]];
            int[] b = DEBTOR_NODES[e[1]];
            int n = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])) / 2;
            for (int i = 1; i < n; i++) {
                out.add(new int[] {a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n});
            }
        }
        return out;
    }

    private static final List<int[]> KEEP_FREE = keepFree();

    /** Whether a building may stand on the rectangle: off the streets, off the other buildings, off the places of the quests. */
    private String whyNot(Rect r, Rect bodyRect) {
        List<Rect> streets = new ArrayList<>();
        for (Rect road : ROADS) {
            streets.add(road.grow(3));
        }
        streets.add(PLAZA.grow(1));
        streets.add(TRAM_BED.grow(1));
        streets.addAll(LANES);
        for (Rect street : streets) {
            if (street.x1 <= r.x2 && street.x2 >= r.x1 && street.z1 <= r.z2 && street.z2 >= r.z1) {
                return "it stands on a street " + street;
            }
        }
        for (Rect o : occupied) {
            if (o.x1 <= r.x2 && o.x2 >= r.x1 && o.z1 <= r.z2 && o.z2 >= r.z1) {
                return "it overlaps another building " + o;
            }
        }
        for (int[] k : KEEP_FREE) {
            if (bodyRect.grow(1).has(k[0], k[1])) {
                return "it covers the place of a quest at " + k[0] + "," + k[1];
            }
        }
        return null;
    }

    /** Free-play objects: the seed bowls, the validator of the wrong tram stop and the bunker of Garage No. 0. */
    private void postgame() {
        int[][] bowls = {{-33, -40}, {36, 38}, {-83, 24}};
        for (int i = 0; i < bowls.length; i++) {
            propForce("seed_bowl_" + (i + 1), Props.seedBowl(i + 1), bowls[i][0], GROUND + 1, bowls[i][1]);
        }
        propForce("wrong_stop", Props.wrongStop(), 1, GROUND + 1, 2);
        // two rubbish heaps with district tokens (the first night must not depend on luck)
        propForce("stash_tokens_n", Props.stash("tokens"), -24, GROUND + 1, -41);
        propForce("stash_tokens_shop", Props.stash("tokens"), 72, GROUND + 1, -23);
        StructurePlacement g0 = new StructurePlacement("garage0", Garage0.blueprint(), Garage0.ORIGIN_X, GROUND - Garage0.TOP, Garage0.ORIGIN_Z);
        ground.plateau(g0.x(), g0.z(), g0.maxX(), g0.maxZ(), GROUND, 10);
        clearTrees(g0.x(), g0.z(), g0.maxX(), g0.maxZ());
        placements.add(g0);
    }

    private void building(String id, Blueprint bp, int bodyX, int bodyZ) {
        building(id, bp, bodyX, bodyZ, true);
    }

    /** A block of flats: {@code sections} entrances, {@code floors} storeys; (x, z) is the corner of the body at the lowest x and z. */
    private void panel(String id, int sections, int floors, Buildings.Theme theme, int turns, long seed, int x, int z) {
        building(id, Buildings.panelBlock(sections, floors, theme, turns, seed), x, z);
    }

    private void building(String id, Blueprint bp, int bodyX, int bodyZ, boolean strict) {
        Blueprint.Marker body = bp.marker("body");
        int ox = bodyX - body.x();
        int oz = bodyZ - body.z();
        StructurePlacement probe = new StructurePlacement(id, bp, ox, GROUND, oz);
        if (strict) {
            Rect bodyRect = new Rect(probe.x() + body.x(), probe.z() + body.z(), probe.x() + body.x() + body.sx() - 1, probe.z() + body.z() + body.sz() - 1);
            String why = whyNot(new Rect(probe.x(), probe.z(), probe.maxX(), probe.maxZ()), bodyRect);
            if (why != null) {
                layoutProblems.add(id + " at " + probe.x() + "," + probe.z() + ".." + probe.maxX() + "," + probe.maxZ() + ": " + why);
            }
        }
        // every building stands on a pad at the level of the streets (the land between the buildings undulates, the pads and the
        // streets do not: neighbours never differ in level, and no slope is steeper than the margin of a pad allows)
        int level = GROUND;
        StructurePlacement sp = new StructurePlacement(id, bp, ox, level, oz);
        ground.plateau(sp.x(), sp.z(), sp.maxX(), sp.maxZ(), level, 8);
        clearTrees(sp.x(), sp.z(), sp.maxX(), sp.maxZ());
        placements.add(sp);
        occupied.add(new Rect(sp.x(), sp.z(), sp.maxX(), sp.maxZ()));
    }

    /** Remembers that a placement stands on the ground: its height is the ground at (refX, refZ) plus dy once the ground is final. */
    private StructurePlacement standing(StructurePlacement sp, int refX, int refZ, int dy) {
        onGround.put(sp, new int[] {refX, refZ, dy});
        return sp;
    }

    /** Buildings and quest objects always win over decorative trees. */
    private void clearTrees(int x1, int z1, int x2, int z2) {
        placements.removeIf(p -> p.id().equals("tree") && p.intersectsXZ(x1, z1, x2, z2));
    }

    private boolean free(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x += Math.max(1, x2 - x1)) {
            for (int z = z1; z <= z2; z += Math.max(1, z2 - z1)) {
                if (isRoad(x, z) || isSidewalk(x, z) || TRAM_BED.has(x, z) || PLAZA.has(x, z)) {
                    return false;
                }
            }
        }
        Rect probe = new Rect(x1, z1, x2, z2);
        for (Rect o : occupied) {
            if (o.x1 <= probe.x2 && o.x2 >= probe.x1 && o.z1 <= probe.z2 && o.z2 >= probe.z1) {
                return false;
            }
        }
        return true;
    }

    /** Places a prop whose layer 0 is the first block above the surface; skipped when the spot is taken. */
    private boolean prop(String id, Blueprint bp, int x, int z) {
        if (!free(x, z, x + bp.sizeX() - 1, z + bp.sizeZ() - 1)) {
            return false;
        }
        placements.add(standing(new StructurePlacement(id, bp, x, GROUND + 1, z), x + bp.sizeX() / 2, z + bp.sizeZ() / 2, 1));
        occupied.add(new Rect(x, z, x + bp.sizeX() - 1, z + bp.sizeZ() - 1));
        return true;
    }

    /**
     * Like {@link #prop} but ignores collisions (quest objects must exist). A height within two blocks of {@link #GROUND} means
     * "on the ground" (the ground may have risen or fallen there); any other height is taken as it is.
     */
    private void propForce(String id, Blueprint bp, int x, int y, int z) {
        clearTrees(x - 1, z - 1, x + bp.sizeX(), z + bp.sizeZ());
        StructurePlacement sp = new StructurePlacement(id, bp, x, y, z);
        placements.add(Math.abs(y - GROUND) <= 2 ? standing(sp, x + bp.sizeX() / 2, z + bp.sizeZ() / 2, y - GROUND) : sp);
    }

    private void tree(int x, int z, int variant) {
        Blueprint t = Props.tree(variant);
        int half = t.sizeX() / 2;
        int ox = x - half;
        int oz = z - half;
        // trees may overlap each other a little but never roads or buildings
        if (!free(x - 1, z - 1, x + 1, z + 1)) {
            return;
        }
        // the crown must not grow through a building or a prop either
        StructurePlacement sp = new StructurePlacement("tree", t, ox, GROUND + 1, oz);
        for (StructurePlacement other : placements) {
            if (!other.id().equals("tree") && other.intersectsXZ(sp.x(), sp.z(), sp.maxX(), sp.maxZ())) {
                return;
            }
        }
        placements.add(standing(sp, x, z, 1));
        occupied.add(new Rect(x - 1, z - 1, x + 1, z + 1));
    }

    /** A marker of the plan; a height within two blocks of {@link #GROUND} follows the ground (see {@link #propForce}). */
    private void marker(String name, int x, int y, int z, String data) {
        pendingMarkers.add(new Object[] {name, x, y, z, data});
    }

    private void treeLineX(int x1, int x2, int z, int spacing, long seed) {
        for (int x = x1; x <= x2; x += spacing) {
            tree(x + Noise.range(seed, x, z, 3) - 1, z + Noise.range(seed + 1, x, z, 3) - 1, Noise.range(seed + 2, x, z, Props.TREE_VARIANTS));
        }
    }

    private void treeLineZ(int z1, int z2, int x, int spacing, long seed) {
        for (int z = z1; z <= z2; z += spacing) {
            tree(x + Noise.range(seed, x, z, 3) - 1, z + Noise.range(seed + 1, x, z, 3) - 1, Noise.range(seed + 2, x, z, Props.TREE_VARIANTS));
        }
    }

    // ---------------------------------------------------------------- areas

    private void tramStop() {
        Blueprint stop = TramBuilders.tramStop();
        StructurePlacement sp = new StructurePlacement("tram_stop", stop, -12, GROUND, -9);
        clearTrees(sp.x(), sp.z(), sp.maxX(), sp.maxZ());
        placements.add(sp);
        occupied.add(new Rect(sp.x(), sp.z(), sp.maxX(), sp.maxZ()));
        // terminus: brick depot with the barred arch facing the turning loop
        building("tram_depot", Buildings.tramDepot(1), -64, -9, false);
        // the flowerbed in the middle of the turning loop
        propForce("loop_flowers", Props.flowers(3), -37, GROUND + 1, -1);
    }

    private void northCourtyard() {
        panel("block_a", 3, 5, Buildings.Theme.PANEL_GREY, 2, 11, -62, -57);
        panel("block_c", 2, 5, Buildings.Theme.PANEL_BEIGE, 2, 12, 4, -57);
        panel("block_b", 3, 5, Buildings.Theme.PANEL_BEIGE, 0, 13, -62, -26);
        panel("block_d", 2, 5, Buildings.Theme.PANEL_GREY, 0, 14, 4, -26);
        prop("playground_north", Props.playground(), -30, -40);
        // courtyard life
        treeLineX(-58, 38, -40, 9, 100);
        treeLineX(-58, 38, -31, 9, 120);
        prop("bench_n1", Props.bench(), -48, -36);
        prop("bench_n2", Props.bench(), -6, -36);
        // bench clue: tea glass left on the seat
        Blueprint bench = Props.bench();
        propForce("bench_clue", bench, 14, GROUND + 1, -36);
        propForce("clue_bench", Props.clue(1), 15, GROUND + 2, -36);
        prop("car_n1", Props.car(Props.CAR_BLUE, 0), -54, -40);
        prop("car_n2", Props.car(Props.CAR_WHITE, 0), 22, -40);
        prop("car_n3", Props.car(Props.CAR_RUST, 1), -14, -34);
        // supply stashes for the kiosk (planks) and junk
        propForce("stash_planks", Props.dumpster("kiosk_planks"), 33, GROUND + 1, -40);
        propForce("dumpster_n1", Props.dumpster("junk"), -58, GROUND + 1, -32);
        propForce("dumpster_n2", Props.dumpster("junk"), -24, GROUND + 1, -60);
        propForce("stash_seeds_n", Props.stash("seeds"), -28, GROUND + 1, -41);
        for (int x = -56; x <= 36; x += 14) {
            prop("flowers_n" + x, Props.flowers(6), x, -33);
        }
    }

    private void southCourtyard() {
        panel("block_e", 3, 5, Buildings.Theme.PANEL_GREY, 2, 21, -62, 16);
        panel("block_f", 2, 5, Buildings.Theme.PANEL_BEIGE, 2, 22, 4, 16);
        panel("block_g", 3, 5, Buildings.Theme.PANEL_ORANGE, 0, 23, -62, 45);
        // kindergarten
        building("kindergarten", Buildings.kindergarten(Buildings.HouseStyle.LILAC, 0, 24), 8, 46);
        prop("playground_south", Props.playground(), -26, 33);
        propForce("clue_playground", Props.clue(2), -21, GROUND + 1, 37);
        treeLineX(-58, 38, 32, 9, 140);
        treeLineX(-58, -30, 40, 9, 160);
        prop("bench_s1", Props.bench(), -46, 38);
        prop("bench_s2", Props.bench(), 12, 38);
        prop("car_s1", Props.car(Props.CAR_YELLOW, 0), 24, 36);
        prop("car_s2", Props.car(Props.CAR_GREEN, 0), -44, 33);
        propForce("stash_iron", Props.dumpster("kiosk_iron"), 34, GROUND + 1, 36);
        propForce("dumpster_s1", Props.dumpster("junk"), 34, GROUND + 1, 40);
        propForce("stash_seeds_s", Props.stash("seeds"), -8, GROUND + 1, 35);
        prop("fence_k", Props.chainFence(26, true), 7, 44);
        for (int x = -56; x <= 36; x += 16) {
            prop("flowers_s" + x, Props.flowers(6), x, 41);
        }
    }

    private void westSide() {
        panel("block_w1", 3, 5, Buildings.Theme.PANEL_GREY, 1, 31, -100, -56);
        panel("block_w2", 2, 5, Buildings.Theme.PANEL_BEIGE, 1, 32, -100, -1);
        treeLineZ(-52, 30, -86, 8, 170);
        treeLineZ(-4, 30, -110, 10, 171);
        propForce("dumpster_w1", Props.dumpster("junk"), -86, GROUND + 1, 12);
    }

    private void northEast() {
        building("old_shop", Buildings.oldShop(2), 64, -20, false);
        // the marked foundation for the abandoned kiosk
        propForce("kiosk_foundation", Props.kioskFoundation(), 58, GROUND, -11);
        propForce("clue_tea", Props.clue(0), 66, GROUND + 1, -23);
        propForce("dumpster_shop", Props.dumpster("junk"), 80, GROUND + 1, -23);
        propForce("stash_seeds_shop", Props.stash("seeds"), 70, GROUND + 1, -23);
        int[] xs = {62, 82, 106, 126};
        Buildings.HouseStyle[] styles = Buildings.HouseStyle.values();
        for (int i = 0; i < xs.length; i++) {
            houseLot("house_ne" + i, xs[i], -56, 12 + (i % 2) * 2, 9, styles[i % 3], 2, 40 + i);
        }
        for (int i = 0; i < 3; i++) {
            int x = 84 + i * 21;
            houseLot("house_nes" + i, x, -28, 12, 9, styles[(i + 1) % 3], 0, 50 + i);
        }
        treeLineX(58, 146, -9, 12, 180);
    }

    private void southEast() {
        for (int i = 0; i < 4; i++) {
            houseLot("house_se" + i, 62 + i * 21, 18, 12 + (i % 2) * 2, 9, Buildings.HouseStyle.values()[(i + 2) % 3], 2, 60 + i);
        }
        for (int i = 0; i < 2; i++) {
            houseLot("house_ses" + i, 62 + i * 21, 44, 12, 9, Buildings.HouseStyle.values()[i % 3], 0, 70 + i);
        }
        // trash yard with a burnt-out look
        for (int i = 0; i < 3; i++) {
            propForce("dumpster_t" + i, Props.dumpster(i == 1 ? "trash" : "junk"), 122 + i * 5, GROUND + 1, 44);
        }
        propForce("clue_trash", Props.clue(3), 128, GROUND + 1, 49);
        propForce("pallets_t", Props.pallets(), 134, GROUND + 1, 46);
        prop("car_t", Props.car(Props.CAR_RUST, 1), 140, 40);
        treeLineX(58, 146, 9, 12, 190);
    }

    /**
     * The north strip, behind the street at z = -66: two rows of blocks that face a courtyard between them (the row at the street
     * has its back to the street and its entrances on the courtyard side, as in the courtyards near the tram), the houses of the
     * private sector in the east and the garages of Garage No. 0 in the corner.
     */
    private void northStrip() {
        Buildings.Theme grey = Buildings.Theme.PANEL_GREY;
        Buildings.Theme beige = Buildings.Theme.PANEL_BEIGE;
        Buildings.Theme orange = Buildings.Theme.PANEL_ORANGE;
        // the row at the street (entrances to the north, into the courtyard) and the row at the back (entrances to the south)
        panel("block_n1", 3, 5, beige, 0, 41, -62, -89);
        panel("block_n2", 2, 5, grey, 0, 42, 4, -89);
        panel("block_n3", 2, 5, orange, 0, 43, -146, -89);
        panel("block_n4", 3, 5, grey, 2, 44, -62, -129);
        panel("block_n5", 2, 5, beige, 2, 45, 4, -129);
        panel("block_n6", 3, 5, orange, 2, 46, -146, -129);
        panel("tower_n1", 1, 9, grey, 0, 47, -106, -89);
        // the courtyards between the rows
        for (int x : new int[] {-52, -4, 22}) {
            prop("playground_n" + x, Props.playground(), x, -108);
        }
        treeLineX(-140, 38, -95, 9, 300);
        treeLineX(-140, 38, -112, 9, 301);
        for (int x = -140; x <= 36; x += 22) {
            prop("bench_ns" + x, Props.bench(), x, -102);
            prop("flowers_ns" + x, Props.flowers(6), x + 4, -99);
        }
        prop("car_ns1", Props.car(Props.CAR_WHITE, 0), -20, -100);
        prop("car_ns2", Props.car(Props.CAR_BLUE, 1), 12, -104);
        prop("car_ns3", Props.car(Props.CAR_RUST, 0), -96, -102);
        // the east: the private sector (the lane runs between the two rows of houses)
        for (int i = 0; i < 3; i++) {
            houseLot("house_nn" + i, 62 + i * 21, -127, 12 + (i % 2) * 2, 9, Buildings.HouseStyle.values()[(i + 1) % 3], 2, 80 + i);
        }
        for (int i = 0; i < 4; i++) {
            houseLot("house_nm" + i, 62 + i * 21, -92, 12 + ((i + 1) % 2) * 2, 9, Buildings.HouseStyle.values()[(i + 2) % 3], 0, 90 + i);
        }
        treeLineX(58, 146, -108, 12, 310);
    }

    /** The south strip: the same as in the north, mirrored around the main street; Garage No. 13 fills the south-west. */
    private void southStrip() {
        Buildings.Theme grey = Buildings.Theme.PANEL_GREY;
        Buildings.Theme beige = Buildings.Theme.PANEL_BEIGE;
        Buildings.Theme orange = Buildings.Theme.PANEL_ORANGE;
        panel("block_s1", 3, 5, grey, 2, 51, -62, 78);
        panel("block_s2", 2, 5, orange, 2, 52, 4, 78);
        panel("block_s3", 3, 5, orange, 0, 53, -62, 118);
        panel("block_s4", 2, 5, grey, 0, 54, 4, 118);
        for (int x : new int[] {-48, -8, 18}) {
            prop("playground_s" + x, Props.playground(), x, 98);
        }
        treeLineX(-58, 38, 95, 9, 320);
        treeLineX(-58, 38, 112, 9, 321);
        for (int x = -56; x <= 36; x += 22) {
            prop("bench_ss" + x, Props.bench(), x, 104);
            prop("flowers_ss" + x, Props.flowers(6), x + 4, 101);
        }
        prop("car_ss1", Props.car(Props.CAR_GREEN, 0), -30, 100);
        prop("car_ss2", Props.car(Props.CAR_YELLOW, 1), 30, 106);
        for (int i = 0; i < 4; i++) {
            houseLot("house_sn" + i, 62 + i * 21, 84, 12 + (i % 2) * 2, 9, Buildings.HouseStyle.values()[i % 3], 2, 100 + i);
        }
        for (int i = 0; i < 4; i++) {
            houseLot("house_sm" + i, 62 + i * 21, 119, 12 + ((i + 1) % 2) * 2, 9, Buildings.HouseStyle.values()[(i + 1) % 3], 0, 110 + i);
        }
        treeLineX(58, 146, 108, 12, 330);
    }

    /** The private sector west of the blocks (houses turned to the east), the pitch between them and the blocks, the allotment gardens. */
    private void westFlank() {
        Buildings.HouseStyle[] styles = Buildings.HouseStyle.values();
        for (int i = 0; i < 4; i++) {
            houseLot("house_wn" + i, -143, -56 + i * 16, 12 + (i % 2) * 2, 9, styles[i % 3], 1, 120 + i);
        }
        building("field_w", Yard.field(19, 42), -124, -50);
        treeLineZ(-52, -8, -127, 8, 340);
        treeLineZ(-52, -8, -105, 8, 341);
        // the allotments: rows of beds with a shed at the end of every row
        for (int row = 0; row < 4; row++) {
            int z = 10 + row * 12;
            for (int col = 0; col < 2; col++) {
                prop("plot_" + row + col, Props.gardenPlot(10, 8), -145 + col * 13, z);
            }
            building("shed_" + row, Yard.shed(1, 150 + row), -121, z, false);
        }
        propForce("pallets_w", Props.pallets(), -144, GROUND + 1, 58);
    }

    /** The market along the south side of the main street: stalls with awnings. */
    private void market() {
        for (int i = 0; i < 7; i++) {
            prop("stall_" + i, Yard.stall(5, 0, 130 + i), 64 + i * 7, 10);
        }
    }

    /** Garages, a boiler house and a transformer station at the northern and the southern edge of the district. */
    private void utilities() {
        // north: the garages turn their gates to the north, the boiler house stands beside the street
        building("garages_n1", Industry.garageRow(10, 0, 6), -146, -141);
        building("boiler", Industry.boilerHouse(0, 5), -102, -142);
        building("substation_n", Industry.substation(0, 3), -60, -141);
        building("garages_n2", Industry.garageRow(8, 0, 7), -46, -141);
        building("garages_n3", Industry.garageRow(8, 0, 8), 4, -141);
        // south: the same, turned to face south
        building("garages_s1", Industry.garageRow(10, 2, 9), -60, 135);
        building("substation_s", Industry.substation(2, 4), -12, 135);
        building("garages_s2", Industry.garageRow(8, 2, 10), 4, 135);
        building("garages_s3", Industry.garageRow(8, 2, 11), 60, 135);
        for (int x : new int[] {-56, -30, 10, 30}) {
            prop("car_nu" + x, Props.car(Props.CAR_BLUE, 0), x, -131);
            prop("car_su" + x, Props.car(Props.CAR_WHITE, 0), x, 132);
        }
    }

    /**
     * House with a fenced front garden, bush and tree. {@code turns}: 0 = front faces north,
     * 2 = front faces south. The 3-block porch margin of the blueprint is followed by a 3-deep garden
     * and the fence.
     */
    private void houseLot(String id, int x, int z, int w, int d, Buildings.HouseStyle style, int turns, long seed) {
        Blueprint h = Buildings.plasterHouse(w, d, style, turns, seed);
        building(id, h, x, z);
        int tree = (int) Math.floorMod(seed, (long) Props.TREE_VARIANTS);
        switch (turns & 3) {
            case 0, 2 -> {
                int gz = turns == 2 ? z + d + 3 : z - 6;
                int fz = turns == 2 ? z + d + 6 : z - 7;
                prop(id + "_garden", Props.gardenPlot(w - 2, 3), x + 1, gz);
                prop(id + "_fence", Props.chainFence(w + 6, true), x - 3, fz);
                tree(x + w + 2, z + 3, tree);
                prop(id + "_bush", Props.bush((int) seed), x - 2, z + d / 2);
            }
            default -> {
                // the house faces east (1) or west (3): its depth runs along x, its width along z
                int gx = turns == 1 ? x + d + 3 : x - 6;
                int fx = turns == 1 ? x + d + 6 : x - 7;
                prop(id + "_garden", Yard.rotated(Props.gardenPlot(w - 2, 3), 1), gx, z + 1);
                prop(id + "_fence", Yard.rotated(Props.chainFence(w + 6, true), 1), fx, z - 3);
                tree(turns == 1 ? x + 3 : x + d - 4, z + w + 2, tree);
                prop(id + "_bush", Props.bush((int) seed), x + d / 2, z - 2);
            }
        }
    }

    private void garageCooperative() {
        Blueprint garage = GarageComplex.garage13();
        propForce("garage13", garage, GarageComplex.ORIGIN_X, GarageComplex.ORIGIN_Y, GarageComplex.ORIGIN_Z);
        ground.plateau(GarageComplex.ORIGIN_X, GarageComplex.ORIGIN_Z, GarageComplex.ORIGIN_X + garage.sizeX() - 1, GarageComplex.ORIGIN_Z + garage.sizeZ() - 1, GROUND, 10);
        occupied.add(new Rect(GarageComplex.ORIGIN_X, GarageComplex.ORIGIN_Z,
                GarageComplex.ORIGIN_X + garage.sizeX() - 1, GarageComplex.ORIGIN_Z + garage.sizeZ() - 1));
    }

    private void streetFurniture() {
        // utility poles with lamps along the main street (north sidewalk) and the cross streets
        for (int x = -26; x <= 146; x += 24) {
            if (x > -16 && x < 16) {
                continue;
            }
            polePlacement(x, -7);
        }
        for (int z = -140; z <= 140; z += 28) {
            if (Math.abs(z) < 12) {
                continue;
            }
            polePlacement(-63, z);
            polePlacement(57, z + 4);
        }
        // parked cars on the main street (abandoned)
        prop("car_m1", Props.car(Props.CAR_BLUE, 0), -28, 3);
        prop("car_m2", Props.car(Props.CAR_WHITE, 0), 32, -4);
        prop("car_m3", Props.car(Props.CAR_RUST, 0), 88, 3);
        // trees along the main street sidewalks
        treeLineX(-26, 146, 8, 11, 210);
        // trees along cross streets
        treeLineZ(-140, 140, -64, 10, 220);
        treeLineZ(-140, 140, 56, 10, 230);
        // hedges along the plaza
        prop("hedge_p1", Props.bush(1), -44, -12);
        prop("hedge_p2", Props.bush(1), -44, 10);
    }

    private void polePlacement(int x, int z) {
        Blueprint p = Props.utilityPole(true);
        if (free(x - 2, z - 2, x + 2, z + 2)) {
            placements.add(standing(new StructurePlacement("pole", p, x - 2, GROUND + 1, z - 2), x, z, 1));
            occupied.add(new Rect(x - 1, z - 1, x + 1, z + 1));
        }
    }

    // ---------------------------------------------------------------- markers

    private void questMarkers() {
        int y = GROUND + 1;
        // points of interest for "Десь я не туди вийшов" (explore 4 of 6)
        marker("poi_north_courtyard", -30, y, -36, "r=9");
        marker("poi_playground", -17, y, 38, "r=9");
        marker("poi_shop", 70, y, -9, "r=9");
        marker("poi_garages", -110, y, 72, "r=10");
        marker("poi_houses", 110, y, -45, "r=12");
        marker("poi_trash", 130, y, 45, "r=9");
        // Pan Shlahbaum stands next to the kiosk
        marker("shlahbaum_spot", 61, y, -9, "");
        marker("kiosk_spot", 59, GROUND, -10, "");
        // debtor chase
        for (int i = 0; i < DEBTOR_NODES.length; i++) {
            boolean dead = i == 8 || i == 9;
            marker("debtor_wp_" + i, DEBTOR_NODES[i][0], y, DEBTOR_NODES[i][1], dead ? "dead=1" : "");
        }
        marker("debtor_spawn", -101, y, 37, "");
        // neutral seed-asking groups (appear at night)
        for (int i = 0; i < NEUTRALS.length; i++) {
            marker("neutral_" + i, NEUTRALS[i][0], y, NEUTRALS[i][1], "");
        }
        // last tram event
        marker("tram_origin", -11, GROUND + 1, -1, "");
        marker("tram_fog_start", 120, GROUND + 1, 0, "");
        marker("tram_stop_x", -11, GROUND + 1, -1, "");
        String[][] waves = {{"-34", "0"}, {"-30", "-6"}, {"34", "0"}, {"30", "6"}, {"0", "-12"}, {"8", "-12"}, {"0", "13"}, {"-8", "13"}};
        for (int i = 0; i < waves.length; i++) {
            marker("wave_" + (i + 1), Integer.parseInt(waves[i][0]), y, Integer.parseInt(waves[i][1]), "");
        }
        // postgame: twelve hidden cats, seed bowls and the secret tram
        int[][] cats = CATS;
        for (int i = 0; i < cats.length; i++) {
            marker("cat_spot_" + (i + 1), cats[i][0], y, cats[i][1], "");
        }
        marker("seed_bowl_1", -33, y, -40, "");
        marker("seed_bowl_2", 36, y, 38, "");
        marker("seed_bowl_3", -83, y, 24, "");
        // district portal (the way back from Chromandivka): right next to the tram stop; the frame stands in the ground from
        // the start (a dormant portal), the portal service only fills and empties the pane above it
        propForce("portal_district_frame", Props.districtPortal(), 14, GROUND, -10);
        marker("portal_district", 16, GROUND + 1, -10, "");
        marker("wrong_tram_stop", 0, GROUND + 1, 0, "");
    }
}
