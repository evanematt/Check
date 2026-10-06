package com.lewandivka.core.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Route choice for a fleeing NPC (the Debtor). The district has a small graph of waypoints
 * (alleys, courtyards, garage rows). At every junction the runner prefers the neighbour that keeps
 * the largest distance to the nearest pursuer, so two or three players can really cut off routes,
 * while a lone player is helped by {@link com.lewandivka.core.scale.PartyScale#fleeStumbleChance()}.
 */
public final class FleePlanner {

    public record Node(int id, double x, double z, boolean deadEnd) {
    }

    private final List<Node> nodes;
    private final List<List<Integer>> adjacency = new ArrayList<>();

    public FleePlanner(List<Node> nodes, List<int[]> edges) {
        this.nodes = List.copyOf(nodes);
        for (int i = 0; i < nodes.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        for (int[] e : edges) {
            adjacency.get(e[0]).add(e[1]);
            adjacency.get(e[1]).add(e[0]);
        }
    }

    public int size() {
        return nodes.size();
    }

    public Node node(int id) {
        return nodes.get(id);
    }

    public List<Integer> neighbours(int id) {
        return adjacency.get(id);
    }

    public int nearest(double x, double z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (Node n : nodes) {
            double d = dist2(n.x(), n.z(), x, z);
            if (d < bestD) {
                bestD = d;
                best = n.id();
            }
        }
        return best;
    }

    /**
     * @param current   node the runner stands on
     * @param previous  node it came from, or -1
     * @param pursuers  {x, z} of every player
     * @return next node to run to (may equal {@code current} when isolated)
     */
    public int choose(int current, int previous, List<double[]> pursuers, Random rng) {
        List<Integer> options = new ArrayList<>(adjacency.get(current));
        if (options.isEmpty()) {
            return current;
        }
        if (options.size() > 1 && previous >= 0) {
            options.remove(Integer.valueOf(previous));
        }
        int best = options.get(0);
        double bestScore = -Double.MAX_VALUE;
        for (int id : options) {
            Node n = nodes.get(id);
            double minD = Double.MAX_VALUE;
            for (double[] p : pursuers) {
                minD = Math.min(minD, Math.sqrt(dist2(n.x(), n.z(), p[0], p[1])));
            }
            if (pursuers.isEmpty()) {
                minD = 0;
            }
            double score = minD + rng.nextDouble() * 1.5;
            if (n.deadEnd() && options.size() > 1) {
                // Prefer loops over dead ends, but never prefer running into a player.
                score -= 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = id;
            }
        }
        return best;
    }

    public static boolean stumbles(double chance, Random rng) {
        return rng.nextDouble() < chance;
    }

    private static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }
}
