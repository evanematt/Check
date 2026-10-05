package ua.lewandivka.logic;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import ua.lewandivka.registry.ModVillagers;

import java.util.List;

/** Пан Шлагбаум — торговець біля закинутого кіоску. */
public final class Shlagbaum {
    public static void summonAt(ServerWorld world, BlockPos kiosk, PlayerEntity player) {
        List<VillagerEntity> existing = world.getEntitiesByClass(VillagerEntity.class, new Box(kiosk).expand(24),
                v -> v.getVillagerData().getProfession() == ModVillagers.SHLAGBAUM);
        if (!existing.isEmpty()) {
            player.sendMessage(Text.literal("Пан Шлагбаум уже тут. Поторгуйся з ним — він росте в рівнях, як звичайний житель.").formatted(Formatting.GRAY), true);
            return;
        }
        Direction side = Direction.getFacing(player.getX() - kiosk.getX() - 0.5, 0, player.getZ() - kiosk.getZ() - 0.5);
        BlockPos spot = kiosk.offset(side);
        VillagerEntity v = EntityType.VILLAGER.create(world);
        if (v == null) {
            return;
        }
        v.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, side.getOpposite().asRotation(), 0);
        v.setVillagerData(v.getVillagerData().withProfession(ModVillagers.SHLAGBAUM).withLevel(1));
        v.setExperience(1);
        v.setCustomName(Text.literal("Пан Шлагбаум"));
        v.setCustomNameVisible(true);
        v.setPersistent();
        world.spawnEntity(v);
        player.sendMessage(Text.literal("З-за кіоску виходить Пан Шлагбаум: «Хрома? Є Хрома. Але спершу — стань своїм на районі.»")
                .formatted(Formatting.YELLOW), false);
        player.sendMessage(Text.literal("«Одному не продаю. Двом не працює. Троє — вже можна попробувати.»").formatted(Formatting.YELLOW, Formatting.ITALIC), false);
    }

    private Shlagbaum() {
    }
}
