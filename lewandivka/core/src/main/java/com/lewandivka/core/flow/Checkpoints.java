package com.lewandivka.core.flow;

import java.util.List;
import java.util.Map;

/**
 * Which marker is the respawn point of checkpoint {@code n} of a structure. Flows only say "checkpoint 2"; the glue
 * resolves the marker here, so the order of the story is written down once and a test checks that every marker exists.
 */
public final class Checkpoints {

    private static final Map<String, List<String>> TABLE = Map.ofEntries(
            Map.entry("garage13", List.of("cp_0", "cp_1", "cp_2", "cp_3")),
            Map.entry("rainbow_garage", List.of("cp_entrance", "cp_arena")),
            Map.entry("shelter", List.of("cp_entrance", "cp_levers", "cp_warehouse", "cp_arena")),
            Map.entry("aquapark", List.of("cp_lobby", "cp_pumps", "cp_arena")),
            Map.entry("sky_ascent", List.of("cp_start", "cp_isle3", "cp_stop")),
            Map.entry("sky_depot", List.of("cp_arrival", "cp_dispatcher", "cp_yard", "cp_office", "cp_arena")),
            Map.entry("tower_approach", List.of("cp_start", "cp_dash", "cp_hidden", "cp_shaft", "cp_deck", "cp_landing", "cp_tower")),
            Map.entry("garage0", List.of("cp_entry")),
            Map.entry("tower", List.of("cp_gate", "cp_lobby", "cp_archive", "cp_office", "cp_dept", "cp_shaft", "cp_collapsed", "cp_arena")));

    private Checkpoints() {
    }

    public static List<String> names(String structure) {
        return TABLE.getOrDefault(structure, List.of());
    }

    public static Iterable<String> structures() {
        return TABLE.keySet();
    }

    /** Marker name of the checkpoint (the last one when the index is too large), or null when the structure has none. */
    public static String marker(String structure, int index) {
        List<String> names = names(structure);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(Math.max(0, Math.min(index, names.size() - 1)));
    }
}
