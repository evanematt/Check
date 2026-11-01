package com.lewandivka.core.world.gen;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the mods of the pack add to the containers of the buildings (the tables of {@link Loot} only hold things of the game itself): the
 * drinks of Alcohol Only, the tobacco of Fumee de Bushy, the food of More Food. The glue adds one more pool to a table when the item
 * is there (a table with the name of an item that does not exist would not load at all, so the names cannot be in the files of the
 * tables), and leaves out what the game does not have: with none of the mods installed the tables are the ones of the files.
 * Nothing for the children: the kindergarten and the school have food and sweets only.
 *
 * <p>The drinks of Alcohol Only are its {@code *_item} items (a bottle that can be drunk and set down); the items without the suffix are the
 * plain block items of the same bottles, which have no model in the mod and show as a purple and black square.</p>
 */
public final class LootExtras {

    private LootExtras() {
    }

    /** One thing of a mod: the item, how often it comes up among the others and how many come at once. */
    public record Extra(String item, int weight, int min, int max) {
        public String mod() {
            return item.substring(0, item.indexOf(':'));
        }
    }

    /** The pool of a kind of place: the chance that it rolls at all for a container, how many times it rolls, and its things. */
    public record Pool(double chance, int minRolls, int maxRolls, List<Extra> items) {
    }

    /** The namespaces of the mods whose items are here: Alcohol Only (its id is {@code shield}), Fumee de Bushy, More Food. */
    public static final Set<String> MODS = Set.of("shield", "fumee-de-bushy", "more_food");

    private static Extra e(String item, int weight, int min, int max) {
        return new Extra(item, weight, min, max);
    }

    private static final Extra BEER = e("shield:beer_item", 6, 1, 3);
    private static final Extra VODKA = e("shield:vodka_item", 4, 1, 2);
    private static final Extra WINE = e("shield:wine_item", 3, 1, 2);
    private static final Extra JAEGER = e("shield:jaegermeister_item", 1, 1, 1);
    private static final Extra CIGARETTE = e("fumee-de-bushy:cigarette", 5, 2, 6);
    private static final Extra TOBACCO = e("fumee-de-bushy:tobacco", 2, 1, 3);

    private static final Extra BAGUETTE = e("more_food:baguette", 5, 1, 2);
    private static final Extra SAUSAGE_ROLL = e("more_food:sausage_roll", 4, 1, 3);
    private static final Extra CRISPS = e("more_food:crisps", 4, 1, 2);
    private static final Extra CHOCOLATE = e("more_food:chocolate_bar", 4, 1, 3);
    private static final Extra WAFFLE = e("more_food:waffle", 2, 1, 2);
    private static final Extra CUPCAKE = e("more_food:cupcake", 3, 1, 2);
    private static final Extra PASTRY = e("more_food:pastry", 2, 1, 2);
    private static final Extra MILK = e("more_food:milk_bottle", 3, 1, 2);
    private static final Extra PIE = e("more_food:apple_pie", 2, 1, 1);
    private static final Extra CHEESE = e("more_food:grilled_cheese", 3, 1, 2);
    private static final Extra OMELETTE = e("more_food:omelette", 3, 1, 2);
    private static final Extra HOTDOG = e("more_food:cooked_porkchop_hotdog", 2, 1, 2);
    private static final Extra MASH = e("more_food:mashed_potato", 2, 1, 2);
    private static final Extra DONUT = e("more_food:chocolate_donut", 3, 1, 2);
    private static final Extra BERRY_DONUT = e("more_food:sweet_berry_donut", 3, 1, 2);
    private static final Extra GUMMY = e("more_food:gummy_bear", 2, 1, 3);
    private static final Extra FLOSS = e("more_food:fairy_floss", 1, 1, 1);

    private static final Map<String, Pool> POOLS = Map.of(
            "flat", new Pool(0.35, 1, 2, List.of(BAGUETTE, SAUSAGE_ROLL, PIE, CHEESE, OMELETTE, WAFFLE, HOTDOG, MASH, MILK, CUPCAKE,
                    e("shield:beer_item", 3, 1, 2), e("shield:wine_item", 2, 1, 1), e("shield:vodka_item", 2, 1, 1), e("fumee-de-bushy:cigarette", 3, 2, 6), e("fumee-de-bushy:tobacco", 1, 1, 3))),
            "house", new Pool(0.45, 1, 2, List.of(BAGUETTE, PIE, PASTRY, CHEESE, OMELETTE, MASH, HOTDOG, MILK, CUPCAKE,
                    e("shield:wine_item", 3, 1, 2), e("shield:beer_item", 3, 1, 2), e("shield:vodka_item", 1, 1, 1), e("fumee-de-bushy:tobacco", 1, 1, 3))),
            "kindergarten", new Pool(0.5, 1, 2, List.of(CUPCAKE, CHOCOLATE, DONUT, BERRY_DONUT, MILK, GUMMY, FLOSS)),
            "school", new Pool(0.45, 1, 2, List.of(CRISPS, CHOCOLATE, BAGUETTE, WAFFLE, MILK, CUPCAKE, SAUSAGE_ROLL)),
            "garage", new Pool(0.6, 1, 2, List.of(BEER, VODKA, JAEGER, CIGARETTE, TOBACCO, CRISPS, SAUSAGE_ROLL)),
            "shed", new Pool(0.5, 1, 2, List.of(e("shield:beer_item", 4, 1, 2), e("shield:vodka_item", 2, 1, 1), e("fumee-de-bushy:cigarette", 3, 2, 5), TOBACCO, SAUSAGE_ROLL)),
            "shop", new Pool(0.9, 1, 3, List.of(BEER, VODKA, WINE, CIGARETTE, TOBACCO, CRISPS, CHOCOLATE, BAGUETTE, SAUSAGE_ROLL, WAFFLE, CUPCAKE, MILK, PASTRY)));

    /** The extras of a kind of place, or null when it has none. */
    public static Pool of(String kind) {
        return POOLS.get(kind);
    }

    /** Whether an item is a drink or tobacco (nothing of the kind is for the children). */
    public static boolean isVice(Extra extra) {
        return extra.item().startsWith("shield:") || extra.item().startsWith("fumee-de-bushy:");
    }
}
