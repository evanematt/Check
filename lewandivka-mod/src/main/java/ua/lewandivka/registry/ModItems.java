package ua.lewandivka.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.item.*;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    private static final List<Item> ITEMS = new ArrayList<>();

    public static final Item CHROMA_PILL = register("chroma_pill", new ChromaPillItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)
            .food(new FoodComponent.Builder().hunger(1).saturationModifier(0.2f).alwaysEdible().snack().build())));
    public static final Item DISTRICT_NOTEBOOK = register("district_notebook", new DistrictNotebookItem(new Item.Settings().maxCount(1)));
    public static final Item DISTRICT_TOKEN = register("district_token", new LoreItem(new Item.Settings(),
            "Жетон району. Валюта, яку поважає Шлагбаум."));
    public static final Item MAGIC_KETTLE = register("magic_kettle", new QuestLoreItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON),
            "Той самий чайник, який Боржник «позичив» у Шлагбаума.", "got_kettle"));
    public static final Item GARAGE_PARCEL = register("garage_parcel", new ParcelItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item TRAM_COMPOSTOR = register("tram_compostor", new QuestLoreItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON),
            "Компостер з останнього трамваю. Кондуктор буде радий.", "got_compostor"));
    public static final Item NOTE_BORZHNYK = register("note_borzhnyk", new QuestNoteItem(new Item.Settings().maxCount(1), QuestNoteItem.Quest.BORZHNYK));
    public static final Item NOTE_GARAGE = register("note_garage", new QuestNoteItem(new Item.Settings().maxCount(1), QuestNoteItem.Quest.GARAGE));
    public static final Item NOTE_TRAM = register("note_tram", new QuestNoteItem(new Item.Settings().maxCount(1), QuestNoteItem.Quest.TRAM));
    public static final Item COLLAR_CHINAZIK = register("collar_chinazik", new LoreItem(new Item.Settings().maxCount(1),
            "Великий червоний нашийник з дзвіночком. Пахне рибою."));
    public static final Item COLLAR_METADONNA = register("collar_metadonna", new LoreItem(new Item.Settings().maxCount(1),
            "Крихітний рожевий нашийник. Пахне коробкою."));
    public static final Item COLOR_CHARGE = register("color_charge", new ColorChargeItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
    public static final Item SUNFLOWER_SEEDS = register("sunflower_seeds", new LoreItem(new Item.Settings()
            .food(new FoodComponent.Builder().hunger(1).saturationModifier(0.1f).snack().build()), "Семки. Класика району."));
    public static final Item DASH_SNEAKERS = register("dash_sneakers", new AbilityItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), AbilityItem.Kind.DASH));
    public static final Item SPRING_INSOLES = register("spring_insoles", new AbilityItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), AbilityItem.Kind.JUMP));
    public static final Item GLIDER_TICKET = register("glider_ticket", new AbilityItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), AbilityItem.Kind.GLIDE));

    public static final Item GOPNIK_SPAWN_EGG = register("gopnik_spawn_egg", new SpawnEggItem(ModEntities.GOPNIK, 0x1b1b1f, 0xf2f2f2, new Item.Settings()));
    public static final Item SHADE_SPAWN_EGG = register("shade_spawn_egg", new SpawnEggItem(ModEntities.SHADE, 0x2a1840, 0xc86bff, new Item.Settings()));
    public static final Item COLORLESS_SPAWN_EGG = register("colorless_spawn_egg", new SpawnEggItem(ModEntities.COLORLESS, 0x808080, 0xd0d0d0, new Item.Settings()));
    public static final Item BORZHNYK_SPAWN_EGG = register("borzhnyk_spawn_egg", new SpawnEggItem(ModEntities.BORZHNYK, 0x6b6b6b, 0xc9a26b, new Item.Settings()));

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Lewandivka.id("main"), FabricItemGroup.builder()
            .icon(() -> new ItemStack(CHROMA_PILL))
            .displayName(Text.literal("Левандівка"))
            .entries((context, entries) -> {
                for (Item item : ITEMS) {
                    entries.add(item);
                }
                for (Block block : ModBlocks.ALL) {
                    entries.add(block);
                }
            })
            .build());

    private static Item register(String name, Item item) {
        Registry.register(Registries.ITEM, Lewandivka.id(name), item);
        ITEMS.add(item);
        return item;
    }

    public static void init() {
    }

    private ModItems() {
    }
}
