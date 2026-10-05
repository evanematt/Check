package ua.lewandivka.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.entity.GopnikEntity;

import java.util.List;

/** Посилка з Гаража № 13. Постійно бурчить і приманює охоронців. */
public class ParcelItem extends Item {
    public ParcelItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && world.getTime() % 20 == 0 && world.getServer() != null) {
            ua.lewandivka.logic.LewState.get(world.getServer()).set("got_parcel");
        }
        if (world.isClient || !(entity instanceof PlayerEntity player) || world.getTime() % 200 != 0 || world.random.nextFloat() > 0.4f) {
            return;
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1f, 0.6f);
        player.sendMessage(Text.literal("Посилка бурчить: «Поклади мене, де взяв…»").formatted(Formatting.GOLD), true);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false));
        for (GopnikEntity g : world.getEntitiesByClass(GopnikEntity.class, player.getBoundingBox().expand(20), g -> g.getTarget() == null)) {
            g.setTarget(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Бурчить і приманює охоронців. Віднеси Шлагбауму.").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
