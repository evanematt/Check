package ua.lewandivka.registry;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import ua.lewandivka.Lewandivka;

/**
 * Пан Шлагбаум прокачується як звичайний житель:
 * 1 жетони → 2 чайник Боржника → 3 посилка з Гаража № 13 → 4 компостер → 5 «Хрома».
 */
public final class ModVillagers {
    public static final RegistryKey<PointOfInterestType> KIOSK_POI_KEY = RegistryKey.of(RegistryKeys.POINT_OF_INTEREST_TYPE, Lewandivka.id("kiosk"));
    public static final PointOfInterestType KIOSK_POI = PointOfInterestHelper.register(Lewandivka.id("kiosk"), 1, 1, ModBlocks.KIOSK);
    public static final VillagerProfession SHLAGBAUM = Registry.register(Registries.VILLAGER_PROFESSION, Lewandivka.id("shlagbaum"),
            new VillagerProfession("shlagbaum", e -> e.matchesKey(KIOSK_POI_KEY), e -> e.matchesKey(KIOSK_POI_KEY),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER));

    public static void init() {
        TradeOfferHelper.registerVillagerOffers(SHLAGBAUM, 1, f -> {
            f.add((entity, random) -> new TradeOffer(new ItemStack(ModItems.DISTRICT_TOKEN, 4), new ItemStack(Items.EMERALD), 16, 5, 0.05f));
            f.add((entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(ModItems.NOTE_BORZHNYK), 4, 2, 0.05f));
        });
        TradeOfferHelper.registerVillagerOffers(SHLAGBAUM, 2, f -> {
            f.add((entity, random) -> new TradeOffer(new ItemStack(ModItems.MAGIC_KETTLE), new ItemStack(Items.EMERALD, 6), 4, 60, 0.05f));
            f.add((entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(ModItems.NOTE_GARAGE), 4, 2, 0.05f));
        });
        TradeOfferHelper.registerVillagerOffers(SHLAGBAUM, 3, f -> {
            f.add((entity, random) -> new TradeOffer(new ItemStack(ModItems.GARAGE_PARCEL), new ItemStack(Items.EMERALD, 8), 4, 80, 0.05f));
            f.add((entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(ModItems.NOTE_TRAM), 4, 2, 0.05f));
        });
        TradeOfferHelper.registerVillagerOffers(SHLAGBAUM, 4, f -> {
            f.add((entity, random) -> new TradeOffer(new ItemStack(ModItems.TRAM_COMPOSTOR), new ItemStack(Items.EMERALD, 10), 4, 100, 0.05f));
            f.add((entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(ModItems.SUNFLOWER_SEEDS, 8), 12, 1, 0.05f));
        });
        TradeOfferHelper.registerVillagerOffers(SHLAGBAUM, 5, f -> {
            f.add((entity, random) -> new TradeOffer(new ItemStack(Items.EMERALD, 5), new ItemStack(ModItems.DISTRICT_TOKEN),
                    new ItemStack(ModItems.CHROMA_PILL), 12, 5, 0.0f));
        });
    }

    private ModVillagers() {
    }
}
