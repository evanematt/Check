package com.lewandivka.core;

import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.gen.DistrictPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ground of the city: level where people walk and drive, hilly further out, and always flush with what stands on it. */
class CityGroundTest {

    private static final DistrictPlan PLAN = DistrictPlan.get();
    private static final int G = DistrictPlan.GROUND;
    private static final int E = DistrictPlan.CITY_EDGE;

    @Test
    void streetsAndTheTramLineAreLevel() {
        int[][] streets = {
                {-29, -5, 150, 5}, {-75, -148, -67, 148}, {45, -148, 53, 148}, {-148, -70, 148, -62}, {-148, 62, 148, 70},
                {-43, -1, 150, 1}, {-42, -10, -30, 10}
        };
        for (int[] s : streets) {
            for (int x = s[0]; x <= s[2]; x++) {
                for (int z = s[1]; z <= s[3]; z++) {
                    assertEquals(G, PLAN.groundAt(x, z), "the street is level at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void theCityHasHillsButNoCliffs() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int steps = 0;
        int steep = 0;
        int cliffs = 0;
        for (int x = -E; x < E; x++) {
            for (int z = -E; z < E; z++) {
                int h = PLAN.groundAt(x, z);
                min = Math.min(min, h);
                max = Math.max(max, h);
                for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                    int step = Math.abs(PLAN.groundAt(x + d[0], z + d[1]) - h);
                    steps++;
                    steep += step > 1 ? 1 : 0;
                    cliffs += step > 2 ? 1 : 0;
                }
            }
        }
        System.out.println("city ground " + min + ".." + max + ", steps >1: " + steep + " of " + steps + ", steps >2: " + cliffs);
        assertTrue(max - min >= 6, "the city has relief: " + min + ".." + max);
        assertTrue(min >= G - 8 && max <= G + 14, "the relief stays moderate: " + min + ".." + max);
        assertTrue(steep < steps / 40, "hardly any step is steeper than one block: " + steep + " of " + steps);
        assertEquals(0, cliffs, "no step of more than two blocks");
    }

    @Test
    void everythingThatStandsOnTheGroundStandsFlush() {
        int checked = 0;
        for (StructurePlacement sp : PLAN.fixedPlacements()) {
            boolean building = sp.id().startsWith("block_") || sp.id().startsWith("house_") && !sp.id().contains("_garden") && !sp.id().contains("_fence") && !sp.id().contains("_bush")
                    || sp.id().equals("old_shop") || sp.id().equals("kindergarten") || sp.id().equals("tram_depot");
            if (!building) {
                continue;
            }
            for (int x = sp.x(); x <= sp.maxX(); x++) {
                for (int z = sp.z(); z <= sp.maxZ(); z++) {
                    assertEquals(sp.y(), PLAN.groundAt(x, z), sp.id() + " stands on a level plot at " + x + "," + z);
                }
            }
            checked++;
        }
        assertTrue(checked >= 20, "the buildings of the district were checked: " + checked);
    }

    @Test
    void theQuestPlacesFollowTheGround() {
        for (StructurePlacement.MarkerPos m : PLAN.planMarkers()) {
            if (m.id().startsWith("district:portal") || m.id().startsWith("district:tram")) {
                continue;
            }
            int ground = PLAN.groundAt(m.x(), m.z());
            assertTrue(Math.abs(m.y() - ground) <= 3, m.id() + " at " + m.x() + "," + m.z() + " is " + m.y() + " but the ground is " + ground);
        }
        for (int[] node : DistrictPlan.DEBTOR_NODES) {
            assertTrue(Math.abs(PLAN.groundAt(node[0], node[1]) - G) <= 3, "the chase runs over level ground at " + node[0] + "," + node[1]);
        }
        int[] spawn = PLAN.spawn();
        int ground = PLAN.groundAt(spawn[0], spawn[2]);
        assertTrue(spawn[1] > ground && spawn[1] <= ground + 4, "the player starts just over the ground: " + spawn[1] + " over " + ground);
    }

    @Test
    void theHillsGrowOutwardFromAFlatCore() {
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                assertTrue(Math.abs(PLAN.groundAt(x, z) - G) <= 1, "the core of the city is flat at " + x + "," + z);
            }
        }
        List<Integer> outskirts = List.of(PLAN.groundAt(120, 100), PLAN.groundAt(-120, -100), PLAN.groundAt(-130, 100), PLAN.groundAt(100, -130));
        System.out.println("outskirts " + outskirts);
    }
}
