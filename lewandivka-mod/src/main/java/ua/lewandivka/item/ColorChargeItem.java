package ua.lewandivka.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Кольоровий заряд: тільки його носій може ранити Безбарвного Голову. Згасає за 45 секунд. */
public class ColorChargeItem extends Item {
    public static final int LIFE_TICKS = 900;

    public ColorChargeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || world.getTime() % 20 != 0) {
            return;
        }
        NbtCompound nbt = stack.getOrCreateNbt();
        int life = nbt.getInt("Life") + 20;
        if (life >= LIFE_TICKS) {
            stack.decrement(1);
            if (entity instanceof PlayerEntity p) {
                p.sendMessage(Text.literal("Кольоровий заряд згас. Чекайте новий!").formatted(Formatting.GRAY), true);
            }
            return;
        }
        nbt.putInt("Life", life);
        if (entity instanceof PlayerEntity p) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, false, false));
            if (life % 200 == 0) {
                p.sendMessage(Text.literal("Заряд: " + (LIFE_TICKS - life) / 20 + " с — бий Голову або передай іншому!").formatted(Formatting.AQUA), true);
            }
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Поки він у тебе — твої удари ранять Безбарвного Голову.").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Можна кинути (Q) товаришу. Згасає за 45 с.").formatted(Formatting.GRAY));
    }
}
