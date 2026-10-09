package com.lewandivka.core.world.gen;

import com.lewandivka.core.HandcraftedBlocks;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.Materials;
import com.lewandivka.core.world.WorldPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The furniture of the furniture mod: every piece is a block of the mod with a block of the game as the stand-in, the stand-in
 * is what the checks of the core see, turned buildings keep the shapes of tables and sofas right.
 */
class DecorTest {

    private static String prop(String key, String name) {
        String body = Keys.preferred(key);
        int br = body.indexOf('[');
        if (br < 0) {
            return null;
        }
        for (String kv : body.substring(br + 1, body.length() - 1).split(",")) {
            if (kv.startsWith(name + "=")) {
                return kv.substring(name.length() + 1);
            }
        }
        return null;
    }

    private static List<Blueprint> placements() {
        List<Blueprint> out = new ArrayList<>();
        WorldPlan plan = DistrictPlan.get();
        plan.fixedPlacements().forEach(p -> out.add(p.blueprint()));
        return out;
    }

    @Test
    void aKeyNamesTheModBlockAndItsStandIn() {
        String key = Decor.chair("oak", "red", Dir.NORTH, "minecraft:oak_stairs");
        assertEquals("handcrafted:oak_chair[color=red,facing=north]|minecraft:oak_stairs[facing=south,half=bottom]", key);
        assertEquals("handcrafted:oak_chair[color=red,facing=north]", Keys.preferred(key));
        assertEquals("minecraft:oak_stairs[facing=south,half=bottom]", Keys.fallback(key));
        assertEquals("minecraft:oak_stairs", Keys.blockId(key));
        assertTrue(Keys.isEither(key));
        assertEquals("minecraft:stone", Keys.fallback("minecraft:stone"));
        assertFalse(Keys.isEither("minecraft:stone"));
    }

    @Test
    void theChecksOfTheCoreSeeTheStandIn() {
        assertEquals(Materials.Kind.SOLID, Materials.classify(Decor.chair("oak", "red", Dir.NORTH, "minecraft:oak_stairs")));
        assertEquals(Materials.Kind.SOLID, Materials.classify(Decor.table("oak", "single", "minecraft:oak_slab")));
        // plates on a table: the candle of the game is what counts, and a candle does not stop anyone
        assertEquals(Materials.Kind.PASS, Materials.classify(Decor.crockery(2, Dir.EAST, "minecraft:candle[candles=1,lit=true]")));
        assertEquals(Materials.Kind.SOLID, Materials.classify(Decor.cushion("minecraft:red_wool")));
    }

    @Test
    void turningATurnsBothHalves() {
        String key = Decor.chair("oak", "red", Dir.NORTH, "minecraft:oak_stairs");
        assertEquals("handcrafted:oak_chair[color=red,facing=east]|minecraft:oak_stairs[facing=west,half=bottom]", BlueprintBuilder.rotateKey(key, 1));
        assertEquals("handcrafted:oak_chair[color=red,facing=south]|minecraft:oak_stairs[facing=north,half=bottom]", BlueprintBuilder.rotateKey(key, 2));
        assertEquals(key, BlueprintBuilder.rotateKey(key, 4));
    }

    @Test
    void everyShapeOfATableIsOneTheModKnowsAndTheyAreAllDifferent() {
        Set<String> shapes = new TreeSet<>();
        for (int mask = 0; mask < 16; mask++) {
            String shape = Decor.tableShape((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
            assertTrue(HandcraftedBlocks.valuesOf("oak_table", "shape").contains(shape), "no such table shape: " + shape);
            shapes.add(shape);
        }
        assertEquals(16, shapes.size(), shapes.toString());
    }

    @Test
    void aTurnedTableKeepsItsShape() {
        for (int mask = 0; mask < 16; mask++) {
            boolean[] side = {(mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0};   // north, east, south, west
            String shape = Decor.tableShape(side[0], side[1], side[2], side[3]);
            for (int turns = 1; turns < 4; turns++) {
                // the neighbours seen from the turned building: the one that was in the north is now where north turned to
                boolean[] turned = new boolean[4];
                for (Dir d : Dir.values()) {
                    turned[d.turn(turns).ordinal()] = side[d.ordinal()];
                }
                String expected = Decor.tableShape(turned[0], turned[1], turned[2], turned[3]);
                String key = Keys.of("handcrafted:oak_table", "color", "none", "shape", shape);
                assertEquals(Keys.of("handcrafted:oak_table", "color", "none", "shape", expected), BlueprintBuilder.rotateKey(key, turns),
                        "shape " + shape + " turned " + turns + " times");
            }
        }
    }

    @Test
    void sofasJoinTheirNeighbours() {
        assertEquals("single", Decor.couchShape(false, false));
        assertEquals("left", Decor.couchShape(true, false));
        assertEquals("right", Decor.couchShape(false, true));
        assertEquals("middle", Decor.couchShape(true, true));
        for (String color : new String[] {"red", "magenta", "cyan", "gray", "purple", "brown"}) {
            assertTrue(HandcraftedBlocks.valuesOf("oak_couch", "color").contains(color), color);
        }
        for (String stairs : Furnish.SOFAS) {
            assertTrue(HandcraftedBlocks.valuesOf("oak_couch", "color").contains(Decor.sofaColor(stairs)), stairs);
        }
    }

    /** The shape written into every table of every building is the one its neighbours in the building ask for, whatever way the building is turned. */
    @Test
    void tablesOfEveryBuildingJoinTheirNeighbours() {
        int tables = 0;
        for (Blueprint bp : placements()) {
            for (int y = 0; y < bp.sizeY(); y++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    for (int x = 0; x < bp.sizeX(); x++) {
                        String key = bp.keyAt(x, y, z);
                        if (key == null || !Keys.preferred(key).startsWith("handcrafted:") || !Keys.blockId(Keys.preferred(key)).endsWith("_table")) {
                            continue;
                        }
                        tables++;
                        boolean[] side = new boolean[4];
                        for (Dir d : Dir.values()) {
                            String other = bp.keyAt(x + d.dx, y, z + d.dz);
                            side[d.ordinal()] = other != null && Keys.preferred(other).startsWith("handcrafted:")
                                    && Keys.blockId(Keys.preferred(other)).endsWith("_table");
                        }
                        assertEquals(Decor.tableShape(side[0], side[1], side[2], side[3]), prop(key, "shape"),
                                bp.id() + " at " + x + "," + y + "," + z + ": " + key);
                    }
                }
            }
        }
        assertTrue(tables > 100, "the buildings are furnished with " + tables + " tables of the mod");
    }

    @Test
    void sofasOfEveryBuildingJoinTheirNeighbours() {
        int seats = 0;
        for (Blueprint bp : placements()) {
            for (int y = 0; y < bp.sizeY(); y++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    for (int x = 0; x < bp.sizeX(); x++) {
                        String key = bp.keyAt(x, y, z);
                        if (key == null || !Keys.preferred(key).startsWith("handcrafted:") || !Keys.blockId(Keys.preferred(key)).endsWith("_couch")) {
                            continue;
                        }
                        seats++;
                        Dir front = Dir.byKey(prop(key, "facing"));
                        assertNotNull(front, key);
                        boolean left = joinedSeat(bp, x + front.left().dx, y, z + front.left().dz, front);
                        boolean right = joinedSeat(bp, x + front.right().dx, y, z + front.right().dz, front);
                        assertEquals(Decor.couchShape(left, right), prop(key, "shape"), bp.id() + " at " + x + "," + y + "," + z + ": " + key);
                    }
                }
            }
        }
        assertTrue(seats > 20, "the buildings are furnished with " + seats + " seats of sofas of the mod");
    }

    private static boolean joinedSeat(Blueprint bp, int x, int y, int z, Dir front) {
        return joinedSeat(bp, x, y, z, front, "_couch");
    }

    private static boolean joinedSeat(Blueprint bp, int x, int y, int z, Dir front, String kind) {
        String other = bp.keyAt(x, y, z);
        return other != null && Keys.preferred(other).startsWith("handcrafted:") && Keys.blockId(Keys.preferred(other)).endsWith(kind)
                && front.key().equals(prop(other, "facing"));
    }

    /** The benches of the streets join in a row as sofas do, whichever way the bench stands. */
    @Test
    void benchesOfTheStreetsJoinTheirNeighbours() {
        int seats = 0;
        Set<String> places = new HashSet<>();
        for (Blueprint bp : placements()) {
            for (int y = 0; y < bp.sizeY(); y++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    for (int x = 0; x < bp.sizeX(); x++) {
                        String key = bp.keyAt(x, y, z);
                        if (key == null || !Keys.preferred(key).startsWith("handcrafted:") || !Keys.blockId(Keys.preferred(key)).endsWith("_bench")) {
                            continue;
                        }
                        seats++;
                        places.add(bp.id());
                        Dir front = Dir.byKey(prop(key, "facing"));
                        assertNotNull(front, key);
                        boolean left = joinedSeat(bp, x + front.left().dx, y, z + front.left().dz, front, "_bench");
                        boolean right = joinedSeat(bp, x + front.right().dx, y, z + front.right().dz, front, "_bench");
                        assertEquals(Decor.couchShape(left, right), prop(key, "shape"), bp.id() + " at " + x + "," + y + "," + z + ": " + key);
                        assertTrue(HandcraftedBlocks.valuesOf("spruce_bench", "shape").contains(prop(key, "shape")), key);
                    }
                }
            }
        }
        assertTrue(seats >= 30, "the streets have " + seats + " seats of benches of the mod");
    }

    /** The sets of a table and chairs stand in the courtyards and behind the stalls of the market. */
    @Test
    void tablesWithChairsStandInTheCourtyardsAndAtTheMarket() {
        WorldPlan plan = DistrictPlan.get();
        int sets = 0;
        int atTheMarket = 0;
        for (var p : plan.fixedPlacements()) {
            if (p.blueprint().id().equals("cafeset")) {
                sets++;
                if (p.x() >= 60 && p.x() <= 110 && p.z() >= 12 && p.z() <= 18) {
                    atTheMarket++;
                }
            }
        }
        assertTrue(sets >= 8, "there are " + sets + " sets of a table and chairs");
        assertTrue(atTheMarket >= 2, atTheMarket + " of them behind the stalls of the market");
    }

    /** Every chair of a table of the streets looks at the table. */
    @Test
    void theChairsOfTheStreetsLookAtTheirTable() {
        Blueprint set = Props.cafeSet();
        int chairs = 0;
        for (int z = 0; z < set.sizeZ(); z++) {
            for (int x = 0; x < set.sizeX(); x++) {
                String key = set.keyAt(x, 0, z);
                if (key == null || !Keys.blockId(Keys.preferred(key)).endsWith("_chair")) {
                    continue;
                }
                chairs++;
                Dir front = Dir.byKey(prop(key, "facing"));
                String ahead = set.keyAt(x + front.dx, 0, z + front.dz);
                assertNotNull(ahead, key);
                assertTrue(Keys.blockId(Keys.preferred(ahead)).endsWith("_table"), "the chair at " + x + "," + z + " looks at " + ahead);
            }
        }
        assertEquals(4, chairs);
    }

    @Test
    void theRoomsAreFurnishedWithEveryKindOfPieceTheModOffers() {
        Set<String> kinds = new TreeSet<>();
        Set<String> standIns = new HashSet<>();
        for (Blueprint bp : placements()) {
            for (String key : bp.paletteKeys()) {
                if (key != null && Keys.isEither(key)) {
                    String id = Keys.blockId(Keys.preferred(key)).substring("handcrafted:".length());
                    for (String kind : new String[] {"_fancy_bed", "_chair", "_table", "_couch", "_shelf", "_cupboard", "_counter", "oven", "_cushion", "_crockery_combo", "_bench"}) {
                        if (id.endsWith(kind) || id.equals(kind)) {
                            kinds.add(kind);
                        }
                    }
                    standIns.add(Keys.blockId(key));
                    assertFalse(Keys.fallback(key).startsWith("handcrafted:"), "the stand-in of " + key + " is a block of the game");
                    assertTrue(Keys.preferred(key).startsWith("handcrafted:"), key);
                }
            }
        }
        assertEquals(new TreeSet<>(List.of("_fancy_bed", "_chair", "_table", "_couch", "_shelf", "_cupboard", "_counter", "oven", "_cushion", "_crockery_combo", "_bench")), kinds);
    }

    @Test
    void aBedOfTheModIsWholeOnlyWithBothHalves() {
        for (Blueprint bp : placements()) {
            for (int y = 0; y < bp.sizeY(); y++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    for (int x = 0; x < bp.sizeX(); x++) {
                        String key = bp.keyAt(x, y, z);
                        if (key == null || !Keys.blockId(Keys.preferred(key)).endsWith("_fancy_bed") || !"foot".equals(prop(key, "part"))) {
                            continue;
                        }
                        Dir toHead = Dir.byKey(prop(key, "facing"));
                        String head = bp.keyAt(x + toHead.dx, y, z + toHead.dz);
                        assertNotNull(head, bp.id() + " bed at " + x + "," + z);
                        assertEquals("head", prop(head, "part"), bp.id() + " bed at " + x + "," + z);
                        assertEquals(prop(key, "facing"), prop(head, "facing"));
                        assertEquals(Keys.blockId(Keys.preferred(key)), Keys.blockId(Keys.preferred(head)));
                    }
                }
            }
        }
    }
}
