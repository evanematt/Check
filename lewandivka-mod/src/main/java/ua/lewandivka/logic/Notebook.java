package ua.lewandivka.logic;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.lewandivka.campaign.Campaign;
import ua.lewandivka.campaign.Stage;
import ua.lewandivka.network.ModNetworking;

import java.util.List;

/** Дані для екрана «Районний блокнот». */
public final class Notebook {
    public static void open(ServerPlayerEntity p) {
        LewState st = LewState.get(p.getServer());
        Stage stage = Campaign.stage(st);
        String[] step = Hud.step(st);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(step[0]);
        buf.writeString(step[1]);
        buf.writeString(stage.clue);
        List<Stage> done = Campaign.history(st);
        buf.writeVarInt(done.size());
        for (Stage s : done) {
            buf.writeString(s.title);
        }
        ServerPlayNetworking.send(p, ModNetworking.NOTEBOOK, buf);
    }

    private Notebook() {
    }
}
