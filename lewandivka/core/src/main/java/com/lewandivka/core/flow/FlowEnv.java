package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.scale.PartyScale;

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

    /** Makes the entities with a tag walk towards a player (guards that heard a noise). */
    void attract(String tag, UUID target);

    /** Gives an item to a player (or everybody in the party when null). */
    void give(UUID player, String item, int count);

    // ------------------------------------------------------------------ campaign
    /** Adds to the counter of the current quest step ("power points 2/3"). */
    void stepProgress(int delta);

    /** Marks a checkpoint of this encounter; respawns after a death go to the marker {@code cp_<n>}. */
    void checkpoint(int index);

    /** Plays a dialogue script for the party. */
    void dialogue(String scriptId);
}
