package com.lewandivka.item;

import com.lewandivka.block.GameBlocks;
import com.lewandivka.block.SpecBlock;
import com.lewandivka.core.registry.ItemSpec;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.util.Ids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Registers the items of the catalog and the block items of every mod block. */
public final class GameItems {

    private static final Map<String, SpecItem> ITEMS = new LinkedHashMap<>();
    private static final Map<String, BlockItem> BLOCK_ITEMS = new LinkedHashMap<>();

    private GameItems() {
    }

    public static void register() {
        for (ItemSpec spec : ModItems.ALL) {
            Item.Settings settings = new Item.Settings().maxCount(spec.stack).rarity(rarity(spec.rarity));
            if (spec.hunger > 0) {
                settings.food(new FoodComponent.Builder().hunger(spec.hunger).saturationModifier(spec.saturation).alwaysEdible().build());
            }
            SpecItem item = new SpecItem(settings, spec);
            Registry.register(Registries.ITEM, Ids.of(spec.id), item);
            ITEMS.put(spec.id, item);
        }
        for (SpecBlock block : GameBlocks.all()) {
            BlockItem item = new BlockItem(block, new Item.Settings());
            Registry.register(Registries.ITEM, Ids.of(block.spec.id), item);
            BLOCK_ITEMS.put(block.spec.id, item);
        }
    }

    private static Rarity rarity(String name) {
        try {
            return Rarity.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    public static SpecItem get(String id) {
        SpecItem item = ITEMS.get(id);
        if (item == null) {
            throw new IllegalArgumentException("unknown item " + id);
        }
        return item;
    }

    public static ItemStack stack(String id, int count) {
        return new ItemStack(get(id), count);
    }

    public static ItemStack stack(String id) {
        return stack(id, 1);
    }

    public static boolean isQuestItem(ItemStack stack) {
        return stack.getItem() instanceof SpecItem s && s.spec.quest;
    }

    /** Catalog id of a mod item stack, or null. */
    public static String idOf(ItemStack stack) {
        return stack.getItem() instanceof SpecItem s ? s.spec.id : null;
    }

    public static BlockItem blockItem(String blockId) {
        return BLOCK_ITEMS.get(blockId);
    }

    public static Iterable<SpecItem> all() {
        return ITEMS.values();
    }

    public static Iterable<BlockItem> blockItems() {
        return BLOCK_ITEMS.values();
    }
}
