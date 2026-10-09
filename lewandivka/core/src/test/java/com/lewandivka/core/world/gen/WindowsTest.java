package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Materials;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.Pal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The windows of the buildings: tinted glass in a closed frame, and a facade without patches that look like holes. */
class WindowsTest {

    private static boolean isBuilding(String id) {
        return id.startsWith("block_") || id.startsWith("house_") && !id.contains("_garden") && !id.contains("_fence") && !id.contains("_bush")
                || id.equals("school") || id.equals("kindergarten") || id.equals("old_shop");
    }

    private static List<StructurePlacement> buildings() {
        List<StructurePlacement> out = new ArrayList<>();
        for (StructurePlacement p : DistrictPlan.get().fixedPlacements()) {
            if (isBuilding(p.id()) && p.blueprint().sizeY() > 6) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean solid(Blueprint bp, int x, int y, int z) {
        String key = bp.keyAt(x, y, z);
        return key != null && !key.startsWith("minecraft:air") && !key.startsWith("minecraft:cave_air");
    }

    @Test
    void theBuildingsHaveWindowsOfTintedGlassAndNoClearPanes() {
        List<StructurePlacement> list = buildings();
        assertTrue(list.size() >= 30, "buildings found: " + list.size());
        for (StructurePlacement p : list) {
            List<String> keys = p.blueprint().paletteKeys();
            assertTrue(keys.contains(Pal.WINDOW), p.id() + " has no windows");
            assertFalse(keys.contains(Pal.PANE), p.id() + " has clear panes that look like holes");
        }
    }

    /** A window is closed: above and below every pane there is glass, frame or wall, and along the wall too. */
    @Test
    void everyWindowIsClosedByItsFrame() {
        int panes = 0;
        for (StructurePlacement p : buildings()) {
            Blueprint bp = p.blueprint();
            for (int y = 0; y < bp.sizeY(); y++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    for (int x = 0; x < bp.sizeX(); x++) {
                        if (!Pal.WINDOW.equals(bp.keyAt(x, y, z))) {
                            continue;
                        }
                        panes++;
                        String where = p.id() + " at " + x + "," + y + "," + z;
                        assertTrue(solid(bp, x, y + 1, z) && solid(bp, x, y - 1, z), where + ": the pane is not closed above and below");
                        boolean alongX = solid(bp, x - 1, y, z) && solid(bp, x + 1, y, z);
                        boolean alongZ = solid(bp, x, y, z - 1) && solid(bp, x, y, z + 1);
                        assertTrue(alongX || alongZ, where + ": the pane is not closed at its sides");
                    }
                }
            }
        }
        assertTrue(panes > 2000, "panes in the buildings: " + panes);
    }

    @Test
    void theFacadeHasNoBlackPatches() {
        for (Buildings.Theme theme : Buildings.Theme.values()) {
            for (long seed : new long[] {1, 7, 12345}) {
                for (int x = 0; x < 80; x++) {
                    for (int y = 1; y < 24; y++) {
                        for (int z : new int[] {0, 12}) {
                            String key = Buildings.panelWall(theme, seed, x, y, z);
                            assertNotEquals(Pal.CONCRETE_DARK, key, theme + " at " + x + "," + y);
                            assertNotEquals(Pal.CONCRETE_BLACK, key, theme + " at " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theGlassIsSolidForTheWalkChecks() {
        assertEquals(Materials.Kind.SOLID, Materials.classify(Pal.WINDOW));
    }
}
