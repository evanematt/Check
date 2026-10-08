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
    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;
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

    private static DistrictPlan instance;

    public static synchronized DistrictPlan get() {
        if (instance == null) {
            instance = new DistrictPlan();
        }
        return instance;
    }

    public DistrictPlan() {
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
    public void column(int x, int z, TerrainColumn out) {
        int r = Math.max(Math.abs(x), Math.abs(z));
        if (r > EDGE + 42) {
            out.reset(118, "minecraft:stone", "minecraft:stone", 3, "minecraft:stone");
            return;
        }
        if (r > EDGE + 2) {
            double t = (r - EDGE - 2) / 40.0;
            int h = GROUND + (int) Math.round(t * t * 24 + t * 6 + Noise.value2(SEED + 7, x * 0.15, z * 0.15) * 2);
            String top = r > EDGE + 36 ? "minecraft:stone" : (Noise.hash(SEED, x, z) & 7) == 0 ? Pal.COARSE_DIRT : Pal.GRASS;
            out.reset(h, top, Pal.DIRT, 3, "minecraft:stone");
            return;
        }
        String top = topBlock(x, z);
        out.reset(GROUND, top, top.equals(Pal.GRASS) || top.equals(Pal.COARSE_DIRT) || top.equals(Pal.PATH) ? Pal.DIRT : Pal.GRAVEL,
                top.equals(Pal.GRASS) || top.equals(Pal.COARSE_DIRT) || top.equals(Pal.PATH) ? 3 : 2, "minecraft:stone");
        out.decor = decorAt(x, z, top);
    }

    @Override
    public String biomeAt(int x, int y, int z) {
        return BIOME;
    }

    @Override
    public List<String> biomes() {
        return List.of(BIOME);
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
                int half = tree.sizeX() / 2;
                StructurePlacement sp = new StructurePlacement("forest", tree, tx - half, col.height + 1, tz - half);
                if (sp.intersectsXZ(minX, minZ, maxX, maxZ)) {
                    out.add(sp);
                }
            }
        }
        return out;
    }

    @Override
    public List<MarkerPos> planMarkers() {
        return markers;
    }

    @Override
    public int[] spawn() {
        return new int[] {2, GROUND + 3, -3};
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
        northEast();
        southEast();
        garageCooperative();
        streetFurniture();
        questMarkers();
        postgame();
    }

    /** Free-play objects: the seed bowls, the validator of the wrong tram stop and the bunker of Garage No. 0. */
    private void postgame() {
        int[][] bowls = {{-33, -40}, {36, 38}, {-83, 24}};
        for (int i = 0; i < bowls.length; i++) {
            propForce("seed_bowl_" + (i + 1), Props.seedBowl(i + 1), bowls[i][0], GROUND + 1, bowls[i][1]);
        }
        propForce("wrong_stop", Props.wrongStop(), 1, GROUND + 1, 2);
        // two rubbish heaps with district tokens (the first night must not depend on luck)
        propForce("stash_tokens_n", Props.stash("tokens"), -30, GROUND + 1, -44);
        propForce("stash_tokens_shop", Props.stash("tokens"), 72, GROUND + 1, -23);
        StructurePlacement g0 = new StructurePlacement("garage0", Garage0.blueprint(), Garage0.ORIGIN_X, GROUND - Garage0.TOP, Garage0.ORIGIN_Z);
        clearTrees(g0.x(), g0.z(), g0.maxX(), g0.maxZ());
        placements.add(g0);
    }

    private void building(String id, Blueprint bp, int bodyX, int bodyZ) {
        Blueprint.Marker body = bp.marker("body");
        int ox = bodyX - body.x();
        int oz = bodyZ - body.z();
        StructurePlacement sp = new StructurePlacement(id, bp, ox, GROUND, oz);
        clearTrees(sp.x(), sp.z(), sp.maxX(), sp.maxZ());
        placements.add(sp);
        occupied.add(new Rect(sp.x(), sp.z(), sp.maxX(), sp.maxZ()));
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
        placements.add(new StructurePlacement(id, bp, x, GROUND + 1, z));
        occupied.add(new Rect(x, z, x + bp.sizeX() - 1, z + bp.sizeZ() - 1));
        return true;
    }

    /** Like {@link #prop} but ignores collisions (quest objects must exist). */
    private void propForce(String id, Blueprint bp, int x, int y, int z) {
        clearTrees(x - 1, z - 1, x + bp.sizeX(), z + bp.sizeZ());
        placements.add(new StructurePlacement(id, bp, x, y, z));
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
        placements.add(sp);
        occupied.add(new Rect(x - 1, z - 1, x + 1, z + 1));
    }

    private void marker(String name, int x, int y, int z, String data) {
        markers.add(new MarkerPos("district:" + name, x, y, z, 1, 1, 1, data));
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
        building("tram_depot", Buildings.tramDepot(1), -64, -9);
        // the flowerbed in the middle of the turning loop
        propForce("loop_flowers", Props.flowers(3), -37, GROUND + 1, -1);
    }

    private void northCourtyard() {
        building("block_a", Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_GREY, 2, 11), -60, -56);
        building("block_c", Buildings.panelBlock(36, 5, Buildings.Theme.PANEL_BEIGE, 2, 12), -8, -56);
        building("block_b", Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_BEIGE, 0, 13), -60, -26);
        building("block_d", Buildings.panelBlock(36, 5, Buildings.Theme.PANEL_GREY, 0, 14), -8, -26);
        prop("playground_north", Props.playground(), -30, -40);
        // courtyard life
        treeLineX(-58, 38, -43, 9, 100);
        treeLineX(-58, 38, -28, 9, 120);
        prop("bench_n1", Props.bench(), -48, -36);
        prop("bench_n2", Props.bench(), -6, -36);
        // bench clue: tea glass left on the seat
        Blueprint bench = Props.bench();
        propForce("bench_clue", bench, 14, GROUND + 1, -36);
        propForce("clue_bench", Props.clue(1), 15, GROUND + 2, -36);
        prop("car_n1", Props.car(Props.CAR_BLUE, 0), -54, -41);
        prop("car_n2", Props.car(Props.CAR_WHITE, 0), 22, -41);
        prop("car_n3", Props.car(Props.CAR_RUST, 1), -14, -34);
        // supply stashes for the kiosk (planks) and junk
        propForce("stash_planks", Props.dumpster("kiosk_planks"), 33, GROUND + 1, -42);
        propForce("dumpster_n1", Props.dumpster("junk"), -58, GROUND + 1, -30);
        propForce("dumpster_n2", Props.dumpster("junk"), -24, GROUND + 1, -59);
        propForce("stash_seeds_n", Props.stash("seeds"), -33, GROUND + 1, -44);
        for (int x = -56; x <= 36; x += 14) {
            prop("flowers_n" + x, Props.flowers(6), x, -41);
        }
    }

    private void southCourtyard() {
        building("block_e", Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_GREY, 2, 21), -60, 16);
        building("block_f", Buildings.panelBlock(36, 5, Buildings.Theme.PANEL_BEIGE, 2, 22), -8, 16);
        building("block_g", Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_ORANGE, 0, 23), -60, 46);
        // kindergarten
        building("kindergarten", Buildings.plasterHouse(24, 10, Buildings.HouseStyle.LILAC, 0, 24), -2, 46);
        prop("playground_south", Props.playground(), -26, 34);
        propForce("clue_playground", Props.clue(2), -21, GROUND + 1, 37);
        treeLineX(-58, 38, 31, 9, 140);
        treeLineX(-58, -30, 44, 9, 160);
        prop("bench_s1", Props.bench(), -46, 38);
        prop("bench_s2", Props.bench(), 12, 38);
        prop("car_s1", Props.car(Props.CAR_YELLOW, 0), 20, 40);
        prop("car_s2", Props.car(Props.CAR_GREEN, 0), -44, 33);
        propForce("stash_iron", Props.dumpster("kiosk_iron"), 34, GROUND + 1, 36);
        propForce("dumpster_s1", Props.dumpster("junk"), 34, GROUND + 1, 42);
        propForce("stash_seeds_s", Props.stash("seeds"), -8, GROUND + 1, 33);
        prop("fence_k", Props.chainFence(26, true), -3, 44);
        for (int x = -56; x <= 36; x += 16) {
            prop("flowers_s" + x, Props.flowers(6), x, 33);
        }
    }

    private void westSide() {
        building("block_w1", Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_GREY, 1, 31), -100, -40);
        building("block_w2", Buildings.panelBlock(36, 5, Buildings.Theme.PANEL_BEIGE, 1, 32), -100, 14);
        treeLineZ(-48, 56, -86, 8, 170);
        treeLineZ(-48, 56, -110, 10, 171);
        prop("fence_w1", Props.chainFence(24, true), -130, -50);
        // a small allotment garden
        prop("garden_w1", Props.gardenPlot(10, 8), -130, -20);
        prop("garden_w2", Props.gardenPlot(10, 8), -130, -8);
        propForce("dumpster_w1", Props.dumpster("junk"), -86, GROUND + 1, 12);
    }

    private void northEast() {
        building("old_shop", Buildings.oldShop(2), 64, -20);
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
     * House with a fenced front garden, bush and tree. {@code turns}: 0 = front faces north,
     * 2 = front faces south. The 3-block porch margin of the blueprint is followed by a 3-deep garden
     * and the fence.
     */
    private void houseLot(String id, int x, int z, int w, int d, Buildings.HouseStyle style, int turns, long seed) {
        Blueprint h = Buildings.plasterHouse(w, d, style, turns, seed);
        building(id, h, x, z);
        int gz = turns == 2 ? z + d + 3 : z - 6;
        int fz = turns == 2 ? z + d + 6 : z - 7;
        prop(id + "_garden", Props.gardenPlot(w - 2, 3), x + 1, gz);
        prop(id + "_fence", Props.chainFence(w + 6, true), x - 3, fz);
        tree(x + w + 2, z + 3, (int) Math.floorMod(seed, (long) Props.TREE_VARIANTS));
        prop(id + "_bush", Props.bush((int) seed), x - 2, z + d / 2);
    }

    private void garageCooperative() {
        Blueprint garage = GarageComplex.garage13();
        propForce("garage13", garage, GarageComplex.ORIGIN_X, GarageComplex.ORIGIN_Y, GarageComplex.ORIGIN_Z);
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
            placements.add(new StructurePlacement("pole", p, x - 2, GROUND + 1, z - 2));
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
        marker("neutral_0", -20, y, -30, "");
        marker("neutral_1", 30, y, 40, "");
        marker("neutral_2", -45, y, 52, "");
        marker("neutral_3", 95, y, -45, "");
        // last tram event
        marker("tram_origin", -11, GROUND + 1, -1, "");
        marker("tram_fog_start", 120, GROUND + 1, 0, "");
        marker("tram_stop_x", -11, GROUND + 1, -1, "");
        String[][] waves = {{"-34", "0"}, {"-30", "-6"}, {"34", "0"}, {"30", "6"}, {"0", "-12"}, {"8", "-12"}, {"0", "13"}, {"-8", "13"}};
        for (int i = 0; i < waves.length; i++) {
            marker("wave_" + (i + 1), Integer.parseInt(waves[i][0]), y, Integer.parseInt(waves[i][1]), "");
        }
        // postgame: twelve hidden cats, seed bowls and the secret tram
        int[][] cats = {
                {-52, -41}, {-2, -52}, {30, -33}, {-72, 20}, {-20, 60}, {18, 48}, {70, -28},
                {118, -30}, {136, 20}, {-124, 30}, {-98, 86}, {58, 82}
        };
        for (int i = 0; i < cats.length; i++) {
            marker("cat_spot_" + (i + 1), cats[i][0], y, cats[i][1], "");
        }
        marker("seed_bowl_1", -33, y, -40, "");
        marker("seed_bowl_2", 36, y, 38, "");
        marker("seed_bowl_3", -83, y, 24, "");
        // district portal (the way back from Chromandivka): right next to the tram stop
        marker("portal_district", 16, GROUND + 1, -10, "");
        marker("wrong_tram_stop", 0, GROUND + 1, 0, "");
    }
}
