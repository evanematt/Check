package com.lewandivka.core;

import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.core.world.gen.WildTerrain;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The open country around the city: it has to be walkable next to the city, varied further out and the same every time. */
class WildTerrainTest {

    private static final DistrictPlan PLAN = DistrictPlan.get();

    private static int height(int x, int z) {
        TerrainColumn col = new TerrainColumn();
        PLAN.column(x, z, col);
        return col.height;
    }

    @Test
    void theLandIsAPureFunctionOfThePosition() {
        TerrainColumn a = new TerrainColumn();
        TerrainColumn b = new TerrainColumn();
        Random rnd = new Random(1);
        for (int i = 0; i < 2000; i++) {
            int x = rnd.nextInt(6000) - 3000;
            int z = rnd.nextInt(6000) - 3000;
            PLAN.column(x, z, a);
            PLAN.column(x, z, b);
            assertEquals(a.height, b.height);
            assertEquals(a.top, b.top);
            assertEquals(a.fluidY, b.fluidY);
            if (Math.max(Math.abs(x), Math.abs(z)) > DistrictPlan.CITY_EDGE) {
                assertEquals(PLAN.biomeAt(x, 64, z), PLAN.biomeAt(x, 5, z), "the biome of the open country does not depend on the height");
            }
        }
    }

    @Test
    void theCityMeetsTheCountrysideWithoutAStep() {
        // along the whole ring at the square edge of the city the ground goes on without a step (the hills of the city grow into the land)
        for (int i = -DistrictPlan.CITY_EDGE; i <= DistrictPlan.CITY_EDGE; i += 3) {
            int e = DistrictPlan.CITY_EDGE;
            for (int[] p : new int[][] {{i, e}, {i, -e}, {e, i}, {-e, i}}) {
                int outward = Math.abs(p[0]) == e ? (int) Math.signum(p[0]) : 0;
                int outwardZ = Math.abs(p[1]) == e ? (int) Math.signum(p[1]) : 0;
                for (int k = 0; k < 6; k++) {
                    int a = height(p[0] + outward * k, p[1] + outwardZ * k);
                    int b = height(p[0] + outward * (k + 1), p[1] + outwardZ * (k + 1));
                    assertEquals(a, b, 1, "step at the edge of the city " + p[0] + "," + p[1] + " k=" + k);
                }
            }
        }
    }

    @Test
    void nextToTheCityThereIsNoCliffNoWaterAndNoCave() {
        TerrainColumn col = new TerrainColumn();
        int reach = DistrictPlan.CITY_EDGE + 90;
        for (int x = -reach; x <= reach; x += 2) {
            for (int z = -reach; z <= reach; z += 2) {
                PLAN.column(x, z, col);
                int h = col.height;
                assertTrue(Math.abs(h - height(x + 1, z)) <= 1 && Math.abs(h - height(x, z + 1)) <= 1, "cliff at " + x + "," + z);
                if (Math.max(Math.abs(x), Math.abs(z)) <= DistrictPlan.CITY_EDGE + 60) {
                    assertFalse(col.fluidY > col.height, "water at " + x + "," + z);
                }
                if (Math.max(Math.abs(x), Math.abs(z)) <= DistrictPlan.CITY_EDGE + 24) {
                    // there are caves under the city, but a roof of rock stays over them: nothing opens to the surface
                    for (int y = h - 8; y <= h; y++) {
                        assertFalse(PLAN.carved(x, y, z, h), "cave under the street at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void theWildernessIsVariedAndSurvivable() {
        Random rnd = new Random(7);
        TerrainColumn col = new TerrainColumn();
        Set<String> biomes = new HashSet<>();
        int n = 40000;
        int wet = 0;
        int steep = 0;
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int x = rnd.nextInt(8000) - 4000;
            int z = rnd.nextInt(8000) - 4000;
            PLAN.column(x, z, col);
            minH = Math.min(minH, col.height);
            maxH = Math.max(maxH, col.height);
            if (col.fluidY > col.height) {
                wet++;
            }
            if (Math.abs(col.height - height(x + 1, z)) > 3) {
                steep++;
            }
            biomes.add(PLAN.biomeAt(x, 64, z));
            assertTrue(col.height >= 20 && col.height < 200, "height " + col.height);
        }
        double water = 100.0 * wet / n;
        assertTrue(water > 12 && water < 45, "water covers " + water + "% of the land");
        assertTrue(steep < n / 100, steep + " of " + n + " columns are walls of more than three blocks");
        assertTrue(biomes.size() >= 18, "only " + biomes.size() + " biomes: " + biomes);
        for (String b : biomes) {
            assertTrue(PLAN.biomes().contains(b), b + " is not declared by the plan");
        }
        assertTrue(minH < 50 && maxH > 120, "the relief is flat: " + minH + ".." + maxH);
        assertEquals("lewandivka:district", PLAN.biomeAt(0, 64, 0));
        assertEquals(PLAN.biomes().get(0), "lewandivka:district", "the city biome is the fallback of the biome source");
    }

    @Test
    void cavesOpenTheRockWithoutTearingTheSurface() {
        Random rnd = new Random(3);
        TerrainColumn col = new TerrainColumn();
        int floor = PLAN.minY();
        long cells = 0;
        long carved = 0;
        long nearSurface = 0;
        for (int i = 0; i < 3000; i++) {
            int x = 600 + rnd.nextInt(2400);
            int z = rnd.nextInt(2400) - 1200;
            PLAN.column(x, z, col);
            if (col.height < WildTerrain.SEA + 2) {
                continue;
            }
            assertFalse(PLAN.carved(x, floor, z, col.height), "bedrock is never carved");
            assertFalse(PLAN.carved(x, floor + 2, z, col.height), "the floor under the lava is never carved");
            assertFalse(PLAN.carved(x, col.height + 1, z, col.height), "nothing above the surface is carved");
            for (int y = floor + 3; y <= col.height; y++) {
                cells++;
                if (PLAN.carved(x, y, z, col.height)) {
                    carved++;
                    if (y > col.height - 6) {
                        nearSurface++;
                    }
                }
            }
        }
        double share = 100.0 * carved / cells;
        assertTrue(share > 2.0 && share < 14.0, "caves take " + share + "% of the rock");
        assertTrue(nearSurface * 50 < carved, "caves reach the daylight everywhere: " + nearSurface + " of " + carved);
    }

    @Test
    void underTheCityThereIsTheOrdinaryRockWithCavesOfItsOwn() {
        TerrainColumn col = new TerrainColumn();
        long cells = 0;
        long carved = 0;
        for (int x = -140; x <= 140; x += 7) {
            for (int z = -140; z <= 140; z += 7) {
                PLAN.column(x, z, col);
                for (int y = PLAN.minY() + 3; y <= col.height - 9; y++) {
                    cells++;
                    carved += PLAN.carved(x, y, z, col.height) ? 1 : 0;
                }
                // the rock under the streets belongs to the ordinary underground, the streets themselves to the district
                assertEquals(DistrictPlan.BIOME, PLAN.biomeAt(x, col.height, z), "the surface of the city is the district's");
            }
        }
        double share = 100.0 * carved / cells;
        assertTrue(share > 1.5 && share < 14.0, "caves take " + share + "% of the rock under the city");
        assertEquals(DistrictPlan.UNDERGROUND_BIOME, PLAN.biomeAt(60, 0, 60), "deep under the street");
        assertEquals(-64, PLAN.minY());
        assertEquals(384, PLAN.height());
    }

    @Test
    void everyWildBiomeHasASurfaceThatTheGameKnows() {
        // the surface blocks of the sampler are plain vanilla ids
        WildTerrain.Sample s = new WildTerrain.Sample();
        Random rnd = new Random(11);
        Set<String> tops = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            WildTerrain.sample(rnd.nextInt(8000) - 4000, rnd.nextInt(8000) - 4000, DistrictPlan.EDGE, s);
            tops.add(s.top);
            assertTrue(s.top.startsWith("minecraft:") && s.sub.startsWith("minecraft:") && s.base.startsWith("minecraft:"));
        }
        assertTrue(tops.containsAll(List.of("minecraft:grass_block", "minecraft:sand", "minecraft:gravel", "minecraft:snow_block", "minecraft:stone")), tops.toString());
    }
}
