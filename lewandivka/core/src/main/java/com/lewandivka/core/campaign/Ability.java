package com.lewandivka.core.campaign;

import java.util.Locale;

/**
 * Permanent traversal abilities. They are stored on the player (see {@link PlayerProgress}),
 * never as inventory items, so they cannot be lost.
 */
public enum Ability {
    /** Short forward burst. Unlocked by the Garage King. */
    DASH(0, "garage_king", 70, 0),
    /** High bounce from spring blocks and reduced fall impact. Unlocked by Lady Vortex. */
    SPRING_INSOLES(1, "lady_vortex", 0, 0),
    /** Press jump while airborne to glide. Unlocked by the Conductor. */
    GLIDER(2, "conductor", 100, 140);

    /** Stable id used in saves and packets. */
    public final int id;
    /** Boss id whose defeat unlocks this ability for the whole party (and late joiners). */
    public final String unlockBoss;
    /** Cooldown in ticks after use (dash: 3.5 s, glider: 5 s after the glide ends). */
    public final int cooldownTicks;
    /** Energy in ticks (glider only: 7 s). */
    public final int energyTicks;

    Ability(int id, String unlockBoss, int cooldownTicks, int energyTicks) {
        this.id = id;
        this.unlockBoss = unlockBoss;
        this.cooldownTicks = cooldownTicks;
        this.energyTicks = energyTicks;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Ability byId(int id) {
        for (Ability a : values()) {
            if (a.id == id) {
                return a;
            }
        }
        return null;
    }

    public static Ability byKey(String key) {
        for (Ability a : values()) {
            if (a.key().equalsIgnoreCase(key)) {
                return a;
            }
        }
        return null;
    }

    public static Ability byBoss(String bossId) {
        for (Ability a : values()) {
            if (a.unlockBoss.equals(bossId)) {
                return a;
            }
        }
        return null;
    }

    public int bit() {
        return 1 << id;
    }
}
