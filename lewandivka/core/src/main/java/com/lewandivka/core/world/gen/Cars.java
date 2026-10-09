package com.lewandivka.core.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cars of the district. Every car of the plan is built of blocks ({@link Props#car}) and carries a marker; with the mod
 * <b>Trep's Cars</b> installed the blocks are air and the glue makes a car of the mod at the marker instead (one for every marker,
 * once: if the player drives it away the place stays empty). In a world that was made without the mod, the blocks that are
 * still standing at the marker are taken away when the car is made.
 */
public final class Cars {

    private Cars() {
    }

    /** The id of the mod of the cars. */
    public static final String MOD = "trepscars";

    /** The name of the marker of a car in a blueprint (stamped into another blueprint it is named {@code car.car}, {@code car.car#2} ...). */
    public static final String MARKER = "car";

    /** Half of the length and of the width of the footprint of a car (a car is 5 x 3 blocks). */
    public static final int HALF_LENGTH = 2;
    public static final int HALF_WIDTH = 1;
    public static final int HEIGHT = 3;

    /** The colour codes of the mod for the colours the cars of the district have: the codes are those of the ids of its creatures ({@code car_v1_wb}). */
    private static final Map<String, String> COLORS = Map.of(
            Props.CAR_BLUE, "wb", Props.CAR_WHITE, "ww", Props.CAR_RUST, "wr", Props.CAR_GREEN, "wg", Props.CAR_YELLOW, "wy");

    /** Every colour code the mod has for the three bodies of its cars (the bodies are {@code car_v1}, {@code car_v2} and {@code car_v3}). */
    public static final List<String> ALL_COLORS = List.of("wlb", "wr", "wgr", "wb", "wc", "wm", "wpu", "wp", "wbr", "wlg", "wo", "wbl", "wy", "wg", "ww",
            "wl", "wda", "wdb", "wdc", "wdd", "wdj", "wdo", "wds");

    public static String colorOf(String body) {
        return COLORS.getOrDefault(body, "ww");
    }

    /** The id of the creature of a car: the body (1 to 3) and the colour code. */
    public static String entityId(int body, String color) {
        return MOD + ":car_v" + body + "_" + color;
    }

    /** Which of the three bodies a car at the place has (the same one whenever the world is made). */
    public static int bodyAt(int x, int z) {
        return 1 + (int) Math.floorMod(com.lewandivka.core.world.Noise.hash(0xCA25L, x, z), 3L);
    }

    public static boolean isCarMarker(String name) {
        return name.equals(MARKER) || name.startsWith(MARKER + "." + MARKER);
    }

    /** The turn of a creature (in degrees, as the game counts them: 0 looks south, 90 west, 180 north, 270 east) for a direction word. */
    public static float yaw(String facing) {
        return switch (facing == null ? "" : facing) {
            case "south" -> 0f;
            case "west" -> 90f;
            case "north" -> 180f;
            case "east" -> 270f;
            default -> 0f;
        };
    }

    /** The value of a key of the data of a marker ({@code facing=east,color=wb}), or the default. */
    public static String data(String data, String key, String def) {
        if (data != null) {
            for (String part : data.split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0 && part.substring(0, eq).trim().equals(key)) {
                    return part.substring(eq + 1).trim();
                }
            }
        }
        return def;
    }

    /** A place for a car: the marker and where the car stands and looks. */
    public record Spot(String marker, int x, int y, int z, String facing, String color) {

        /** The cells of the car built of blocks: the corners of its footprint (inclusive) and the number of layers. */
        public int[] footprint() {
            boolean alongX = facing.equals("east") || facing.equals("west");
            int hx = alongX ? HALF_LENGTH : HALF_WIDTH;
            int hz = alongX ? HALF_WIDTH : HALF_LENGTH;
            return new int[] {x - hx, z - hz, x + hx, z + hz};
        }
    }

    /** Every car of a plan, in the order of the plan. */
    public static List<Spot> spots(com.lewandivka.core.world.WorldPlan plan) {
        List<Spot> out = new ArrayList<>();
        for (com.lewandivka.core.structure.StructurePlacement p : plan.fixedPlacements()) {
            for (com.lewandivka.core.structure.StructurePlacement.MarkerPos m : p.markers()) {
                String id = m.id();
                String name = id.substring(id.indexOf(':') + 1);
                if (isCarMarker(name)) {
                    out.add(new Spot(id, m.x(), m.y(), m.z(), data(m.data(), "facing", "east"), data(m.data(), "color", "ww")));
                }
            }
        }
        return out;
    }
}
