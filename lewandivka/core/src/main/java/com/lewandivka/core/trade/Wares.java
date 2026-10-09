package com.lewandivka.core.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the traders of the market sell and buy. The currency is the emerald of the game (the district's own tokens are for the
 * trams and stay out of the trade: nothing a quest needs can be spent here). Every trader has a handful of offers in both
 * directions: food and things he sells for emeralds, and the plain goods of a first day of survival (grain, coal, stone, bones)
 * he buys, so that anyone can come by their first emerald.
 */
public final class Wares {

    private Wares() {
    }

    public static final String CURRENCY = "minecraft:emerald";

    /** One offer: the player gives {@code give} x {@code giveCount} and gets {@code get} x {@code getCount}, {@code maxUses} times between two restocks. */
    public record Offer(String give, int giveCount, String get, int getCount, int maxUses) {

        public boolean sells() {
            return give.equals(CURRENCY);
        }

        public boolean buys() {
            return get.equals(CURRENCY);
        }
    }

    private static Offer sell(String item, int count, int price, int maxUses) {
        return new Offer(CURRENCY, price, "minecraft:" + item, count, maxUses);
    }

    private static Offer buy(String item, int count, int emeralds, int maxUses) {
        return new Offer("minecraft:" + item, count, CURRENCY, emeralds, maxUses);
    }

    private static final Map<String, List<Offer>> OFFERS = Map.of(
            "vendor_baker", List.of(
                    sell("bread", 6, 1, 16), sell("cookie", 18, 2, 8), sell("pumpkin_pie", 3, 1, 8), sell("cake", 1, 2, 4),
                    sell("sugar", 8, 1, 8), sell("milk_bucket", 1, 1, 6),
                    buy("wheat", 20, 1, 24), buy("egg", 12, 1, 16), buy("sugar_cane", 20, 1, 16)),
            "vendor_greengrocer", List.of(
                    sell("apple", 5, 1, 16), sell("carrot", 8, 1, 16), sell("baked_potato", 6, 1, 16), sell("melon_slice", 10, 1, 16),
                    sell("sweet_berries", 8, 1, 12), sell("beetroot_soup", 1, 1, 8), sell("golden_carrot", 2, 3, 6),
                    buy("potato", 26, 1, 24), buy("carrot", 22, 1, 24), buy("beetroot", 15, 1, 16), buy("pumpkin", 6, 1, 16), buy("melon", 4, 1, 16)),
            "vendor_butcher", List.of(
                    sell("cooked_porkchop", 4, 1, 16), sell("cooked_beef", 4, 1, 16), sell("cooked_chicken", 6, 1, 16),
                    sell("cooked_mutton", 4, 1, 16), sell("rabbit_stew", 1, 1, 8),
                    buy("chicken", 14, 1, 16), buy("porkchop", 7, 1, 16), buy("beef", 7, 1, 16), buy("mutton", 7, 1, 16),
                    buy("leather", 6, 1, 16), buy("feather", 16, 1, 16)),
            "vendor_handyman", List.of(
                    sell("torch", 16, 1, 24), sell("arrow", 16, 1, 16), sell("shears", 1, 2, 4), sell("bucket", 1, 3, 4),
                    sell("iron_pickaxe", 1, 4, 3), sell("iron_axe", 1, 3, 3), sell("iron_shovel", 1, 2, 3), sell("iron_sword", 1, 4, 3),
                    sell("shield", 1, 3, 3),
                    buy("cobblestone", 48, 1, 32), buy("coal", 15, 1, 24), buy("iron_ingot", 4, 1, 24), buy("copper_ingot", 12, 1, 24),
                    buy("flint", 10, 1, 16)),
            "vendor_flea", List.of(
                    sell("white_bed", 1, 3, 4), sell("lead", 2, 2, 6), sell("name_tag", 1, 5, 3), sell("compass", 1, 3, 3),
                    sell("chest", 2, 1, 8), sell("book", 3, 1, 8), sell("glass_bottle", 6, 1, 8), sell("ladder", 8, 1, 12),
                    buy("string", 12, 1, 16), buy("bone", 12, 1, 16), buy("gunpowder", 5, 1, 16), buy("paper", 16, 1, 16)),
            "vendor_fishmonger", List.of(
                    sell("cooked_cod", 5, 1, 16), sell("cooked_salmon", 4, 1, 16), sell("fishing_rod", 1, 2, 4), sell("oak_boat", 1, 2, 4),
                    sell("bucket", 1, 3, 3),
                    buy("cod", 8, 1, 16), buy("salmon", 7, 1, 16), buy("kelp", 24, 1, 16), buy("ink_sac", 6, 1, 12)),
            "vendor_gardener", List.of(
                    sell("wheat_seeds", 12, 1, 24), sell("pumpkin_seeds", 5, 1, 12), sell("melon_seeds", 5, 1, 12), sell("beetroot_seeds", 8, 1, 12),
                    sell("bone_meal", 8, 1, 16), sell("oak_sapling", 2, 1, 12), sell("birch_sapling", 2, 1, 12), sell("flower_pot", 4, 1, 8),
                    buy("oak_log", 16, 1, 24), buy("birch_log", 16, 1, 24), buy("spruce_log", 16, 1, 24), buy("sweet_berries", 20, 1, 12)));

    /** The offers of a trader of the market (empty for any other creature). */
    public static List<Offer> of(String vendorId) {
        return OFFERS.getOrDefault(vendorId, List.of());
    }

    /** The traders that have goods. */
    public static List<String> vendors() {
        return new ArrayList<>(new java.util.TreeSet<>(OFFERS.keySet()));
    }
}
