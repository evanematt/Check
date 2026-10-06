package com.lewandivka.core.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What the lootable rubbish heaps ({@code supply_stash}) give. The kind comes from the {@code loot=<kind>} data of the
 * stash marker. Item ids without a namespace are items of the mod, ids with {@code minecraft:} are vanilla items.
 * Quest-critical stashes (the kiosk materials, the fish for Chinazik) always give their guaranteed items.
 */
public final class StashLoot {

    private StashLoot() {
    }

    public record Drop(String item, int min, int max) {
    }

    private static final Map<String, List<Drop>> TABLE = Map.of(
            "kiosk_planks", List.of(new Drop("minecraft:oak_planks", 6, 6)),
            "kiosk_iron", List.of(new Drop("minecraft:iron_ingot", 2, 2)),
            "kiosk_sign", List.of(new Drop("minecraft:oak_sign", 1, 1)),
            "seeds", List.of(new Drop(QuestItems.SEEDS, 3, 6)),
            "fish", List.of(new Drop(QuestItems.FISH, 1, 1)),
            "tokens", List.of(new Drop(QuestItems.TOKEN, 1, 2)),
            "junk", List.of(new Drop("minecraft:string", 1, 3), new Drop("minecraft:iron_nugget", 1, 4), new Drop("minecraft:bread", 1, 1)));

    public static boolean known(String kind) {
        return TABLE.containsKey(kind);
    }

    public static List<String> kinds() {
        return List.copyOf(TABLE.keySet());
    }

    /** Rolls the loot of a stash. Guaranteed kinds always return everything; junk returns a random subset. */
    public static List<Drop> table(String kind) {
        return TABLE.getOrDefault(kind, List.of());
    }

    public static List<Drop> roll(String kind, Random rng) {
        List<Drop> out = new ArrayList<>();
        List<Drop> all = table(kind);
        for (Drop d : all) {
            if (kind.equals("junk") && rng.nextInt(3) == 0) {
                continue;
            }
            int n = d.min() + (d.max() > d.min() ? rng.nextInt(d.max() - d.min() + 1) : 0);
            out.add(new Drop(d.item(), n, n));
        }
        return out;
    }
}
