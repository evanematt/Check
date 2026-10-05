package ua.lewandivka.item;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import ua.lewandivka.logic.LewState;

/** Квестовий предмет: у момент, коли він опиняється в інвентарі, світ запам'ятовує «предмет був» — для відновлення. */
public class QuestLoreItem extends LoreItem {
    private final String flag;

    public QuestLoreItem(Settings settings, String lore, String flag) {
        super(settings, lore);
        this.flag = flag;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && world.getTime() % 20 == 0 && world.getServer() != null) {
            LewState.get(world.getServer()).set(flag);
        }
    }
}
