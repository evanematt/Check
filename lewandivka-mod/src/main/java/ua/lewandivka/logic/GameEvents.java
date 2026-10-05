package ua.lewandivka.logic;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.registry.ModWorldgen;
import ua.lewandivka.world.City;
import ua.lewandivka.world.Sites;

import java.util.ArrayList;
import java.util.List;

public final class GameEvents {
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick(server);
            if (server.getTicks() % 20 == 0) {
                ChromaSync.tick(server);
            }
            if (server.getTicks() % 600 == 0) {
                QuestItems.ensure(server);
            }
            if (server.getTicks() % 40 == 0) {
                Hud.update(server);
            }
            if (server.getTicks() % 20 == 0) {
                ServerWorld chroma = server.getWorld(ModWorldgen.CHROMA);
                if (chroma != null && !chroma.getPlayers().isEmpty()) {
                    Sites.tickProximity(chroma);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Scheduler.clear());
        ServerLifecycleEvents.SERVER_STARTED.register(City::ensure);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    City.onJoin(handler.player);
                    ua.lewandivka.ability.Abilities.syncOnJoin(handler.player);
                    QuestItems.ensure(server);
                }));

        // Загинув у Хромандівці — прокидаєшся в будинку-базі.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            QuestItems.ensure(newPlayer.getServer());
            if (alive || !oldPlayer.getWorld().getRegistryKey().equals(ModWorldgen.CHROMA)) {
                return;
            }
            LewState st = LewState.get(newPlayer.getServer());
            if (st.has("done_tower")) {
                return;
            }
            Scheduler.after(newPlayer.getServer(), 2, () -> {
                ServerWorld chroma = newPlayer.getServer().getWorld(ModWorldgen.CHROMA);
                if (chroma == null || newPlayer.isRemoved()) {
                    return;
                }
                var spawn = st.point("base", "spawn");
                if (spawn != null) {
                    newPlayer.teleport(chroma, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, newPlayer.getYaw(), 0);
                }
            });
        });

        // Коти, які йдуть за гравцем, переходять з ним між вимірами.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            List<ChromaCatEntity> cats = new ArrayList<>(origin.getEntitiesByType(TypeFilter.instanceOf(ChromaCatEntity.class),
                    c -> c.isBonded() && c.isFollowing(player.getUuid())));
            for (ChromaCatEntity cat : cats) {
                cat.teleportTo(destination, player.getPos());
            }
        });
    }

    private GameEvents() {
    }
}
