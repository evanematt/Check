package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;

import java.util.List;

/**
 * The chests and barrels of the buildings: every one is an ordinary chest or barrel of the game with a marker that names the
 * table of its contents, and the glue gives the container that table when the chunk is made ({@code LootTable} of its block
 * entity), so that what is inside is made by the game when the container is first opened, different every time and in every world:
 * food, torches, tools, arrows and now and then an emerald. The tables are data files of the mod, one for every kind of place.
 */
public final class Loot {

    private Loot() {
    }

    /** The kinds of places that have a table of their own ({@code data/lewandivka/loot_tables/chests/<kind>.json}). */
    public static final List<String> KINDS = List.of("flat", "house", "kindergarten", "school", "garage", "shed", "shop");

    /** The names of the markers of containers start with this. */
    public static final String MARKER = "loot_";

    /** The id of the table of a kind of place. */
    public static String table(String kind) {
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("no table of loot for " + kind);
        }
        return "lewandivka:chests/" + kind;
    }

    /** Whether the marker (its name in the blueprint, a stamped one is prefixed by the name of the stamped structure) is that of a container. */
    public static boolean isLootMarker(String name) {
        return name.startsWith(MARKER) || name.contains("." + MARKER);
    }

    /** The table named in the data of a marker, or null. */
    public static String tableOf(String data) {
        return Cars.data(data, "table", null);
    }

    /** A chest whose front looks in the direction {@code front}. */
    static void chest(BlueprintBuilder b, int x, int y, int z, Dir front, String kind) {
        b.set(x, y, z, Keys.of("minecraft:chest", "facing", front.key(), "type", "single"));
        b.marker(MARKER + x + "_" + y + "_" + z, x, y, z, "table=" + table(kind));
    }

    /** A barrel that stands with its opening in the direction {@code front}. */
    static void barrel(BlueprintBuilder b, int x, int y, int z, Dir front, String kind) {
        b.set(x, y, z, Keys.barrel(front));
        b.marker(MARKER + x + "_" + y + "_" + z, x, y, z, "table=" + table(kind));
    }

    /** A barrel that stands with its opening upwards, as barrels in a shed or a garage stand. */
    static void barrelUp(BlueprintBuilder b, int x, int y, int z, String kind) {
        b.set(x, y, z, Keys.of("minecraft:barrel", "facing", "up"));
        b.marker(MARKER + x + "_" + y + "_" + z, x, y, z, "table=" + table(kind));
    }
}
