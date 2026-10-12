package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.world.Pal;

/**
 * Garage No. 0: the postgame challenge room under the north-east corner of the district. Nobody walks in: the wrong
 * tram brings the party to {@code entry}. A ladder shaft in the corner leads back to the street (a trapdoor in the
 * pavement). Layer {@link #TOP} is the street level.
 */
public final class Garage0 {

    private Garage0() {
    }

    public static final int SX = 25;
    public static final int SY = 26;
    public static final int SZ = 25;
    /** Local y of the street surface (the trapdoor sits there). */
    public static final int TOP = 24;
    public static final int ORIGIN_X = 118;
    public static final int ORIGIN_Z = -148;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("garage0", SX, SY, SZ);
        // the hall: floor y 1, interior y 2..8, ceiling y 9
        b.room(3, 1, 3, 23, 9, 23, Pal.CONCRETE_GREY);
        b.fill(4, 1, 4, 22, 1, 22, Pal.CONCRETE_DARK);
        // parking stripes on the floor: the garage numbers that were never given
        for (int x = 6; x <= 20; x += 4) {
            b.fill(x, 1, 6, x, 1, 20, Pal.ORANGE_CONCRETE);
        }
        // light
        for (int x = 7; x <= 19; x += 6) {
            for (int z = 7; z <= 19; z += 6) {
                b.set(x, 8, z, Pal.light(14));
            }
        }
        // the ladder shaft in the south-west corner: ladder cell (2,y,2), solid walls around it
        b.fill(1, 1, 1, 3, TOP, 3, Pal.CONCRETE_DARK);
        b.fill(2, 2, 2, 2, TOP - 1, 2, Pal.AIR);
        for (int y = 2; y <= TOP - 1; y++) {
            b.set(2, y, 2, "minecraft:ladder[facing=east]");
        }
        b.set(3, 2, 2, Pal.AIR);
        b.set(3, 3, 2, Pal.AIR);
        b.set(2, TOP, 2, "minecraft:oak_trapdoor[facing=east,half=bottom,open=false]");
        b.marker("exit_ladder", 2, 2, 2);
        b.marker("exit_top", 2, TOP, 2);
        // notes next to the ladder
        b.set(4, 4, 2, Pal.note(90, Dir.EAST));
        b.set(12, 4, 23, Pal.note(89, Dir.NORTH));
        // arrival of the wrong tram, a checkpoint and the arena
        b.marker("entry", 12, 2, 21);
        b.interact("cp_entry", 12, 2, 19, Pal.checkpoint());
        b.region("arena", 5, 2, 5, 21, 8, 21);
        b.marker("spawn_1", 6, 2, 6);
        b.marker("spawn_2", 20, 2, 6);
        b.marker("spawn_3", 6, 2, 16);
        b.marker("spawn_4", 20, 2, 16);
        // the reward appears when the third wave is down
        b.marker("reward", 21, 2, 13);
        b.region("body", 3, 1, 3, 23, 9, 23);
        return b.build();
    }
}
