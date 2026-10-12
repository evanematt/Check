package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The small things of the yards: a shed to walk into, a stall, the pitch, a fence turned to the side. */
class YardTest {

    @Test
    void theShedHasAFloorAFreeInsideAndADoor() {
        Blueprint bp = Yard.shed(0, 1);
        Blueprint.Marker entrance = bp.marker("entrance");
        Walk walk = new Walk(bp, true, 0);
        assertTrue(walk.canStand(entrance.x(), entrance.y(), entrance.z()), "the cell in front of the door of the shed is free");
        Walk.Reach reach = walk.from(entrance.x(), entrance.y(), entrance.z());
        assertTrue(reach.contains(2, 1, 2 + 1), "the cell inside the shed can be reached");
        assertTrue(reach.contains(3, 1, 2 + 2), "the back of the shed can be reached");
    }

    @Test
    void aRotatedBlueprintHasTheRotatedSize() {
        Blueprint fence = Props.chainFence(20, true);
        Blueprint turned = Yard.rotated(fence, 1);
        assertEquals(fence.sizeX(), turned.sizeZ());
        assertEquals(fence.sizeZ(), turned.sizeX());
        assertEquals(fence.nonEmptyCells(), turned.nonEmptyCells());
    }

    @Test
    void thePitchAndTheStallsHaveABody() {
        assertTrue(Yard.field(19, 42).marker("body") != null);
        Blueprint stall = Yard.stall(5, 0, 1);
        assertEquals(5, stall.sizeX());
        assertEquals(3, stall.sizeZ());
    }
}
