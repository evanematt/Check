package com.lewandivka.core.boss;

/**
 * Something the boss rules want the game to do. The rules never touch the world: the boss entity of the game glue
 * turns these events into animations, telegraphs, spawns and damage.
 *
 * @param type what to do
 * @param a    first numeric argument (index of a lane, zone, platform, target ...)
 * @param b    second numeric argument (count, duration in ticks ...)
 * @param text optional key or id
 */
public record BossEvent(Type type, int a, int b, String text) {

    public enum Type {
        // generic
        PHASE, SHIELD_ON, SHIELD_OFF, VULNERABLE_START, VULNERABLE_END, MESSAGE, ADDS, TELEGRAPH, ATTACK, DEFEATED,
        // garage king
        CORE_OPEN, CORE_CLOSE, LIFTS, WHEEL, CAR, MALFUNCTION, REPAIRED,
        // collar collector
        NAMES_HIDDEN, COLLARS_SHUFFLED, COLLAR_REACTION, LEASH, LEASH_END, LEASH_BROKEN_BY_CAT, CLONES, CLONES_GONE, REAL_BOSS_HINT,
        // lady vortex
        ZONE_WARN, ZONE_FLOOD, ZONE_CLEAR, CORES_SPAWN, DRAIN_PHASE_START, DRAIN_PHASE_FAILED,
        // conductor
        TRAM_WARN, TRAM_RUN, TICKET_CALL, TICKET_FAIL,
        // colorless head
        CHARGE_GRANT, PLATFORM_DECAY, PLATFORM_REGROW, EXAM, STEAL_CHARGE, MONOCHROME, CATS_ENTER, CATS_PASS_CHARGE, FINAL_WINDOW
    }

    public static BossEvent of(Type type) {
        return new BossEvent(type, 0, 0, "");
    }

    public static BossEvent of(Type type, int a) {
        return new BossEvent(type, a, 0, "");
    }

    public static BossEvent of(Type type, int a, int b) {
        return new BossEvent(type, a, b, "");
    }

    public static BossEvent of(Type type, String text) {
        return new BossEvent(type, 0, 0, text);
    }
}
