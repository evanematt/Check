package com.lewandivka.core.campaign;

import com.lewandivka.core.data.DataStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Root of the persistent campaign data: one {@link WorldProgress} plus one {@link PlayerProgress}
 * per player. Carries a data version so later releases can migrate old saves (see {@link #migrate}).
 */
public final class CampaignModel {

    /** Version written by this release. Never decrease; add a branch to {@link #migrate} when bumping. */
    public static final int CURRENT_VERSION = 1;

    private int dataVersion = CURRENT_VERSION;
    private WorldProgress world = new WorldProgress();
    private final Map<UUID, PlayerProgress> players = new LinkedHashMap<>();

    public int dataVersion() {
        return dataVersion;
    }

    public WorldProgress world() {
        return world;
    }

    /** Returns the progress of a player, creating a fresh record on first contact. */
    public PlayerProgress player(UUID id) {
        return players.computeIfAbsent(id, PlayerProgress::new);
    }

    public boolean hasPlayer(UUID id) {
        return players.containsKey(id);
    }

    public Collection<PlayerProgress> allPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    // ---- persistence ----

    public void write(DataStore s) {
        s.putInt("dataVersion", CURRENT_VERSION);
        world.write(s.child("world"));
        DataStore p = s.child("players");
        for (String k : new ArrayList<>(p.keys())) {
            p.remove(k);
        }
        for (Map.Entry<UUID, PlayerProgress> e : players.entrySet()) {
            e.getValue().write(p.child(e.getKey().toString()));
        }
    }

    public static CampaignModel read(DataStore s) {
        CampaignModel m = new CampaignModel();
        int version = s.getInt("dataVersion", 0);
        migrate(s, version);
        m.dataVersion = CURRENT_VERSION;
        if (s.hasChild("world")) {
            m.world = WorldProgress.read(s.child("world"));
        }
        if (s.hasChild("players")) {
            DataStore p = s.child("players");
            for (String key : p.childKeys()) {
                try {
                    UUID id = UUID.fromString(key);
                    m.players.put(id, PlayerProgress.read(id, p.child(key)));
                } catch (IllegalArgumentException ignored) {
                    // A corrupted key must never take the whole campaign down.
                }
            }
        }
        return m;
    }

    /**
     * Upgrades an old save tree in place. Version 0 is "no version field" (development builds);
     * it only needs defaults, which every {@code read} already supplies.
     */
    static void migrate(DataStore s, int fromVersion) {
        if (fromVersion >= CURRENT_VERSION) {
            return;
        }
        // v0 -> v1: nothing to move, all newer keys have safe defaults.
        // Future example:
        // if (fromVersion < 2) { moveKey(s, "oldName", "newName"); }
    }
}
