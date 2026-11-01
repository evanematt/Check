package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Walk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A panel block is a building to walk through: from the street to every flat of every floor, and up to the roof. */
class PanelBlockTest {

    private static final int FLOORS = 5;

    private static List<String> unreachable(Blueprint bp, int sections) {
        Blueprint.Marker lobby = bp.marker("entrance");
        Walk walk = new Walk(bp, true, 0).halfSteps();
        List<String> problems = new ArrayList<>();
        if (!walk.canStand(lobby.x(), lobby.y(), lobby.z())) {
            problems.add("the lobby marker is not a standing position");
            return problems;
        }
        Walk.Reach reach = walk.from(lobby.x(), lobby.y(), lobby.z());
        int dz = PanelBlock.FRONT;
        for (int e = 0; e < sections; e++) {
            int u0 = PanelBlock.PITCH * e;
            for (int k = 0; k < FLOORS; k++) {
                int f = PanelBlock.STOREY * k;
                // the landing of the floor, the cells inside the doors of the four flats
                check(reach, problems, "landing of section " + e + " floor " + k, u0 + 8, f + 1, PanelBlock.LANDING - 1 + dz);
                for (int side = 0; side < 4; side++) {
                    Plot plot = PanelBlock.flat(u0, (side & 1) == 1, (side & 2) == 2);
                    int x = plot.x(RoomKit.ENTRY_A, RoomKit.ENTRY_B);
                    int z = plot.z(RoomKit.ENTRY_A, RoomKit.ENTRY_B) + dz;
                    check(reach, problems, "flat " + side + " of section " + e + " floor " + k, x, f + 1, z);
                    // and the far side of the flat, the window
                    check(reach, problems, "window of flat " + side + " of section " + e + " floor " + k, plot.x(2, 2), f + 1, plot.z(2, 2) + dz);
                }
            }
            check(reach, problems, "the roof of section " + e, u0 + 9, PanelBlock.roofLayer(FLOORS) + 1, 6 + dz);
        }
        return problems;
    }

    private static void check(Walk.Reach reach, List<String> problems, String what, int x, int y, int z) {
        if (!reach.contains(x, y, z)) {
            problems.add(what + " cannot be reached at " + x + "," + y + "," + z);
        }
    }

    @Test
    void everyFlatAndTheRoofCanBeReachedOnFoot() {
        for (int sections : new int[] {2, 3}) {
            Blueprint bp = Buildings.panelBlock(sections, FLOORS, Buildings.Theme.PANEL_GREY, 0, 11);
            List<String> problems = unreachable(bp, sections);
            assertTrue(problems.isEmpty(), sections + " sections: " + problems.size() + " problems, e.g. " + problems.subList(0, Math.min(8, problems.size())));
        }
    }
}
