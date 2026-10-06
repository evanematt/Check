package com.lewandivka.network;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.ability.Abilities;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.Reputation;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.entity.boss.ColorlessHeadEntity;
import com.lewandivka.util.Ids;
import com.lewandivka.quest.Dialogues;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * All packets of the mod. The server is authoritative: client packets are only requests (use an ability, pass the
 * charge, open the notebook) and are validated against the campaign, the cooldowns and the player state.
 */
public final class Net {

    // server -> client
    public static final Identifier CAMPAIGN = Ids.of("campaign");
    public static final Identifier NOTEBOOK = Ids.of("notebook");
    public static final Identifier SHAKE = Ids.of("shake");
    public static final Identifier CHARGE = Ids.of("charge");
    public static final Identifier MONOCHROME = Ids.of("monochrome");
    public static final Identifier COOLDOWN = Ids.of("cooldown");
    public static final Identifier CINEMATIC = Ids.of("cinematic");
    public static final Identifier GLIDE_STOP = Ids.of("glide_stop");
    // client -> server
    public static final Identifier ABILITY = Ids.of("ability");
    public static final Identifier PASS_CHARGE = Ids.of("pass_charge");
    public static final Identifier OPEN_NOTEBOOK = Ids.of("open_notebook");

    private Net() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ABILITY, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            boolean on = buf.readBoolean();
            server.execute(() -> Abilities.request(player, id, on));
        });
        ServerPlayNetworking.registerGlobalReceiver(PASS_CHARGE, (server, player, handler, buf, sender) -> {
            UUID aimed = buf.readBoolean() ? buf.readUuid() : null;
            server.execute(() -> {
                for (ColorlessHeadEntity boss : player.getServerWorld().getEntitiesByClass(ColorlessHeadEntity.class, player.getBoundingBox().expand(70, 40, 70), b -> b.fighting())) {
                    if (boss.passCharge(player, aimed)) {
                        break;
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(OPEN_NOTEBOOK, (server, player, handler, buf, sender) -> server.execute(() -> sendNotebook(player)));
    }

    // ------------------------------------------------------------------ campaign state

    public static void sendCampaign(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        WorldProgress w = Campaign.world(server);
        PlayerProgress p = Campaign.player(player);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(w.step().id);
        buf.writeVarInt(w.stepCounter());
        buf.writeVarInt(p.abilityMask());
        buf.writeVarInt(Reputation.effective(w.step(), p.repPoints()).level);
        buf.writeVarInt(w.ringFragments());
        buf.writeBoolean(w.portalActive());
        BlockPos target = com.lewandivka.quest.Compass.target(player);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeString(com.lewandivka.quest.Compass.dimension(player));
            buf.writeBlockPos(target);
        }
        ServerPlayNetworking.send(player, CAMPAIGN, buf);
    }

    public static void broadcastCampaign(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sendCampaign(p);
        }
    }

    /** The notebook screen: current objective, history and the notes the player has read. */
    public static void sendNotebook(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        WorldProgress w = Campaign.world(server);
        PlayerProgress p = Campaign.player(player);
        sendCampaign(player);
        PacketByteBuf buf = PacketByteBufs.create();
        List<String> history = w.step().isAfter(QuestStep.EXPLORE_DISTRICT)
                ? QuestStep.ordered().stream().filter(s -> s.isBefore(w.step())).map(QuestStep::key).toList() : List.of();
        buf.writeVarInt(history.size());
        for (String s : history) {
            buf.writeString(s);
        }
        List<Integer> notes = p.collectibles().stream().filter(c -> c.startsWith("note.")).map(c -> {
            try {
                return Integer.parseInt(c.substring(5));
            } catch (NumberFormatException e) {
                return -1;
            }
        }).filter(i -> i >= 0).sorted().toList();
        buf.writeVarInt(notes.size());
        for (int n : notes) {
            buf.writeVarInt(n);
        }
        ServerPlayNetworking.send(player, NOTEBOOK, buf);
    }

    // ------------------------------------------------------------------ effects

    public static void cameraShake(Collection<ServerPlayerEntity> players, int strength, int ticks) {
        for (ServerPlayerEntity p : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(strength);
            buf.writeVarInt(ticks);
            ServerPlayNetworking.send(p, SHAKE, buf);
        }
    }

    public static void charge(Collection<ServerPlayerEntity> players, UUID holder, float fraction, boolean active) {
        for (ServerPlayerEntity p : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(holder != null);
            if (holder != null) {
                buf.writeUuid(holder);
            }
            buf.writeFloat(fraction);
            buf.writeBoolean(active);
            ServerPlayNetworking.send(p, CHARGE, buf);
        }
    }

    public static void monochrome(Collection<ServerPlayerEntity> players, boolean on) {
        for (ServerPlayerEntity p : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(on);
            ServerPlayNetworking.send(p, MONOCHROME, buf);
        }
    }

    public static void cooldown(ServerPlayerEntity player, Ability ability, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(ability.id);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(player, COOLDOWN, buf);
    }

    public static void glideStop(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, GLIDE_STOP, PacketByteBufs.create());
    }

    /** Letterbox cinematic with a title; the client keeps the controls but hides the HUD for the duration. */
    public static void cinematic(Collection<ServerPlayerEntity> players, String id, int ticks) {
        for (ServerPlayerEntity p : players) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(id);
            buf.writeVarInt(ticks);
            ServerPlayNetworking.send(p, CINEMATIC, buf);
        }
    }

    static void log(String what) {
        if (com.lewandivka.config.LewandivkaConfig.get().developerLogs) {
            LewandivkaMod.LOGGER.info("[net] {}", what);
        }
    }

    @SuppressWarnings("unused")
    private static void keep(Dialogues d) {
    }
}
