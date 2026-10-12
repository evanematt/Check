package com.lewandivka.core;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.structure.StructurePlacement.MarkerPos;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks of the two world plans that need no Minecraft: determinism, placement sanity, markers. */
class WorldPlanTest {

    private static final List<WorldPlan> PLANS = List.of(DistrictPlan.get(), ChromaPlan.get());

    private static List<MarkerPos> allMarkers(WorldPlan plan) {
        List<MarkerPos> out = new ArrayList<>(plan.planMarkers());
        for (StructurePlacement sp : plan.fixedPlacements()) {
            out.addAll(sp.markers());
        }
        return out;
    }

    private static int surface(WorldPlan plan, int x, int z) {
        TerrainColumn c = new TerrainColumn();
        plan.column(x, z, c);
        return c.height;
    }

    @Test
    void terrainIsAPureFunctionOfPosition() {
        for (WorldPlan plan : PLANS) {
            TerrainColumn a = new TerrainColumn();
            TerrainColumn b = new TerrainColumn();
            for (int x = -240; x <= 240; x += 37) {
                for (int z = -260; z <= 230; z += 41) {
                    plan.column(x, z, a);
                    plan.column(x, z, b);
                    assertEquals(a.height, b.height, plan.dimensionId() + " " + x + "," + z);
                    assertEquals(a.top, b.top);
                    assertTrue(a.height >= plan.minY() && a.height < plan.minY() + plan.height());
                }
            }
        }
    }

    @Test
    void scatterIsDeterministicAndStaysInsideTheRequestedArea() {
        for (WorldPlan plan : PLANS) {
            List<StructurePlacement> first = plan.scatterIn(-64, -64, 63, 63);
            List<StructurePlacement> second = plan.scatterIn(-64, -64, 63, 63);
            assertEquals(first.size(), second.size());
            for (int i = 0; i < first.size(); i++) {
                assertEquals(first.get(i).x(), second.get(i).x());
                assertEquals(first.get(i).y(), second.get(i).y());
                assertEquals(first.get(i).z(), second.get(i).z());
                assertTrue(first.get(i).intersectsXZ(-64, -64, 63, 63));
            }
        }
    }

    @Test
    void markerIdsAreUniquePerDimension() {
        for (WorldPlan plan : PLANS) {
            Set<String> seen = new HashSet<>();
            for (MarkerPos m : allMarkers(plan)) {
                assertTrue(seen.add(m.id()), plan.dimensionId() + ": duplicate marker " + m.id());
            }
        }
    }

    @Test
    void fixedStructuresDoNotCollide() {
        for (WorldPlan plan : PLANS) {
            List<StructurePlacement> list = plan.fixedPlacements();
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    StructurePlacement a = list.get(i);
                    StructurePlacement b = list.get(j);
                    boolean overlap = a.x() <= b.maxX() && a.maxX() >= b.x() && a.y() <= b.maxY() && a.maxY() >= b.y()
                            && a.z() <= b.maxZ() && a.maxZ() >= b.z();
                    if (overlap && !(a.id().equals("tower_approach") && b.id().equals("tower"))) {
                        assertTrue(!solidOverlap(a, b), plan.dimensionId() + ": " + a.id() + " and " + b.id() + " overlap");
                    }
                }
            }
        }
    }

    /** True if two placements put different non-air blocks into the same cell. */
    private static boolean solidOverlap(StructurePlacement a, StructurePlacement b) {
        int x1 = Math.max(a.x(), b.x());
        int x2 = Math.min(a.maxX(), b.maxX());
        int y1 = Math.max(a.y(), b.y());
        int y2 = Math.min(a.maxY(), b.maxY());
        int z1 = Math.max(a.z(), b.z());
        int z2 = Math.min(a.maxZ(), b.maxZ());
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    String ka = a.blueprint().keyAt(x - a.x(), y - a.y(), z - a.z());
                    String kb = b.blueprint().keyAt(x - b.x(), y - b.y(), z - b.z());
                    if (ka != null && kb != null && !Keys.AIR.equals(ka) && !Keys.AIR.equals(kb)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void chromaStructuresStandOnFlatGround() {
        ChromaPlan plan = ChromaPlan.get();
        int checked = 0;
        for (StructurePlacement sp : plan.fixedPlacements()) {
            if (sp.id().startsWith("isle")) {
                continue;
            }
            for (int x = sp.x(); x <= sp.maxX(); x += 5) {
                for (int z = sp.z(); z <= sp.maxZ(); z += 5) {
                    int ground = ChromaPlan.flatGround(x, z);
                    if (ground >= 0) {
                        assertEquals(ground, surface(plan, x, z), sp.id() + " at " + x + "," + z);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 500, "flat areas were not exercised: " + checked);
    }

    @Test
    void spawnPointsAreUsable() {
        for (WorldPlan plan : PLANS) {
            int[] s = plan.spawn();
            assertTrue(s[1] > surface(plan, s[0], s[2]) - 1, plan.dimensionId() + " spawn is buried");
            assertTrue(s[1] - surface(plan, s[0], s[2]) <= 6, plan.dimensionId() + " spawn floats");
        }
    }

    @Test
    void everyCompassTargetResolvesToAMarker() {
        Set<String> ids = new HashSet<>();
        for (MarkerPos m : allMarkers(ChromaPlan.get())) {
            ids.add(m.id());
        }
        for (QuestStep step : QuestStep.values()) {
            if (step.compassMarker != null) {
                assertTrue(ids.contains(step.compassMarker), step + " points at missing marker " + step.compassMarker);
            }
        }
    }

    @Test
    void skyRideIsLongEnoughAndClearOfStructures() {
        double[][] route = ChromaPlan.skyRide();
        assertTrue(route.length >= 8);
        double length = 0;
        for (int i = 1; i < route.length; i++) {
            length += Math.sqrt(sq(route[i][0] - route[i - 1][0]) + sq(route[i][1] - route[i - 1][1]) + sq(route[i][2] - route[i - 1][2]));
        }
        assertTrue(length > 200 && length < 400, "route length " + length);
        ChromaPlan plan = ChromaPlan.get();
        // the cabin occupies a 3 x 3 footprint and two cells of height above the rails
        for (int i = 1; i < route.length; i++) {
            double[] a = route[i - 1];
            double[] b = route[i];
            double len = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            int steps = (int) Math.ceil(len * 2);
            for (int k = 0; k <= steps; k++) {
                double t = k / (double) steps;
                int x = (int) Math.floor(a[0] + (b[0] - a[0]) * t);
                int y = (int) Math.floor(a[1] + (b[1] - a[1]) * t);
                int z = (int) Math.floor(a[2] + (b[2] - a[2]) * t);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 1; dy <= 3; dy++) {
                            assertTrue(!solidAt(plan, x + dx, y + dy, z + dz), "route is blocked near " + x + "," + y + "," + z + " (cell " + (x + dx) + "," + (y + dy) + "," + (z + dz) + ")");
                        }
                    }
                }
            }
        }
        // it must start at the lower stop and end on the depot's arrival rails
        MarkerPos stop = find(plan, "sky_ascent:tram_stop_lower");
        MarkerPos arrive = find(plan, "sky_depot:tram_arrive");
        assertTrue(Math.abs(route[0][1] - (stop.y() + 0.1)) <= 1.5, "route starts at the wrong height");
        assertTrue(Math.abs(route[route.length - 1][0] - arrive.x()) <= 2 && Math.abs(route[route.length - 1][2] - arrive.z()) <= 3);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static MarkerPos find(WorldPlan plan, String id) {
        for (MarkerPos m : allMarkers(plan)) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        assertNotNull(null, "missing marker " + id);
        return null;
    }

    private static boolean solidAt(WorldPlan plan, int x, int y, int z) {
        if (y <= surface(plan, x, z)) {
            return true;
        }
        for (StructurePlacement sp : plan.fixedPlacements()) {
            if (sp.contains(x, y, z)) {
                String key = sp.blueprint().keyAt(x - sp.x(), y - sp.y(), z - sp.z());
                if (key != null && !Keys.AIR.equals(key) && com.lewandivka.core.structure.Materials.classify(key) != com.lewandivka.core.structure.Materials.Kind.PASS) {
                    return true;
                }
            }
        }
        return false;
    }
}
