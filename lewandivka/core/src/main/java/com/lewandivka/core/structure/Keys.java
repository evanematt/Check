package com.lewandivka.core.structure;

/** Helpers that build block-state keys such as {@code minecraft:oak_stairs[facing=north,half=bottom]}. */
public final class Keys {

    private Keys() {
    }

    public static final String AIR = "minecraft:air";

    /** {@code of("minecraft:lever", "face", "wall", "facing", "north")}. */
    public static String of(String block, String... kv) {
        if (kv.length == 0) {
            return block;
        }
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("odd number of key/value arguments for " + block);
        }
        StringBuilder sb = new StringBuilder(block).append('[');
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(kv[i]).append('=').append(kv[i + 1]);
        }
        return sb.append(']').toString();
    }

    public static String stairs(String block, Dir facing, boolean upsideDown) {
        return of(block, "facing", facing.key(), "half", upsideDown ? "top" : "bottom");
    }

    public static String slab(String block, boolean top) {
        return of(block, "type", top ? "top" : "bottom");
    }

    public static String doubleSlab(String block) {
        return of(block, "type", "double");
    }

    public static String log(String block, char axis) {
        return of(block, "axis", String.valueOf(axis));
    }

    /** One half of a door. {@code facing} is the direction the door faces when closed. */
    public static String door(String block, Dir facing, boolean upper, boolean open, boolean hingeRight) {
        return of(block, "facing", facing.key(), "half", upper ? "upper" : "lower",
                "hinge", hingeRight ? "right" : "left", "open", String.valueOf(open));
    }

    public static String trapdoor(String block, Dir facing, boolean top, boolean open) {
        return of(block, "facing", facing.key(), "half", top ? "top" : "bottom", "open", String.valueOf(open));
    }

    public static String wallTorch(Dir facing) {
        return of("minecraft:wall_torch", "facing", facing.key());
    }

    public static String lantern(boolean hanging) {
        return of("minecraft:lantern", "hanging", String.valueOf(hanging));
    }

    public static String wallSign(String block, Dir facing) {
        return of(block, "facing", facing.key());
    }

    public static String ladder(Dir facing) {
        return of("minecraft:ladder", "facing", facing.key());
    }

    public static String lever(Dir wallFacing) {
        return of("minecraft:lever", "face", "wall", "facing", wallFacing.key(), "powered", "false");
    }

    public static String button(String block, Dir wallFacing) {
        return of(block, "face", "wall", "facing", wallFacing.key());
    }

    public static String rail(String shape) {
        return of("minecraft:rail", "shape", shape);
    }

    public static String vine(Dir... faces) {
        String[] kv = new String[faces.length * 2];
        for (int i = 0; i < faces.length; i++) {
            kv[i * 2] = faces[i].key();
            kv[i * 2 + 1] = "true";
        }
        return of("minecraft:vine", kv);
    }

    public static String leaves(String block) {
        return of(block, "persistent", "true", "distance", "1");
    }

    public static String pane(String block) {
        return block;
    }

    public static String bed(String block, Dir facing, boolean head) {
        return of(block, "facing", facing.key(), "part", head ? "head" : "foot", "occupied", "false");
    }

    public static String barrel(Dir facing) {
        return of("minecraft:barrel", "facing", facing.key());
    }

    /** A mod block (namespace {@code lewandivka}) with optional properties. */
    public static String mod(String name, String... kv) {
        return of("lewandivka:" + name, kv);
    }

    /**
     * A block of another mod and, after a bar, the block of the game that stands in for it when that mod is not installed:
     * {@code handcrafted:oak_chair[facing=north]|minecraft:oak_stairs[facing=south,half=bottom]}. Every analysis of the core (what
     * can be walked on, what is solid) looks at the fallback only, because that is what is always there.
     */
    public static String either(String modKey, String fallbackKey) {
        return modKey + "|" + fallbackKey;
    }

    /** The block that is always there: the part after the bar of an {@link #either} key, the key itself otherwise. */
    public static String fallback(String key) {
        int i = key == null ? -1 : key.indexOf('|');
        return i < 0 ? key : key.substring(i + 1);
    }

    /** The block of the mod of an {@link #either} key, the key itself otherwise. */
    public static String preferred(String key) {
        int i = key == null ? -1 : key.indexOf('|');
        return i < 0 ? key : key.substring(0, i);
    }

    public static boolean isEither(String key) {
        return key != null && key.indexOf('|') >= 0;
    }

    /** Strips the {@code [...]} property part (of the fallback, when the key names a block of a mod and a stand-in). */
    public static String blockId(String key) {
        key = fallback(key);
        int i = key.indexOf('[');
        return i < 0 ? key : key.substring(0, i);
    }
}
