package ua.lewandivka.logic;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Фінал: Безбарвний Голова переможений, всі повертаються у двір, вдома лишається портал. */
public final class Finale {
    public static void victory(ServerWorld chroma) {
        MinecraftServer server = chroma.getServer();
        LewState st = LewState.get(server);
        server.getPlayerManager().broadcast(Text.literal("Безбарвний Голова розсипається сірим пилом. Кольори повертаються в Хромандівку!")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Advancements.grant(p, "victory");
        }
        Scheduler.after(server, 60, () -> server.getPlayerManager().broadcast(
                Text.literal("Серце Хромандівки б'ється знову. Двір під ногами м'яко складається…").formatted(Formatting.LIGHT_PURPLE), false));
        Scheduler.after(server, 160, () -> {
            List<ServerPlayerEntity> party = new ArrayList<>(chroma.getPlayers());
            for (ServerPlayerEntity p : party) {
                Travel.sendHome(p);
            }
            if (party.isEmpty()) {
                return;
            }
            ServerPlayerEntity first = party.get(0);
            ServerWorld home = first.getServerWorld();
            for (UUID id : new UUID[]{st.chinazik, st.metadonna}) {
                if (id == null) {
                    continue;
                }
                Entity e = chroma.getEntity(id);
                if (e instanceof ChromaCatEntity cat) {
                    ChromaCatEntity moved = cat.teleportTo(home, first.getPos());
                    if (moved != null) {
                        moved.bond(first, first.getBlockPos(), home.getRegistryKey());
                    }
                }
            }
            BlockPos portal = findFree(home, first.getBlockPos().add(2, 0, 0));
            home.setBlockState(portal, ModBlocks.CHROMA_PORTAL.getDefaultState());
            Scheduler.after(server, 40, () -> server.getPlayerManager().broadcast(
                    Text.literal("Наче минуло лише п'ять хвилин. Біля під'їзду сидить Пан Шлагбаум: «Ну шо, котів вигуляли?»").formatted(Formatting.YELLOW), false));
            Scheduler.after(server, 100, () -> server.getPlayerManager().broadcast(
                    Text.literal("Вдома лишився маленький кольоровий портал. Хромандівка чекає — її секрети нікуди не ділись.").formatted(Formatting.LIGHT_PURPLE), false));
        });
    }

    private static BlockPos findFree(ServerWorld w, BlockPos start) {
        for (int r = 0; r < 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = start.add(dx, 0, dz);
                    if (w.getBlockState(p).isAir() && !w.getBlockState(p.down()).isAir()) {
                        return p;
                    }
                }
            }
        }
        return start;
    }

    private Finale() {
    }
}
