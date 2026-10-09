package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The garages, the boiler house and the transformer station: you can walk in (unless the gate is shut) and out. */
class IndustryTest {

    private static final int FRONT = 3;

    private static Walk.Reach from(Blueprint bp, Walk walk) {
        Blueprint.Marker entrance = bp.marker("entrance");
        assertTrue(walk.canStand(entrance.x(), entrance.y(), entrance.z()), bp.id() + ": the entrance marker is a standing position");
        return walk.from(entrance.x(), entrance.y(), entrance.z());
    }

    @Test
    void everyOpenGarageCanBeEntered() {
        for (long seed : new long[] {1, 2, 3}) {
            Blueprint bp = Industry.garageRow(8, 0, seed);
            Walk walk = new Walk(bp, true, 0);
            Walk.Reach reach = from(bp, walk);
            List<String> problems = new ArrayList<>();
            int open = 0;
            for (int i = 0; i < 8; i++) {
                int gate = Industry.gate(seed, i);
                int x = Industry.garageX(i);
                // inside the gate, in the row behind it
                boolean in = reach.contains(x, 1, 1 + FRONT);
                if (gate == 2) {
                    if (in) {
                        problems.add("garage " + i + " has a shut gate and can be entered");
                    }
                } else {
                    open++;
                    if (!in) {
                        problems.add("garage " + i + " cannot be entered (gate " + gate + ")");
                    }
                }
            }
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
            assertTrue(open >= 3, "most gates are open: " + open);
        }
    }

    @Test
    void theBoilerHouseCanBeWalkedThrough() {
        Blueprint bp = Industry.boilerHouse(0, 5);
        Walk.Reach reach = from(bp, new Walk(bp, true, 0));
        int[][] cells = {{2, 1, 1}, {2, 1, 5}, {8, 1, 2}, {1, 1, 9}, {13, 1, 5}, {13, 1, 3}, {7, 1, 8}};
        List<String> problems = new ArrayList<>();
        for (int[] c : cells) {
            if (!reach.contains(c[0] + 0, c[1], c[2] + FRONT)) {
                problems.add("the cell " + c[0] + "," + c[1] + "," + c[2] + " of the boiler house cannot be reached");
            }
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void theSubstationCanBeEntered() {
        Blueprint bp = Industry.substation(0, 3);
        Walk.Reach reach = from(bp, new Walk(bp, true, 0));
        for (int[] c : new int[][] {{4, 1, 1}, {1, 1, 2}, {7, 1, 5}, {4, 1, 5}}) {
            assertTrue(reach.contains(c[0], c[1], c[2] + FRONT), "the cell " + c[0] + "," + c[1] + "," + c[2] + " of the substation cannot be reached");
        }
    }
}
