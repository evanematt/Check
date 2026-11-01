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
    public record Spec(String id, List<String> required, String entry, List<String> reachable, List<String> sealed, int groundY,
                       List<String[]> links) {
        public static Spec of(String id, String entry, int groundY) {
            return new Spec(id, new ArrayList<>(), entry, new ArrayList<>(), new ArrayList<>(), groundY, new ArrayList<>());
        }

        /** Connects two marker positions (a dash across a gap, a glide, a spring ride). */
        public Spec link(String from, String to) {
            links.add(new String[] {from, to});
            required.add(from);
            required.add(to);
            return this;
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
        addLiftLinks(bp, open);
        addSpecLinks(bp, spec, open);
        addGateBoxes(bp, open);
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
            if (!standNear(reachOpen, m, 3)) {
                issues.add(new Issue(spec.id(), "marker '" + name + "' is not reachable from '" + spec.entry() + "' even with all gates open"));
            }
        }
        if (!spec.sealed().isEmpty()) {
            Walk closed = new Walk(bp, false, spec.groundY());
            addLiftLinks(bp, closed);
            addSpecLinks(bp, spec, closed);
            addGateBoxes(bp, closed);
            Walk.Reach reachClosed = closed.from(entry.x(), entry.y(), entry.z());
            for (String name : spec.sealed()) {
                Blueprint.Marker m = bp.marker(name);
                if (m != null && standNear(reachClosed, m, 2)) {
                    issues.add(new Issue(spec.id(), "marker '" + name + "' can be reached while the gates are closed (puzzle can be skipped)"));
                }
            }
        }
        return issues;
    }

    private static void addGateBoxes(Blueprint bp, Walk walk) {
        for (Blueprint.Marker m : bp.markers()) {
            if (m.isRegion() && m.data().startsWith("closed=")) {
                walk.openBox(m.x(), m.y(), m.z(), m.sx(), m.sy(), m.sz());
            }
        }
    }

    private static void addSpecLinks(Blueprint bp, Spec spec, Walk walk) {
        for (String[] l : spec.links()) {
            Blueprint.Marker a = bp.marker(l[0]);
            Blueprint.Marker c = bp.marker(l[1]);
            if (a != null && c != null) {
                walk.link(a.x(), a.y(), a.z(), c.x(), c.y(), c.z());
            }
        }
    }

    /** Every region named {@code lift*} with {@code rise=N} links the platform's low and high standing cells. */
    private static void addLiftLinks(Blueprint bp, Walk walk) {
        for (Blueprint.Marker m : bp.markers()) {
            if (m.isRegion() && m.name().startsWith("lift") && m.data().startsWith("rise=")) {
                int rise = Integer.parseInt(m.data().substring(5).trim());
                int cx = m.x() + m.sx() / 2;
                int cz = m.z() + m.sz() / 2;
                walk.link(cx, m.y() + 1, cz, cx, m.y() + 1 + rise, cz);
            }
        }
    }

    private static boolean standNear(Walk.Reach r, Blueprint.Marker m, int radiusY) {
        int cx = m.x() + m.sx() / 2;
        int cz = m.z() + m.sz() / 2;
        // wall-mounted objects (levers, panels, plates) sit up to two blocks above the floor
        return r.containsNear(cx, m.y(), cz, 1, radiusY)
                || (m.isRegion() && r.containsNear(m.x(), m.y(), m.z(), Math.max(m.sx(), m.sz()), 1));
    }
}
