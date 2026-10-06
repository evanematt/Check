package com.lewandivka.core.registry;

import java.util.List;

/**
 * One block-state property of a mod block. The game glue builds the real Minecraft properties from this
 * description and the resource generator writes the blockstate files from it, so both can never disagree.
 *
 * <p>Value names are what structure blueprints use in block keys, for example
 * {@code lewandivka:garage_power_panel[kind=breaker,facing=south,lit=false]}.</p>
 */
public record PropSpec(String name, Type type, List<String> values, int min, int max) {

    public enum Type {
        /** {@code true}/{@code false}. */
        BOOL,
        /** Integer in {@code [min, max]}. */
        INT,
        /** Named values (stored as an integer index in the game, written by name in blueprints). */
        ENUM,
        /** A direction; the values list holds the allowed direction names. */
        FACING
    }

    public static final List<String> HORIZONTAL = List.of("north", "south", "west", "east");

    public static PropSpec bool(String name) {
        return new PropSpec(name, Type.BOOL, List.of("false", "true"), 0, 1);
    }

    public static PropSpec integer(String name, int min, int max) {
        return new PropSpec(name, Type.INT, intValues(min, max), min, max);
    }

    public static PropSpec named(String name, String... values) {
        return new PropSpec(name, Type.ENUM, List.of(values), 0, values.length - 1);
    }

    public static PropSpec facing(String... directions) {
        return new PropSpec("facing", Type.FACING, List.of(directions), 0, directions.length - 1);
    }

    public static PropSpec horizontalFacing() {
        return new PropSpec("facing", Type.FACING, HORIZONTAL, 0, 3);
    }

    private static List<String> intValues(int min, int max) {
        String[] out = new String[max - min + 1];
        for (int i = min; i <= max; i++) {
            out[i - min] = Integer.toString(i);
        }
        return List.of(out);
    }

    public boolean allows(String value) {
        return values.contains(value);
    }

    public int count() {
        return values.size();
    }
}
