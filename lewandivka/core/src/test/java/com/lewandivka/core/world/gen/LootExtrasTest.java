package com.lewandivka.core.world.gen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The things of the mods in the containers: the names are well formed, every kind of place has a pool and the children get no drink. */
class LootExtrasTest {

    @Test
    void everyKindOfPlaceHasAPoolOfThingsOfTheMods() {
        Set<String> mods = new HashSet<>();
        for (String kind : Loot.KINDS) {
            LootExtras.Pool pool = LootExtras.of(kind);
            assertNotNull(pool, kind);
            assertTrue(pool.chance() > 0 && pool.chance() <= 1, kind);
            assertTrue(pool.minRolls() >= 1 && pool.maxRolls() >= pool.minRolls() && pool.maxRolls() <= 3, kind);
            assertTrue(pool.items().size() >= 5, kind + " has " + pool.items().size());
            Set<String> names = new HashSet<>();
            for (LootExtras.Extra e : pool.items()) {
                assertTrue(e.item().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), e.item());
                assertTrue(LootExtras.MODS.contains(e.mod()), e.item());
                assertTrue(e.weight() >= 1 && e.weight() <= 10, e.item());
                assertTrue(e.min() >= 1 && e.max() >= e.min() && e.max() <= 8, e.item());
                assertTrue(names.add(e.item()), kind + " has " + e.item() + " twice");
                mods.add(e.mod());
            }
        }
        assertEquals(LootExtras.MODS, mods, "every mod has something in some container");
        assertNull(LootExtras.of("nowhere"));
    }

    @Test
    void theChildrenGetNoDrinksAndNoTobacco() {
        for (String kind : new String[] {"kindergarten", "school"}) {
            for (LootExtras.Extra e : LootExtras.of(kind).items()) {
                assertTrue(!LootExtras.isVice(e), kind + " has " + e.item());
            }
        }
        // and the others have some, so that the shops and the garages are what they are
        for (String kind : new String[] {"garage", "shop", "shed"}) {
            assertTrue(LootExtras.of(kind).items().stream().anyMatch(LootExtras::isVice), kind);
        }
    }
}
