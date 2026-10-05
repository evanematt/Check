package ua.lewandivka.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.logic.Travel;

import java.util.List;

/** Таблетка «Хрома» — спресований колір іншого виміру. Працює лише коли з'їдять утрьох. */
public class ChromaPillItem extends Item {
    public ChromaPillItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack result = super.finishUsing(stack, world, user);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            Travel.onPillEaten(player);
        }
        return result;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Спресований колір іншого виміру.").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("«Одному не продаю. Двом не працює. Троє — вже можна попробувати».").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
