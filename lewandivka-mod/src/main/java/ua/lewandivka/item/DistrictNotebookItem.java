package ua.lewandivka.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ua.lewandivka.logic.Notebook;

/** Районний блокнот: ПКМ відкриває екран завдань. */
public class DistrictNotebookItem extends Item {
    public DistrictNotebookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            Notebook.open(sp);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
}
