package com.lewandivka.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.Catalog;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.core.world.gen.Nature;
import com.lewandivka.core.world.gen.Props;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every block key the generators can place must exist in Minecraft 1.20.1 or in the mod's own block catalog. */
class RegistryTest {

    private static JsonObject vanilla;

    @BeforeAll
    static void loadRegistry() throws IOException {
        Path p = Path.of("..", "tools", "data", "mc1201-registry.json");
        assertTrue(Files.exists(p), "missing " + p.toAbsolutePath());
        vanilla = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
    }

    /** All block keys that any generator of the mod can produce. */
    static Set<String> allKeys() {
        TreeSet<String> keys = new TreeSet<>();
        List<Blueprint> all = new ArrayList<>(Catalog.namedBlueprints().values());
        for (int i = 0; i < Nature.MUSHROOM_VARIANTS; i++) {
            all.add(Nature.glowshroom(i));
        }
        for (int i = 0; i < Nature.RAINBOW_TREE_VARIANTS; i++) {
            all.add(Nature.rainbowTree(i));
        }
        for (int i = 0; i < Nature.CRYSTAL_VARIANTS; i++) {
            all.add(Nature.crystals(i));
        }
        for (int i = 0; i < Props.TREE_VARIANTS; i++) {
            all.add(Props.tree(i));
        }
        for (WorldPlan plan : List.of(DistrictPlan.get(), ChromaPlan.get())) {
            plan.fixedPlacements().forEach(p -> all.add(p.blueprint()));
            for (int x = -260; x <= 260; x += 24) {
                for (int z = -280; z <= 240; z += 24) {
                    plan.scatterIn(x, z, x + 23, z + 23).forEach(p -> all.add(p.blueprint()));
                }
            }
            TerrainColumn c = new TerrainColumn();
            for (int x = -260; x <= 260; x += 3) {
                for (int z = -280; z <= 240; z += 3) {
                    plan.column(x, z, c);
                    keys.add(c.top);
                    keys.add(c.sub);
                    keys.add(c.base);
                    keys.add(c.fluid);
                    if (c.decor != null) {
                        keys.add(c.decor);
                    }
                }
            }
        }
        for (Blueprint bp : all) {
            for (String k : bp.paletteKeys()) {
                if (k != null) {
                    keys.add(k);
                }
            }
        }
        return keys;
    }

    @Test
    void everyBlockKeyIsValid() {
        JsonObject blocks = vanilla.getAsJsonObject("blocks");
        List<String> problems = new ArrayList<>();
        for (String key : allKeys()) {
            String id = Keys.blockId(key);
            String props = key.length() > id.length() ? key.substring(id.length() + 1, key.length() - 1) : "";
            if (id.startsWith("minecraft:")) {
                String name = id.substring("minecraft:".length());
                if (!blocks.has(name)) {
                    problems.add("not a 1.20.1 block: " + key);
                    continue;
                }
                JsonObject allowed = blocks.getAsJsonObject(name);
                if (!props.isEmpty()) {
                    for (String kv : props.split(",")) {
                        String[] parts = kv.split("=");
                        if (!allowed.has(parts[0])) {
                            problems.add("unknown property in " + key);
                        } else if (!allowed.getAsJsonArray(parts[0]).contains(new Gson().toJsonTree(parts[1]))) {
                            problems.add("bad value in " + key);
                        }
                    }
                }
            } else if (id.startsWith("lewandivka:")) {
                BlockSpec spec = ModBlocks.forKey(id);
                if (spec == null) {
                    problems.add("unknown mod block: " + key);
                    continue;
                }
                String issue = spec.check(props);
                if (issue != null) {
                    problems.add(key + ": " + issue);
                }
            } else {
                problems.add("foreign namespace: " + key);
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void blockCatalogIsConsistent() {
        assertEquals(46, ModBlocks.ALL.size());
        Set<String> names = new HashSet<>();
        for (BlockSpec b : ModBlocks.ALL) {
            assertTrue(names.add(b.id), b.id);
            assertTrue(b.id.matches("[a-z0-9_]+"), b.id);
            assertTrue(b.nameUk != null && !b.nameUk.isBlank() && b.nameEn != null && !b.nameEn.isBlank(), "names of " + b.id);
            for (String v : b.visual) {
                assertTrue(b.prop(v) != null, b.id + " visual property " + v + " does not exist");
            }
            if (b.lightProp != null) {
                assertTrue(b.prop(b.lightProp) != null, b.id + " light property");
            }
            // vanilla limit: block states are stored in a palette, keep them reasonable
            assertTrue(b.stateCount() <= 1200, b.id + " has " + b.stateCount() + " states");
        }
    }

    @Test
    void everyCatalogBlockIsUsedByAStructureOrIsPlayerFacing() {
        Set<String> used = new HashSet<>();
        for (String key : allKeys()) {
            used.add(Keys.blockId(key));
        }
        // blocks that only exist as items placed by the player or created at runtime
        Set<String> runtime = Set.of("lewandivka:kettle_block", "lewandivka:package_block", "lewandivka:abandoned_kiosk",
                "lewandivka:grey_void", "lewandivka:glowshroom_stem", "lewandivka:seed_bowl", "lewandivka:colored_portal",
                "lewandivka:press_head");
        List<String> unused = new ArrayList<>();
        for (BlockSpec b : ModBlocks.ALL) {
            String id = "lewandivka:" + b.id;
            if (!used.contains(id) && !runtime.contains(id)) {
                unused.add(id);
            }
        }
        assertEquals(List.of(), unused, "catalog blocks no structure places (add them to the runtime list if intentional)");
    }

    static Map<String, JsonObject> unused() {
        return Map.of();
    }
}
