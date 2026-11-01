package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Where the Chromatic Compass points: the marker of the current quest step. No beams, no waypoints in the world; only
 * the needle and the landmarks.
 */
public final class Compass {

    private Compass() {
    }

    private static Marker marker(ServerPlayerEntity player) {
        QuestStep step = Campaign.step(player.getServer());
        String id = step.compassMarker;
        if (id == null) {
            id = switch (step) {
                case TOWER_RING -> "base:portal";
                case EPI_RETURN -> "base:spawn";
                default -> null;
            };
        }
        return id == null ? null : Structures.marker(id);
    }

    /** The block the needle points at, or null when this step has no target. */
    public static BlockPos target(ServerPlayerEntity player) {
        Marker m = marker(player);
        return m == null ? null : m.isRegion() ? BlockPos.ofFloored(m.center()) : m.pos();
    }

    /** The dimension of the target (the needle spins when the player is in another one). */
    public static String dimension(ServerPlayerEntity player) {
        Marker m = marker(player);
        return m == null ? Dimensions.CHROMA_ID : m.dimension();
    }
}
