package com.lewandivka.client;

import com.lewandivka.client.screen.CreditsScreen;
import com.lewandivka.client.screen.NotebookScreen;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.network.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Receives the server's state packets and sends the few requests a client may make (everything is validated there). */
public final class ClientNet {

    private ClientNet() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Net.CAMPAIGN, (client, handler, buf, sender) -> {
            int step = buf.readVarInt();
            int counter = buf.readVarInt();
            int mask = buf.readVarInt();
            int rep = buf.readVarInt();
            int rings = buf.readVarInt();
            boolean portal = buf.readBoolean();
            String dimension = "";
            BlockPos target = null;
            if (buf.readBoolean()) {
                dimension = buf.readString();
                target = buf.readBlockPos();
            }
            String dim = dimension;
            BlockPos pos = target;
            client.execute(() -> {
                QuestStep s = QuestStep.byId(step);
                ClientState.step = s == null ? QuestStep.EXPLORE_DISTRICT : s;
                ClientState.counter = counter;
                ClientState.abilityMask = mask;
                ClientState.reputation = rep;
                ClientState.rings = rings;
                ClientState.portal = portal;
                ClientState.compassDimension = dim;
                ClientState.compassTarget = pos;
                ClientState.synced = true;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.NOTEBOOK, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<String> history = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                history.add(buf.readString());
            }
            int m = buf.readVarInt();
            List<Integer> notes = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                notes.add(buf.readVarInt());
            }
            client.execute(() -> {
                ClientState.history = history;
                ClientState.notes = notes;
                client.setScreen(new NotebookScreen());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.SHAKE, (client, handler, buf, sender) -> {
            int strength = buf.readVarInt();
            int ticks = buf.readVarInt();
            client.execute(() -> Effects.shake(strength, ticks));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.CHARGE, (client, handler, buf, sender) -> {
            UUID holder = buf.readBoolean() ? buf.readUuid() : null;
            float fraction = buf.readFloat();
            boolean active = buf.readBoolean();
            client.execute(() -> {
                ClientState.chargeHolder = holder;
                ClientState.chargeFraction = fraction;
                ClientState.chargeActive = active;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.MONOCHROME, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> ClientState.monochrome = on);
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.COOLDOWN, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                Ability a = Ability.byId(id);
                if (a != null) {
                    ClientState.COOLDOWN_UNTIL[a.ordinal()] = ClientState.tick + ticks;
                    ClientState.COOLDOWN_TOTAL[a.ordinal()] = ticks;
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.GLIDE_STOP, (client, handler, buf, sender) -> client.execute(() -> ClientState.gliding = false));
        ClientPlayNetworking.registerGlobalReceiver(Net.CINEMATIC, (client, handler, buf, sender) -> {
            String id = buf.readString();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                if (id.equals("credits")) {
                    client.setScreen(new CreditsScreen(client.currentScreen));
                    return;
                }
                ClientState.cinematic = id;
                ClientState.cinematicLength = ticks;
                ClientState.cinematicTicks = 0;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }

    // ------------------------------------------------------------------ requests

    public static void requestAbility(Ability ability, boolean on) {
        if (!ClientPlayNetworking.canSend(Net.ABILITY)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(ability.id);
        buf.writeBoolean(on);
        ClientPlayNetworking.send(Net.ABILITY, buf);
    }

    public static void passCharge(UUID aimed) {
        if (!ClientPlayNetworking.canSend(Net.PASS_CHARGE)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(aimed != null);
        if (aimed != null) {
            buf.writeUuid(aimed);
        }
        ClientPlayNetworking.send(Net.PASS_CHARGE, buf);
    }

    public static void openNotebook() {
        if (ClientPlayNetworking.canSend(Net.OPEN_NOTEBOOK)) {
            ClientPlayNetworking.send(Net.OPEN_NOTEBOOK, PacketByteBufs.create());
        } else {
            MinecraftClient.getInstance().setScreen(new NotebookScreen());
        }
    }
}
