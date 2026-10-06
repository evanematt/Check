package com.lewandivka.core;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.Materials;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTest {

    private static final String STONE = "minecraft:stone_bricks";

    @Test
    void untouchedCellsAndPaletteWork() {
        BlueprintBuilder b = new BlueprintBuilder("t", 4, 4, 4);
        b.set(1, 1, 1, STONE);
        Blueprint bp = b.build();
        assertEquals(STONE, bp.keyAt(1, 1, 1));
        assertNull(bp.keyAt(0, 0, 0), "untouched cells stay null");
        assertEquals(1, bp.nonEmptyCells());
        assertEquals(1, bp.paletteKeys().size());
    }

    @Test
    void writesOutsideAreRejectedUnlessClipping() {
        BlueprintBuilder b = new BlueprintBuilder("t", 2, 2, 2);
        assertThrows(IllegalArgumentException.class, () -> b.set(2, 0, 0, STONE));
        b.clip(true).set(5, 5, 5, STONE);
        assertEquals(0, b.build().nonEmptyCells());
    }

    @Test
    void fillShellRoomAndWalls() {
        BlueprintBuilder b = new BlueprintBuilder("room", 5, 5, 5);
        b.room(0, 0, 0, 4, 4, 4, STONE);
        Blueprint bp = b.build();
        assertEquals(STONE, bp.keyAt(0, 2, 2));
        assertEquals(Keys.AIR, bp.keyAt(2, 2, 2));
        assertEquals(125 - 27, bp.count(STONE));
    }

    @Test
    void rotationRotatesFacingAndAxisAndSides() {
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom]",
                BlueprintBuilder.rotateKey("minecraft:oak_stairs[facing=north,half=bottom]", 1));
        assertEquals("minecraft:oak_stairs[facing=south,half=top]",
                BlueprintBuilder.rotateKey("minecraft:oak_stairs[facing=north,half=top]", 2));
        assertEquals("minecraft:oak_log[axis=z]", BlueprintBuilder.rotateKey("minecraft:oak_log[axis=x]", 1));
        assertEquals("minecraft:oak_log[axis=y]", BlueprintBuilder.rotateKey("minecraft:oak_log[axis=y]", 3));
        assertEquals("minecraft:oak_fence[east=true]", BlueprintBuilder.rotateKey("minecraft:oak_fence[north=true]", 1));
        assertEquals("minecraft:oak_sign[rotation=4]", BlueprintBuilder.rotateKey("minecraft:oak_sign[rotation=0]", 1));
        assertEquals("minecraft:stone", BlueprintBuilder.rotateKey("minecraft:stone", 3));
        assertEquals("minecraft:oak_door[facing=west]", BlueprintBuilder.rotateKey("minecraft:oak_door[facing=west]", 4));
        assertEquals(Dir.WEST, Dir.NORTH.turn(-1));
        assertEquals(Dir.SOUTH, Dir.NORTH.opposite());
    }

    @Test
    void framePlacesBlocksRotatedAroundTheCorner() {
        // A 3x1x2 strip with a stair at its local (2,0,0) facing north.
        BlueprintBuilder b = new BlueprintBuilder("rot", 2, 1, 3);
        b.turn(1); // after one clockwise turn the local +x axis points to world +z
        b.set(0, 0, 0, Keys.stairs("minecraft:stone_stairs", Dir.NORTH, false));
        b.set(2, 0, 0, STONE);
        Blueprint bp = b.build();
        // local (0,0) -> world (0,0): the stair now faces east
        assertEquals("minecraft:stone_stairs[facing=east,half=bottom]", bp.keyAt(0, 0, 0));
        // local (2,0) -> world (0,2)
        assertEquals(STONE, bp.keyAt(0, 0, 2));
    }

    @Test
    void pushAndPopRestoreTheFrame() {
        BlueprintBuilder b = new BlueprintBuilder("f", 10, 3, 10);
        b.push().at(5, 0, 5).turn(2).set(0, 0, 0, STONE).pop();
        b.set(0, 0, 0, "minecraft:glass");
        Blueprint bp = b.build();
        assertEquals(STONE, bp.keyAt(5, 0, 5));
        assertEquals("minecraft:glass", bp.keyAt(0, 0, 0));
    }

    @Test
    void markersAndRegionsFollowTheFrame() {
        BlueprintBuilder b = new BlueprintBuilder("m", 8, 4, 8);
        b.push().at(2, 0, 0).marker("lever_1", 1, 1, 1, "facing=north").pop();
        b.region("door_a", 1, 0, 1, 2, 2, 1);
        Blueprint bp = b.build();
        Blueprint.Marker m = bp.marker("lever_1");
        assertNotNull(m);
        assertEquals(3, m.x());
        assertEquals("facing=north", m.data());
        Blueprint.Marker r = bp.marker("door_a");
        assertTrue(r.isRegion());
        assertEquals(2, r.sx());
        assertEquals(3, r.sy());
        assertThrows(IllegalArgumentException.class, () -> new BlueprintBuilder("x", 2, 2, 2).marker("bad", 5, 0, 0));
    }

    @Test
    void stampCopiesWithRotation() {
        BlueprintBuilder small = new BlueprintBuilder("small", 2, 1, 1);
        small.set(0, 0, 0, STONE).set(1, 0, 0, "minecraft:glass").marker("p", 1, 0, 0);
        Blueprint s = small.build();
        BlueprintBuilder big = new BlueprintBuilder("big", 4, 1, 4);
        big.stamp(s, 1, 0, 1, 1);
        Blueprint bp = big.build();
        // rotated by one quarter turn the 2x1 strip becomes 1x2: stone at (1,1), glass at (1,2)
        assertEquals(STONE, bp.keyAt(1, 0, 1));
        assertEquals("minecraft:glass", bp.keyAt(1, 0, 2));
        assertNotNull(bp.marker("small.p"));
        assertEquals(2, bp.marker("small.p").z());
    }

    @Test
    void materialsClassification() {
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:air"));
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:oak_wall_sign[facing=north]"));
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:short_grass"));
        assertEquals(Materials.Kind.SOLID, Materials.classify("minecraft:grass_block"));
        assertEquals(Materials.Kind.SOLID, Materials.classify("minecraft:light_gray_concrete"));
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:light[level=7]"));
        assertEquals(Materials.Kind.SOLID, Materials.classify("minecraft:oak_leaves"));
        assertEquals(Materials.Kind.SOLID, Materials.classify("minecraft:glass"));
        assertEquals(Materials.Kind.LIQUID, Materials.classify("minecraft:water"));
        assertEquals(Materials.Kind.HAZARD, Materials.classify("minecraft:lava"));
        assertEquals(Materials.Kind.CLIMB, Materials.classify("minecraft:ladder[facing=north]"));
        assertEquals(Materials.Kind.GATE, Materials.classify("lewandivka:guard_door"));
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:dark_oak_door[facing=north]"));
        assertEquals(Materials.Kind.PASS, Materials.classify("minecraft:rail[shape=east_west]"));
        assertEquals(Materials.Kind.SOLID, Materials.classify("lewandivka:garage_lift"));
        assertEquals(Materials.Kind.PASS, Materials.classify("lewandivka:crystal_cluster"));
    }

    @Test
    void walkFindsRoutesStepsAndBlockedGates() {
        // 12x6x5 corridor: floor y=0, walls, a one-block step in the middle, and a gate at the end.
        BlueprintBuilder b = new BlueprintBuilder("walk", 12, 6, 5);
        b.fill(0, 0, 0, 11, 0, 4, STONE);
        b.fill(0, 1, 0, 11, 4, 0, STONE).fill(0, 1, 4, 11, 4, 4, STONE).fill(0, 5, 0, 11, 5, 4, STONE);
        b.fill(0, 1, 1, 11, 4, 3, Keys.AIR);
        b.fill(5, 1, 1, 5, 1, 3, STONE);   // one block high step
        b.fill(8, 1, 1, 8, 3, 3, "lewandivka:guard_door"); // gate
        b.fill(11, 1, 1, 11, 3, 3, STONE);  // end wall
        Blueprint bp = b.build();

        Walk closed = new Walk(bp, false, 0);
        Walk.Reach r1 = closed.from(1, 1, 2);
        assertTrue(r1.contains(7, 1, 2), "step up over the block and drop back down");
        assertTrue(r1.contains(5, 2, 2), "standing on top of the step block");
        assertFalse(r1.containsNear(9, 1, 2, 0, 1), "gate blocks the way");

        Walk open = new Walk(bp, true, 0);
        Walk.Reach r2 = open.from(1, 1, 2);
        assertTrue(r2.containsNear(9, 1, 2, 0, 1), "with gates open the far side is reachable");
    }

    @Test
    void walkHandlesLaddersAndDrops() {
        BlueprintBuilder b = new BlueprintBuilder("ladder", 5, 10, 5);
        b.fill(0, 0, 0, 4, 0, 4, STONE);
        b.fill(0, 6, 0, 2, 6, 4, STONE);                // upper platform
        b.fill(2, 1, 2, 2, 6, 2, STONE);                // pillar the ladder hangs on
        for (int y = 1; y <= 6; y++) {
            b.set(3, y, 2, Keys.ladder(Dir.WEST));      // ladder attached to the pillar's east side
        }
        b.set(2, 6, 2, STONE);
        Blueprint bp = b.build();
        Walk w = new Walk(bp, false, 0);
        Walk.Reach r = w.from(3, 1, 3);
        assertTrue(r.contains(3, 6, 2), "can climb the ladder");
    }
}
