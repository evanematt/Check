package com.lewandivka.core.campaign;

import java.util.Locale;

/**
 * District reputation: Чужий → Знайомий → Свій → Районний.
 *
 * <p>Reputation is driven by story progress ({@link #forStep}) so nobody has to farm it. Small
 * personal gestures (giving seeds to the yard) add "points" that can lift a player slightly earlier
 * but never beyond {@link #LOCAL}; the last stage is story-only.</p>
 */
public enum Reputation {
    STRANGER(0),
    ACQUAINTANCE(1),
    LOCAL(2),
    DISTRICT(3);

    public final int level;

    Reputation(int level) {
        this.level = level;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "reputation.lewandivka." + key();
    }

    public boolean isAtLeast(Reputation other) {
        return level >= other.level;
    }

    public static Reputation byLevel(int level) {
        for (Reputation r : values()) {
            if (r.level == level) {
                return r;
            }
        }
        return level <= 0 ? STRANGER : DISTRICT;
    }

    /** Reputation implied by how far the campaign has progressed. */
    public static Reputation forStep(QuestStep step) {
        if (step.isAtLeast(QuestStep.GARAGE_DELIVER)) {
            return DISTRICT;
        }
        if (step.isAtLeast(QuestStep.DEBTOR_RESOLVE) && step.isBefore(QuestStep.GARAGE_DELIVER)) {
            return step == QuestStep.DEBTOR_RESOLVE ? ACQUAINTANCE : LOCAL;
        }
        if (step.isAtLeast(QuestStep.PLACE_KIOSK)) {
            return ACQUAINTANCE;
        }
        return STRANGER;
    }

    /** Reputation from personal gestures only. Capped below {@link #DISTRICT}. */
    public static Reputation fromPoints(int points) {
        if (points >= 8) {
            return LOCAL;
        }
        if (points >= 3) {
            return ACQUAINTANCE;
        }
        return STRANGER;
    }

    public static Reputation effective(QuestStep step, int points) {
        Reputation a = forStep(step);
        Reputation b = fromPoints(points);
        return a.level >= b.level ? a : b;
    }
}
