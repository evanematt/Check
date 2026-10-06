package com.lewandivka.core.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation of a blueprint against its specification: required markers exist, quest gates are
 * well formed, and every marker the players have to reach is walkable from the entrance, while
 * gated areas really are closed until the gates open.
 *
 * <p>The same checks run in unit tests (so a build cannot ship an unreachable boss door) and as the
 * structure validation report that the game prints on development startup.</p>
 */
public final class StructureChecks {

    private StructureChecks() {
    }

    /**
     * @param id        structure id (blueprint id)
     * @param required  markers that must exist
     * @param entry     marker players start from (must be a standing position)
     * @param reachable markers that must be reachable on foot from {@code entry} once gates are open
     * @param sealed    markers that must NOT be reachable from {@code entry} while gates are closed
     * @param groundY   untouched cells at or below this layer are terrain
     */
    public record Spec(String id, List<String> required, String entry, List<String> reachable, List<String> sealed, int groundY) {
        public static Spec of(String id, String entry, int groundY) {
            return new Spec(id, new ArrayList<>(), entry, new ArrayList<>(), new ArrayList<>(), groundY);
        }

        public Spec require(String... names) {
            required.addAll(List.of(names));
            return this;
        }

        public Spec reach(String... names) {
            reachable.addAll(List.of(names));
            required.addAll(List.of(names));
            return this;
        }

        public Spec seal(String... names) {
            sealed.addAll(List.of(names));
            required.addAll(List.of(names));
            return this;
        }
    }

    public record Issue(String structure, String message) {
        @Override
        public String toString() {
            return structure + ": " + message;
        }
    }

    public static List<Issue> validate(Blueprint bp, Spec spec) {
        List<Issue> issues = new ArrayList<>();
        for (String name : spec.required()) {
            if (bp.marker(name) == null) {
                issues.add(new Issue(spec.id(), "missing marker '" + name + "'"));
            }
        }
        // gates must carry the block they close with
        for (Blueprint.Marker m : bp.markers()) {
            if (m.isRegion() && m.data().startsWith("closed=") && m.data().length() <= 7) {
                issues.add(new Issue(spec.id(), "gate '" + m.name() + "' has no closed block"));
            }
        }
        Blueprint.Marker entry = spec.entry() == null ? null : bp.marker(spec.entry());
        if (entry == null) {
            if (spec.entry() != null) {
                issues.add(new Issue(spec.id(), "missing entry marker '" + spec.entry() + "'"));
            }
            return issues;
        }
        Walk open = new Walk(bp, true, spec.groundY());
        if (!open.canStand(entry.x(), entry.y(), entry.z())) {
            issues.add(new Issue(spec.id(), "entry marker '" + spec.entry() + "' is not a standing position"));
            return issues;
        }
        Walk.Reach reachOpen = open.from(entry.x(), entry.y(), entry.z());
        for (String name : spec.reachable()) {
            Blueprint.Marker m = bp.marker(name);
            if (m == null) {
                continue;
            }
            if (!standNear(reachOpen, m)) {
                issues.add(new Issue(spec.id(), "marker '" + name + "' is not reachable from '" + spec.entry() + "' even with all gates open"));
            }
        }
        if (!spec.sealed().isEmpty()) {
            Walk closed = new Walk(bp, false, spec.groundY());
            Walk.Reach reachClosed = closed.from(entry.x(), entry.y(), entry.z());
            for (String name : spec.sealed()) {
                Blueprint.Marker m = bp.marker(name);
                if (m != null && standNear(reachClosed, m)) {
                    issues.add(new Issue(spec.id(), "marker '" + name + "' can be reached while the gates are closed (puzzle can be skipped)"));
                }
            }
        }
        return issues;
    }

    private static boolean standNear(Walk.Reach r, Blueprint.Marker m) {
        int cx = m.x() + m.sx() / 2;
        int cz = m.z() + m.sz() / 2;
        // wall-mounted objects (levers, panels, plates) sit up to two blocks above the floor
        return r.containsNear(cx, m.y(), cz, 1, 2)
                || (m.isRegion() && r.containsNear(m.x(), m.y(), m.z(), Math.max(m.sx(), m.sz()), 1));
    }
}
