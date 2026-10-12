package com.lewandivka.campaign;

import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * The active party. It is never "exactly three players": every encounter asks for the party size when it begins and
 * scales its timers from {@link PartyScale}. The size can be overridden for testing ({@code /lewandivka party}).
 */
public final class PartyService {

    private static int override;

    private PartyService() {
    }

    public static int override() {
        return override;
    }

    /** 0 = automatic. */
    public static void setOverride(int size) {
        override = Math.max(0, Math.min(PartyScale.MAX_SIZE, size));
    }

    /** Living, non-spectating players of the campaign dimensions (and the overworld before the campaign starts). */
    public static List<ServerPlayerEntity> players(MinecraftServer server) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isSpectator() && p.isAlive()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Players currently inside a structure's box (with a margin). */
    public static List<ServerPlayerEntity> inStructure(MinecraftServer server, String structure, double margin) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        Structures.Site site = Structures.site(structure);
        if (site == null) {
            return out;
        }
        Box box = Structures.bounds(structure).expand(margin);
        for (ServerPlayerEntity p : players(server)) {
            if (Dimensions.idOf(p.getWorld()).equals(site.dimension()) && box.contains(p.getPos())) {
                out.add(p);
            }
        }
        return out;
    }

    public static PartyScale scaleFor(MinecraftServer server, String structure) {
        if (override > 0) {
            return PartyScale.of(override);
        }
        int n = inStructure(server, structure, 24).size();
        return PartyScale.of(Math.max(1, n));
    }

    public static PartyScale scale(MinecraftServer server) {
        if (override > 0) {
            return PartyScale.of(override);
        }
        return PartyScale.of(Math.max(1, players(server).size()));
    }
}
