package com.lewandivka.world.service;

import com.lewandivka.core.world.Launch;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code wind_*} regions of the structures: an updraft with a push ({@code dir=east,speed=0.3}), used by the sky tube, the spring
 * shaft of the tower approach and the service shaft of the tower. The client applies them to its own player each second tick
 * (see {@code Winds}): it owns the movement of its player, and a push that the server computed from the positions it was sent
 * would always be a little out of date (it was, and the throw of a spring hatch lost two blocks of height to it). The push itself
 * is {@link Launch#wind}, which the level tests fly through every hatch.
 */
public final class WindRegions {

    /** One region of wind: where it blows and how. */
    public record Region(String dimension, Box box, double dx, double dz, double speed) {

        /** The velocity of a rider after a push of this wind. */
        public Vec3d push(Vec3d v) {
            double[] n = Launch.wind(v.x, v.y, v.z, dx, dz, speed);
            return new Vec3d(n[0], n[1], n[2]);
        }
    }

    private static volatile List<Region> regions;
    private static volatile boolean preparing;

    private WindRegions() {
    }

    /**
     * Starts building the index on a worker thread (the first look at the structures of a client that joins a dedicated server
     * costs a moment, which must not happen in the middle of a tick, let alone in the middle of a jump).
     */
    public static void prepare() {
        if (regions == null && !preparing) {
            preparing = true;
            Thread worker = new Thread(WindRegions::index, "lewandivka-wind-index");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static synchronized List<Region> index() {
        if (regions != null) {
            return regions;
        }
        List<Region> list = new ArrayList<>();
        for (Structures.Site site : Structures.sites()) {
            for (Marker m : Structures.markersOf(site.placement().id())) {
                if (m.name().startsWith("wind_") && m.isRegion()) {
                    double dx = 0;
                    double dz = 0;
                    switch (m.data("dir", "up")) {
                        case "east" -> dx = 1;
                        case "west" -> dx = -1;
                        case "south" -> dz = 1;
                        case "north" -> dz = -1;
                        default -> {
                        }
                    }
                    list.add(new Region(m.dimension(), m.box(), dx, dz, Double.parseDouble(m.data("speed", "0.3"))));
                }
            }
        }
        regions = list;
        return regions;
    }

    /** The wind that blows at this position of the dimension, or {@code null} (also while the index is still being built). */
    public static Region at(String dimension, Vec3d pos) {
        List<Region> all = regions;
        if (all == null) {
            prepare();
            return null;
        }
        for (Region r : all) {
            if (r.dimension().equals(dimension) && r.box().contains(pos)) {
                return r;
            }
        }
        return null;
    }
}
