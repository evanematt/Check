package ua.lewandivka.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.entity.ChromaCatEntity;

import java.util.List;
import java.util.UUID;

/** Меню команд котів: сервер відкриває його, клієнт надсилає вибрану команду. */
public final class ModNetworking {
    public static final Identifier OPEN_CAT_MENU = Lewandivka.id("open_cat_menu");
    public static final Identifier CAT_COMMAND = Lewandivka.id("cat_command");
    public static final Identifier HUD = Lewandivka.id("hud");

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(CAT_COMMAND, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            int cmd = buf.readVarInt();
            UUID target = buf.readBoolean() ? buf.readUuid() : null;
            server.execute(() -> {
                Entity e = player.getWorld().getEntityById(entityId);
                if (e instanceof ChromaCatEntity cat && cat.squaredDistanceTo(player) < 64 * 64) {
                    cat.command(cmd, player, target);
                }
            });
        });
    }

    public static void openCatMenu(ServerPlayerEntity player, ChromaCatEntity cat) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(cat.getId());
        buf.writeString(cat.catName());
        buf.writeVarInt(cat.getMode());
        List<ServerPlayerEntity> players = player.getServer().getPlayerManager().getPlayerList();
        buf.writeVarInt(players.size());
        for (ServerPlayerEntity p : players) {
            buf.writeUuid(p.getUuid());
            buf.writeString(p.getName().getString());
        }
        ServerPlayNetworking.send(player, OPEN_CAT_MENU, buf);
    }

    private ModNetworking() {
    }
}
