package com.lewandivka.core.world.gen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.StructurePlacement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chests and barrels of the buildings: they are where the plan says, and their tables hold things that exist. */
class LootTest {

    private static final Path CHESTS = Path.of("..", "src", "main", "resources", "data", "lewandivka", "loot_tables", "chests");
    private static final Set<String> SINGLES = Set.of("shield", "bow", "fishing_rod", "shears", "bucket", "flint_and_steel", "spyglass", "cake", "writable_book");

    private static Set<String> vanillaItems() throws IOException {
        JsonObject registry = new Gson().fromJson(Files.readString(Path.of("..", "tools", "data", "mc1201-registry.json"), StandardCharsets.UTF_8), JsonObject.class);
        Set<String> items = new HashSet<>();
        registry.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
        return items;
    }

    private static boolean single(String name) {
        return SINGLES.contains(name) || name.endsWith("_pickaxe") || name.endsWith("_axe") || name.endsWith("_shovel") || name.endsWith("_hoe") || name.endsWith("_sword");
    }

    @Test
    void everyKindHasATableOfItemsThatExistAndFitTheirStacks() throws IOException {
        Set<String> items = vanillaItems();
        for (String kind : Loot.KINDS) {
            Path file = CHESTS.resolve(kind + ".json");
            assertTrue(Files.exists(file), "no table for " + kind);
            JsonObject table = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            assertEquals("minecraft:chest", table.get("type").getAsString());
            JsonArray pools = table.getAsJsonArray("pools");
            assertTrue(pools.size() >= 2, kind);
            int entries = 0;
            for (JsonElement p : pools) {
                for (JsonElement e : p.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    String name = entry.get("name").getAsString();
                    assertTrue(name.startsWith("minecraft:") && items.contains(name.substring("minecraft:".length())), kind + " has an item that does not exist: " + name);
                    assertTrue(entry.get("weight").getAsInt() >= 1, kind + " " + name);
                    if (entry.has("functions")) {
                        double max = entry.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count").get("max").getAsDouble();
                        assertTrue(max > 1 && max <= 64, kind + " " + name + " " + max);
                        assertTrue(!single(name.substring("minecraft:".length())), kind + ": " + name + " does not stack");
                    }
                    entries++;
                }
            }
            assertTrue(entries >= 15, kind + " has " + entries + " things");
        }
    }

    /** The containers of the plan: where they stand is a chest or a barrel, and what they hold is a table that exists. */
    @Test
    void everyContainerOfThePlanIsAChestOrABarrelWithATable() {
        Map<String, Integer> perKind = new TreeMap<>();
        for (StructurePlacement p : DistrictPlan.get().fixedPlacements()) {
            for (StructurePlacement.MarkerPos m : p.markers()) {
                String name = m.id().substring(m.id().indexOf(':') + 1);
                if (!Loot.isLootMarker(name)) {
                    continue;
                }
                String table = Loot.tableOf(m.data());
                assertTrue(table != null && table.startsWith("lewandivka:chests/"), m.id() + ": " + m.data());
                String kind = table.substring("lewandivka:chests/".length());
                assertTrue(Loot.KINDS.contains(kind), m.id() + ": no table " + table);
                String key = p.blueprint().keyAt(m.x() - p.x(), m.y() - p.y(), m.z() - p.z());
                assertTrue(key != null && (Keys.blockId(key).equals("minecraft:chest") || Keys.blockId(key).equals("minecraft:barrel")), m.id() + " is not a chest or a barrel: " + key);
                perKind.merge(kind, 1, Integer::sum);
            }
        }
        System.out.println("containers of the district: " + perKind);
        assertTrue(perKind.getOrDefault("flat", 0) >= 40, perKind.toString());
        assertTrue(perKind.getOrDefault("house", 0) >= 20, perKind.toString());
        assertTrue(perKind.getOrDefault("kindergarten", 0) >= 3, perKind.toString());
        assertTrue(perKind.getOrDefault("school", 0) >= 3, perKind.toString());
        assertTrue(perKind.getOrDefault("garage", 0) >= 8, perKind.toString());
        assertTrue(perKind.getOrDefault("shed", 0) >= 2, perKind.toString());
        assertTrue(perKind.getOrDefault("shop", 0) >= 3, perKind.toString());
        assertEquals(Loot.KINDS.size(), perKind.size(), "every kind of place has containers");
    }

    @Test
    void theKindsAreTheTablesOfTheMod() {
        for (String kind : Loot.KINDS) {
            assertEquals("lewandivka:chests/" + kind, Loot.table(kind));
        }
        assertTrue(Loot.isLootMarker("loot_3_1_4"));
        assertTrue(Loot.isLootMarker("block_a.loot_3_1_4"));
        assertTrue(!Loot.isLootMarker("stash"));
        assertTrue(!Loot.isLootMarker("looting"));
        assertEquals("lewandivka:chests/flat", Loot.tableOf("table=lewandivka:chests/flat"));
    }
}
