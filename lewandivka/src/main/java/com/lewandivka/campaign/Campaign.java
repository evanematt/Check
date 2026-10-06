package com.lewandivka.campaign;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignModel;
import com.lewandivka.core.campaign.CampaignStage;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative access to the campaign. Every system reads and changes the story through this class, which is
 * also the only place that announces step changes, so nothing can move the campaign without the listeners noticing.
 */
public final class Campaign {

    /** Called after the campaign entered a step (also by admin commands). */
    @FunctionalInterface
    public interface StepListener {
        void entered(MinecraftServer server, QuestStep step, boolean forced);
    }

    private static final List<StepListener> LISTENERS = new ArrayList<>();

    private Campaign() {
    }

    public static void addListener(StepListener listener) {
        LISTENERS.add(listener);
    }

    public static CampaignState state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(CampaignState::fromNbt, CampaignState::new, CampaignState.KEY);
    }

    public static CampaignModel model(MinecraftServer server) {
        return state(server).model();
    }

    public static WorldProgress world(MinecraftServer server) {
        return model(server).world();
    }

    public static PlayerProgress player(MinecraftServer server, UUID id) {
        return model(server).player(id);
    }

    public static PlayerProgress player(ServerPlayerEntity player) {
        PlayerProgress p = model(player.getServer()).player(player.getUuid());
        p.setLastName(player.getGameProfile().getName());
        return p;
    }

    /** Marks the campaign as changed so it is written at the next save. */
    public static void dirty(MinecraftServer server) {
        state(server).markDirty();
    }

    public static QuestStep step(MinecraftServer server) {
        return world(server).step();
    }

    public static CampaignStage stage(MinecraftServer server) {
        return world(server).stage();
    }

    public static boolean atLeast(MinecraftServer server, QuestStep step) {
        return world(server).step().isAtLeast(step);
    }

    public static boolean hasAbility(ServerPlayerEntity player, Ability ability) {
        return player(player).has(ability);
    }

    /** Moves the campaign forward (never backwards). @return true when the step changed */
    public static boolean advance(MinecraftServer server, QuestStep target) {
        WorldProgress w = world(server);
        if (!w.advanceTo(target)) {
            return false;
        }
        dirty(server);
        announce(server, target, false);
        return true;
    }

    /** Admin command: sets the step even backwards. */
    public static void force(MinecraftServer server, QuestStep target) {
        world(server).forceStep(target);
        dirty(server);
        announce(server, target, true);
    }

    private static void announce(MinecraftServer server, QuestStep step, boolean forced) {
        if (LewandivkaConfig.get().developerLogs) {
            LewandivkaMod.LOGGER.info("Campaign step -> {} (forced={})", step, forced);
        }
        for (StepListener l : List.copyOf(LISTENERS)) {
            try {
                l.entered(server, step, forced);
            } catch (RuntimeException e) {
                LewandivkaMod.LOGGER.error("Step listener failed for {}", step, e);
            }
        }
    }
}
