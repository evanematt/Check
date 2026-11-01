package com.lewandivka.core.puzzle;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sky Depot switch puzzle as a tiny routing graph. A tram starts at node 0 and follows track
 * nodes; every switch node has two exits chosen by its current state. A wrong arrangement ends in a
 * dead end ("buffer stop") which is a recoverable failure: the tram rolls back, nobody dies.
 *
 * <p>Node kinds: {@code SWITCH} (two exits), {@code TRACK} (one exit), {@code GOAL}, {@code DEAD_END}.</p>
 */
public final class TramNetwork {

    public enum Kind {
        SWITCH, TRACK, GOAL, DEAD_END
    }

    public record Node(int id, Kind kind, int exitA, int exitB, int switchIndex) {
    }

    public record Trace(List<Integer> nodes, int terminal, boolean reachedGoal) {
    }

    private final List<Node> nodes = new ArrayList<>();
    private int switchCount;

    public int addSwitch(int exitA, int exitB) {
        int id = nodes.size();
        nodes.add(new Node(id, Kind.SWITCH, exitA, exitB, switchCount++));
        return id;
    }

    public int addTrack(int exit) {
        int id = nodes.size();
        nodes.add(new Node(id, Kind.TRACK, exit, -1, -1));
        return id;
    }

    public int addGoal() {
        int id = nodes.size();
        nodes.add(new Node(id, Kind.GOAL, -1, -1, -1));
        return id;
    }

    public int addDeadEnd() {
        int id = nodes.size();
        nodes.add(new Node(id, Kind.DEAD_END, -1, -1, -1));
        return id;
    }

    /** Replaces a node's exits after creation (needed because exits may point forward). */
    public void setExits(int id, int exitA, int exitB) {
        Node n = nodes.get(id);
        nodes.set(id, new Node(n.id(), n.kind(), exitA, exitB, n.switchIndex()));
    }

    public int switchCount() {
        return switchCount;
    }

    public Node node(int id) {
        return nodes.get(id);
    }

    public int size() {
        return nodes.size();
    }

    /** Follows the tram from node 0 for the given switch states (false = exit A, true = exit B). */
    public Trace trace(boolean[] states) {
        List<Integer> path = new ArrayList<>();
        int cur = 0;
        int guard = nodes.size() * 2 + 4;
        while (guard-- > 0) {
            path.add(cur);
            Node n = nodes.get(cur);
            switch (n.kind()) {
                case GOAL -> {
                    return new Trace(path, cur, true);
                }
                case DEAD_END -> {
                    return new Trace(path, cur, false);
                }
                case TRACK -> cur = n.exitA();
                case SWITCH -> cur = states[n.switchIndex()] ? n.exitB() : n.exitA();
                default -> throw new IllegalStateException();
            }
        }
        // A loop in a badly built network counts as a recoverable dead end.
        return new Trace(path, cur, false);
    }

    /** All switch arrangements that deliver the tram to the goal. A good puzzle has exactly one. */
    public List<boolean[]> solutions() {
        List<boolean[]> out = new ArrayList<>();
        for (int mask = 0; mask < (1 << switchCount); mask++) {
            boolean[] s = new boolean[switchCount];
            for (int i = 0; i < switchCount; i++) {
                s[i] = (mask & (1 << i)) != 0;
            }
            if (trace(s).reachedGoal()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * The canonical Sky Depot layout: four switches in a row, every one of them on the only winning
     * path, each wrong exit ending in its own buffer stop. Exactly one arrangement wins:
     * {@code s0=B, s1=A, s2=B, s3=A}.
     */
    public static TramNetwork depot() {
        TramNetwork t = new TramNetwork();
        int s0 = t.addSwitch(-1, -1); // node 0: the tram enters here
        int s1 = t.addSwitch(-1, -1); // node 1
        int s2 = t.addSwitch(-1, -1); // node 2
        int s3 = t.addSwitch(-1, -1); // node 3
        int d0 = t.addDeadEnd();      // node 4
        int d1 = t.addDeadEnd();      // node 5
        int d2 = t.addDeadEnd();      // node 6
        int d3 = t.addDeadEnd();      // node 7
        int goal = t.addGoal();       // node 8
        t.setExits(s0, d0, s1);
        t.setExits(s1, s2, d1);
        t.setExits(s2, d2, s3);
        t.setExits(s3, goal, d3);
        return t;
    }

    /** The unique winning arrangement of {@link #depot()}: false = A, true = B. */
    public static boolean[] depotSolution() {
        return new boolean[] {true, false, true, false};
    }
}
