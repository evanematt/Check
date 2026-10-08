package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The base house in Chromandivka: waking up, the compass, the four artifact pedestals that open the coloured portal
 * and the quiet moment of the epilogue. Artifacts are put on the pedestals one by one by whoever carries them.
 */
public final class BaseFlow implements Flow {

    public static final String ID = "base";
    public static final List<String> KINDS = List.of("kettle", "package", "composter", "token");
    private static final Map<String, String> ITEM_OF = Map.of(
            "kettle", QuestItems.KETTLE,
            "package", QuestItems.PACKAGE,
            "composter", QuestItems.COMPOSTER,
            "token", QuestItems.TOKEN);

    public static final List<String> MARKERS = List.of("pedestal_kettle", "pedestal_package", "pedestal_composter", "pedestal_token",
            "portal", "portal_plane", "spawn", "checkpoint", "bed_1", "bed_2", "bed_3", "litter", "cat_spot_chinazik", "cat_spot_metadonna");

    private final FlowEnv env;
    private long wakeAt = -1;
    /** The compass was handed out for this wake-up (the story has moved on, or is about to). */
    private boolean woken;

    public BaseFlow(FlowEnv env) {
        this.env = env;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "base";
    }

    public int filled() {
        int n = 0;
        for (String k : KINDS) {
            if (env.record().flag("pedestal." + k)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void playerEntered(UUID player) {
        QuestStep step = env.world().step();
        if (step == QuestStep.BASE_WAKE) {
            startWaking();
        } else if (step == QuestStep.EPI_RETURN && env.record().setFlag("epilogue.seen")) {
            env.dialogue("epilogue_cats");
            wakeAt = env.now() + 200;
        }
    }

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (!marker.startsWith("pedestal_")) {
            return false;
        }
        String kind = marker.substring("pedestal_".length());
        if (!KINDS.contains(kind)) {
            return false;
        }
        if (env.record().flag("pedestal." + kind)) {
            return true;
        }
        if (!ITEM_OF.get(kind).equals(item) || !env.take(player, item, 1)) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        env.record().setFlag("pedestal." + kind);
        env.station(marker, "filled", "true");
        env.sound(marker, "portal.activate");
        env.fx(marker, "reveal");
        env.say(null, "message.lewandivka.pedestal.filled", filled(), KINDS.size());
        env.event(Events.BASE_PEDESTAL);
        return true;
    }

    /** Waking up takes a moment: the dialogue, then the compass. Starts once per wake-up. */
    private void startWaking() {
        if (wakeAt < 0 && !woken) {
            wakeAt = env.now() + 140;
            env.dialogue("base_wake");
        }
    }

    @Override
    public void tick() {
        // the transition carries the party into the base first and moves the story to the waking up a moment later, so
        // nobody "enters" after the step began; the flow only ticks while somebody is inside
        if (env.world().step() == QuestStep.BASE_WAKE) {
            startWaking();
        }
        if (wakeAt >= 0 && env.now() >= wakeAt) {
            wakeAt = -1;
            QuestStep step = env.world().step();
            if (step == QuestStep.BASE_WAKE) {
                woken = true;
                env.event(Events.BASE_COMPASS);
            } else if (step == QuestStep.EPI_RETURN) {
                env.event(Events.EPILOGUE_BASE);
            }
        }
    }

    @Override
    public void rebuild() {
        for (String k : KINDS) {
            if (env.record().flag("pedestal." + k)) {
                env.station("pedestal_" + k, "filled", "true");
            }
        }
    }

    @Override
    public void reset() {
        wakeAt = -1;
        woken = false;
    }

    @Override
    public boolean complete() {
        return filled() == KINDS.size();
    }
}
