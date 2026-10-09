package com.lewandivka.world.service;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.world.gen.LootExtras;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The things of the other mods of the pack (drinks, tobacco, food: see {@link LootExtras}) in the tables of the containers of the
 * buildings. The tables of the files only name things of the game; this adds one more pool to every table as it is loaded, with those
 * items the game has, so that a game without these mods gets the tables of the files and not a table that fails to load.
 */
public final class ModLoot {

    private ModLoot() {
    }

    private static final Set<String> USED = ConcurrentHashMap.newKeySet();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<String> TABLES = ConcurrentHashMap.newKeySet();

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (!LewandivkaMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("chests/")) {
                return;
            }
            LootExtras.Pool extras = LootExtras.of(id.getPath().substring("chests/".length()));
            if (extras == null) {
                return;
            }
            LootPool.Builder pool = LootPool.builder()
                    .rolls(UniformLootNumberProvider.create(extras.minRolls(), extras.maxRolls()))
                    .conditionally(RandomChanceLootCondition.builder((float) extras.chance()));
            int count = 0;
            for (LootExtras.Extra extra : extras.items()) {
                Identifier item = Identifier.tryParse(extra.item());
                if (item == null || !Registries.ITEM.containsId(item)) {
                    MISSING.add(extra.item());
                    continue;
                }
                pool.with(ItemEntry.builder(Registries.ITEM.get(item)).weight(extra.weight())
                        .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(extra.min(), extra.max()))));
                USED.add(extra.item());
                count++;
            }
            if (count > 0) {
                tableBuilder.pool(pool);
                TABLES.add(id.getPath());
            }
        });
    }

    /** What was added to the tables so far (the tables are loaded when a world starts and again by /reload). */
    public static String summary() {
        Set<String> mods = new TreeSet<>();
        for (String item : USED) {
            mods.add(item.substring(0, item.indexOf(':')));
        }
        return "things of other mods in the tables: " + USED.size() + " items of " + (mods.isEmpty() ? "no mod" : mods) + " in " + TABLES.size()
                + " tables" + (MISSING.isEmpty() ? "" : "; not in this game: " + new TreeSet<>(MISSING));
    }

    /** The items asked for that the game does not have (the mod is not installed, or its item has another name). */
    public static Set<String> missing() {
        return new TreeSet<>(MISSING);
    }

    public static Set<String> used() {
        return new TreeSet<>(USED);
    }
}
