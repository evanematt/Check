package com.lewandivka.item;

import com.lewandivka.core.registry.ItemSpec;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The item class of the mod: look and lore from the catalog, behaviour from {@link ItemActions}. */
public class SpecItem extends Item {

    public final ItemSpec spec;

    public SpecItem(Settings settings, ItemSpec spec) {
        super(settings);
        this.spec = spec;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return spec.glint || super.hasGlint(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (!spec.loreUk.isEmpty()) {
            tooltip.add(Text.translatable(spec.loreKey()).formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return spec.hunger > 0 ? spec.eatTicks : super.getMaxUseTime(stack);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            TypedActionResult<ItemStack> result = ItemActions.use(player, hand, this);
            if (result != null) {
                return result;
            }
        }
        return super.use(world, user, hand);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer() instanceof ServerPlayerEntity player) {
            ActionResult result = ItemActions.useOnBlock(player, context, this);
            if (result != null) {
                return result;
            }
        }
        return super.useOnBlock(context);
    }
}
