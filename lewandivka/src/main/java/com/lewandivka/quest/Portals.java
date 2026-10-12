package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.story.Events;
import com.lewandivka.core.world.Pal;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** The coloured portal: a pane in the base (Chromandivka) and one next to the tram stop (the district). */
public final class Portals {

    private static final String BASE_PLANE = "base:portal_plane";
    private static final String DISTRICT_SPOT = "district:portal_district";

    private Portals() {
    }

    public static boolean active(MinecraftServer server) {
        return Campaign.world(server).portalActive();
    }

    /** Fills or clears both panes to match the campaign state. */
    public static void apply(MinecraftServer server) {
        boolean on = active(server);
        ServerWorld chroma = Dimensions.chroma(server);
        Marker plane = Structures.marker(BASE_PLANE);
        if (chroma != null && plane != null) {
            String key = plane.data("plane", Pal.portal('z'));
            for (BlockPos p : BlockPos.iterate(plane.pos(), plane.max())) {
                BlockOps.set(chroma, p.toImmutable(), on ? key : "minecraft:air");
            }
        }
        ServerWorld district = Dimensions.district(server);
        Marker spot = Structures.marker(DISTRICT_SPOT);
        if (district != null && spot != null) {
            BlockPos base = spot.pos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos p = base.add(dx, dy, 0);
                    BlockOps.set(district, p, on ? Pal.portal('x') : "minecraft:air");
                }
            }
            // the frame is part of the district plan (dormant while the pane is empty): nothing to do for it here
        }
        LewandivkaMod.LOGGER.info("Portals {}", on ? "open" : "closed");
    }

    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();

    /** A player stepped into a pane: the other side. */
    public static void enter(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null || !active(server)) {
            return;
        }
        long now = server.getTicks();
        Long last = LAST.get(player.getUuid());
        if (last != null && now - last < 60) {
            return;
        }
        LAST.put(player.getUuid(), now);
        boolean inChroma = Dimensions.CHROMA_ID.equals(Dimensions.idOf(player.getWorld()));
        if (!inChroma) {
            Travel.toMarker(player, "base:portal");
            return;
        }
        Marker spot = Structures.marker(DISTRICT_SPOT);
        ServerWorld district = Dimensions.district(server);
        if (spot == null || district == null) {
            return;
        }
        Vec3d at = Travel.safe(district, new Vec3d(spot.x() + 0.5, spot.y(), spot.z() + 2.5));
        player.teleport(district, at.x, at.y, at.z, 0.0f, 0.0f);
        player.fallDistance = 0.0f;
        if (Campaign.step(server) == QuestStep.EPI_PORTAL) {
            Story.event(server, Events.EPILOGUE_PORTAL);
        }
    }
}
