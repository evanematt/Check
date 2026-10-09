package com.lewandivka.core.scale;

import com.lewandivka.core.campaign.QuestStep;

/**
 * How many gopniks roam the district at night. Hostile ones are introduced by the first night (step "collect district
 * tokens"), stay lighter afterwards and vanish when the party leaves for the other side; neutral groups that ask for
 * seeds stand at the four marked corners. Everything scales with the party so a lone player is never swarmed.
 */
public final class Population {

    public static final int NEUTRAL_GROUPS = 4;
    public static final long NIGHT_START = 13000L;
    public static final long NIGHT_END = 23000L;

    private Population() {
    }

    public static boolean night(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= NIGHT_START && t <= NIGHT_END;
    }

    /** Hostile gopniks allowed around the players at night. */
    public static int hostileCap(QuestStep step, PartyScale party) {
        if (step.isBefore(QuestStep.COLLECT_TOKENS) || step.isAtLeast(QuestStep.TRAM_FIGHT)) {
            return 0;
        }
        if (step == QuestStep.COLLECT_TOKENS) {
            return 1 + 2 * party.size();
        }
        return 1 + party.size();
    }

    /** Whether neutral "got any seeds?" groups stand at the corners. */
    public static boolean neutralGroups(QuestStep step) {
        return step.isAtLeast(QuestStep.COLLECT_TOKENS) && step.isBefore(QuestStep.TRAM_FIGHT);
    }

    public static int neutralGroupSize(PartyScale party) {
        return party.size() == 1 ? 2 : 3;
    }

    /** Kind of the n-th hostile spawn: one yard senior (guaranteed token) per six, one seed thrower in four. */
    public static String hostileKind(int n) {
        if (n % 6 == 5) {
            return "senior_yard_gopnik";
        }
        return n % 4 == 3 ? "seed_thrower" : "gopnik";
    }
}
