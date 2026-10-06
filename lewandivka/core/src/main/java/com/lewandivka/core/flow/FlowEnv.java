package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.scale.PartyScale;

import java.util.List;
import java.util.UUID;

/**
 * Everything a dungeon flow may do to the world. The game glue implements it with real blocks and entities, the unit
 * tests with a recording fake. Marker names are local to the structure of the flow ({@code breaker_1}); the glue
 * resolves them to block positions through the marker registry.
 */
public interface FlowEnv {

    /** Structure id of the flow (also the marker namespace), for example {@code garage13}. */
    String structure();

    /** Server tick counter (monotonic across the session). */
    long now();

    /** Active party of the encounter (decided when the encounter began). */
    PartyScale party();

    /** Persistent state of this encounter (flags survive restarts). */
    EncounterRecord record();

    /** Campaign-wide progress (counters and flags shared by all encounters, defeated bosses ...). */
    WorldProgress world();

    /** Players that are currently inside the structure. */
    List<UUID> players();

    // ------------------------------------------------------------------ the world
    /** Sets a block-state property of the block at a point marker, e.g. {@code lit = true}, {@code powered = false}. */
    void station(String marker, String property, String value);

    /** Replaces the block at a point marker (reveals, plates). */
    void block(String marker, String blockKey);

    /** Opens or closes a gate region. */
    void gate(String gate, boolean open);

    /** Starts a moving platform between its low and high position (lifts). */
    void moveLift(String lift, boolean up);

    /** Fills or drains a region with the dungeon's liquid ({@code fill = true} floods it). */
    void flood(String region, boolean fill);

    // ------------------------------------------------------------------ feedback (two channels: text + sound/particles)
    /** Action-bar message; {@code player == null} tells everybody inside the structure. */
    void say(UUID player, String langKey, Object... args);

    void sound(String marker, String soundId);

    /** Plays a sound at a player (the growling package, ability sounds). */
    void soundAt(UUID player, String soundId);

    void fx(String marker, String effect);

    // ------------------------------------------------------------------ entities and items
    /** Spawns {@code count} entities of a type at a marker; {@code tag} lets the flow find them again. */
    void spawn(String entity, String marker, int count, String tag);

    /** Drops an item entity at a marker (the package in garage 13); {@code tag} lets the flow find it again. */
    void dropItem(String marker, String item, int count, String tag);

    /** Removes the entities spawned with a tag. */
    void despawn(String tag);

    /** Number of living entities that were spawned with a tag. */
    int alive(String tag);

    /** Spawns a boss of the encounter at a marker. The boss entity starts its fight when a player gets close. */
    void spawnBoss(String entity, String marker);

    /** Removes a boss and everything it spawned (reset). */
    void despawnBoss(String entity);

    /** Removes items from a player (a battery put into a socket). @return false when the player does not carry them */
    boolean take(UUID player, String item, int count);

    boolean has(UUID player, String item);

    /** Makes the entities with a tag walk towards a player (guards that heard a noise). */
    void attract(String tag, UUID target);

    /** Gives an item to a player (or everybody in the party when null). */
    void give(UUID player, String item, int count);

    // ------------------------------------------------------------------ time, queries and bosses
    /** True while it is night (the last tram needs it). */
    boolean night();

    /** Holds the world at cinematic night and stops the day cycle (restores both when switched off). */
    void forceNight(boolean on);

    /** Number of living entities with the tag within {@code radius} of the marker. */
    int near(String tag, String marker, double radius);

    /** Damage factor of every living entity of this type in the structure (1 = normal, 0.15 = shielded). */
    void resistance(String entity, double factor);

    /**
     * Forwards a station press to the boss that owns the mechanic (arena levers, collar stands, drains, composters,
     * relays).
     *
     * @return true when the boss accepted it
     */
    boolean bossStation(String entity, String action, int index, UUID player);

    /** Plays a named cinematic (letterbox, title, camera) for everybody in the structure. */
    void cinematic(String id);

    // ------------------------------------------------------------------ campaign
    /** Reports a story event; the campaign director decides what it completes (see {@code Events}). */
    void event(String id);

    /** Sets the displayed counter of the current quest step for steps that are not counted by events (levers 2/3). */
    void stepCounter(int value);

    /** Marks a checkpoint of this encounter; respawns after a death go to the marker {@code cp_<n>}. */
    void checkpoint(int index);

    /** Plays a dialogue script for the party. */
    void dialogue(String scriptId);
}
