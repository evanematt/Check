package ua.lewandivka.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Нагороди з босів: ривок, пружний стрибок, ширяння. */
public class AbilityItem extends Item {
    public enum Kind { DASH, JUMP, GLIDE }

    private final Kind kind;

    public AbilityItem(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        Vec3d look = user.getRotationVector();
        Vec3d v = user.getVelocity();
        int cooldown;
        switch (kind) {
            case DASH -> {
                user.setVelocity(look.x * 1.7, Math.max(0.3, look.y * 0.5 + 0.3), look.z * 1.7);
                cooldown = 30;
                world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1f, 1.5f);
            }
            case JUMP -> {
                user.setVelocity(v.x, 1.25, v.z);
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 50, 0, false, false));
                cooldown = 50;
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.PLAYERS, 1f, 1.2f);
            }
            default -> {
                user.setVelocity(look.x * 0.9, 0.5, look.z * 0.9);
                user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, false, true));
                cooldown = 200;
                world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.6f, 1.4f);
            }
        }
        user.fallDistance = 0;
        user.velocityModified = true;
        user.getItemCooldownManager().set(this, cooldown);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String text = switch (kind) {
            case DASH -> "ПКМ — ривок у напрямку погляду. Від Гаражного Короля.";
            case JUMP -> "ПКМ — пружний стрибок угору. Від Пані Вирви.";
            case GLIDE -> "ПКМ — ширяння над районом. Від Кондуктора.";
        };
        tooltip.add(Text.literal(text).formatted(Formatting.AQUA));
    }
}
