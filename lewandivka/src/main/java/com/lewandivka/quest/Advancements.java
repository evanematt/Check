package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.util.Ids;
import net.minecraft.advancement.Advancement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;

/** Grants the mod's advancements (they use an impossible trigger and are completed from here). */
public final class Advancements {

    public static final String CRITERION = "done";

    private Advancements() {
    }

    public static void grant(ServerPlayerEntity player, String id) {
        MinecraftServer server = player.getServer();
        Advancement adv = server.getAdvancementLoader().get(Ids.of(id));
        if (adv == null) {
            LewandivkaMod.LOGGER.warn("Unknown advancement {}", id);
            return;
        }
        player.getAdvancementTracker().grantCriterion(adv, CRITERION);
    }

    public static void grant(Collection<ServerPlayerEntity> players, String id) {
        for (ServerPlayerEntity p : players) {
            grant(p, id);
        }
    }
}
