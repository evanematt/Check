package com.lewandivka.item;

import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.util.Ids;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/** The creative tab "Левандівка". Internal helper items stay out of it; everything else is reachable for testing. */
public final class GameTab {

    private GameTab() {
    }

    public static void register() {
        ItemGroup group = FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.lewandivka.main"))
                .icon(() -> new ItemStack(GameItems.get(QuestItems.NOTEBOOK)))
                .entries((context, entries) -> {
                    for (SpecItem item : GameItems.all()) {
                        if (item.spec.inTab) {
                            entries.add(item);
                        }
                    }
                    for (BlockItem item : GameItems.blockItems()) {
                        if (item.getBlock() instanceof com.lewandivka.block.SpecBlock b && b.spec.inTab) {
                            entries.add(item);
                        }
                    }
                })
                .build();
        Registry.register(Registries.ITEM_GROUP, Ids.of("main"), group);
    }
}
