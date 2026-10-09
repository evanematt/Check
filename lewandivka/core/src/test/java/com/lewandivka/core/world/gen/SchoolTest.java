package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The school: the grids are well formed and every room of both storeys can be reached from the street through the hall. */
class SchoolTest {

    @Test
    void theGridsAreWellFormed() {
        String[][] floors = School.floors();
        for (int storey = 0; storey < 2; storey++) {
            RoomKitTest.dump("school storey " + storey, floors[storey]);
            RoomKit.Grid grid = RoomKit.parse(floors[storey]);
            assertEquals(38, grid.cols());
            assertEquals(14, grid.rows());
            RoomKitTest.wellFormed("school storey " + storey, grid);
        }
    }

    @Test
    void everyRoomOfBothStoreysCanBeReached() {
        Blueprint bp = School.build(0, 7);
        Blueprint.Marker entrance = bp.marker("entrance");
        Walk walk = new Walk(bp, true, 0).halfSteps();
        assertTrue(walk.canStand(entrance.x(), entrance.y(), entrance.z()), "the hall is free behind the door");
        Walk.Reach reach = walk.from(entrance.x(), entrance.y(), entrance.z());
        String[][] floors = School.floors();
        List<String> problems = new ArrayList<>();
        for (int storey = 0; storey < 2; storey++) {
            for (int[] cell : HouseKit.freeCells(floors[storey])) {
                int x = cell[0];
                int z = cell[1] + School.FRONT;
                int y = 4 * storey + 1;
                if (!reach.contains(x, y, z)) {
                    problems.add("storey " + storey + ": the cell " + cell[0] + "," + cell[1] + " cannot be reached");
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " cells of the school cannot be reached, e.g. " + problems.subList(0, Math.min(6, problems.size())));
    }
}
