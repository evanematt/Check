package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The houses and the kindergarten: the grids are well formed and every room of both storeys can be reached from the front door. */
class HouseKitTest {

    private static final int FRONT = 3;

    private static List<String> unreachable(Blueprint bp, String[][] floors) {
        Blueprint.Marker entry = bp.marker("entrance");
        Walk walk = new Walk(bp, true, 0).halfSteps();
        List<String> problems = new ArrayList<>();
        if (!walk.canStand(entry.x(), entry.y(), entry.z())) {
            problems.add("the entry marker is not a standing position");
            return problems;
        }
        Walk.Reach reach = walk.from(entry.x(), entry.y(), entry.z());
        for (int storey = 0; storey < 2; storey++) {
            for (int[] cell : HouseKit.freeCells(floors[storey])) {
                int x = cell[0] + 1;
                int z = cell[1] + FRONT;
                int y = 4 * storey + 1;
                if (!reach.contains(x, y, z)) {
                    problems.add("storey " + storey + ": the cell " + cell[0] + "," + cell[1] + " (" + x + "," + y + "," + z + ") cannot be reached");
                }
            }
        }
        return problems;
    }

    @Test
    void cottagesOfEveryWidthCanBeWalkedThrough() {
        for (int w : new int[] {12, 14}) {
            String[][] floors = HouseKit.house(w);
            for (int storey = 0; storey < 2; storey++) {
                RoomKitTest.dump("house " + w + " storey " + storey, floors[storey]);
                RoomKit.Grid grid = RoomKit.parse(floors[storey]);
                assertEquals(w - 2, grid.cols());
                assertEquals(7, grid.rows());
                RoomKitTest.wellFormed("house " + w + " storey " + storey, grid);
            }
            for (Buildings.HouseStyle style : Buildings.HouseStyle.values()) {
                Blueprint bp = Buildings.plasterHouse(w, 9, style, 0, 40 + w);
                List<String> problems = unreachable(bp, floors);
                assertTrue(problems.isEmpty(), "house " + w + " " + style + ": " + problems.subList(0, Math.min(6, problems.size())));
            }
        }
    }

    @Test
    void theKindergartenCanBeWalkedThrough() {
        String[][] floors = HouseKit.kindergarten();
        for (int storey = 0; storey < 2; storey++) {
            RoomKitTest.dump("kindergarten storey " + storey, floors[storey]);
            RoomKit.Grid grid = RoomKit.parse(floors[storey]);
            assertEquals(22, grid.cols());
            assertEquals(8, grid.rows());
            RoomKitTest.wellFormed("kindergarten storey " + storey, grid);
        }
        Blueprint bp = Buildings.kindergarten(Buildings.HouseStyle.LILAC, 0, 24);
        List<String> problems = unreachable(bp, floors);
        assertTrue(problems.isEmpty(), "kindergarten: " + problems.size() + " problems, e.g. " + problems.subList(0, Math.min(6, problems.size())));
    }
}
