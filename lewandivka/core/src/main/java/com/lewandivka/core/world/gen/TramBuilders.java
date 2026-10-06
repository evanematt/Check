package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Pal;

/**
 * The tram stop where the campaign begins ("Кінцева") and the tram of the Last Tram event.
 *
 * <p>Platform surface is one block above the street; the tram floor is level with it.
 * Layer 0 of the stop replaces the street surface (origin y = ground), layer 0 of the tram is its
 * floor row (origin y = ground + 1).</p>
 */
public final class TramBuilders {

    private TramBuilders() {
    }

    public static final int STOP_W = 25;
    public static final int STOP_D = 8;

    /**
     * Tram stop, 25 x 9 x 8. Platform at z 4..7 (the tram stands south of it), shelter, bench, lamps,
     * three old ticket validators on the platform edge and the player spawn point.
     */
    public static Blueprint tramStop() {
        BlueprintBuilder b = new BlueprintBuilder("tram_stop", STOP_W, 9, STOP_D);
        // platform with a faded yellow safety line on the track side
        b.fill(0, 0, 4, STOP_W - 1, 1, 7, Pal.BRICKS);
        for (int x = 0; x < STOP_W; x++) {
            b.set(x, 1, 7, x % 5 == 3 ? Pal.CONCRETE_GREY : "minecraft:yellow_concrete");
            if (x % 6 == 2) {
                b.set(x, 1, 5, Pal.BRICKS_CRACKED);
            }
        }
        // steps from the northern sidewalk
        for (int x : new int[] {2, 3, 21, 22}) {
            b.set(x, 1, 3, Keys.stairs("minecraft:stone_brick_stairs", Dir.SOUTH, false));
        }
        // shelter: glazed back wall, posts, slab roof
        for (int x = 6; x <= 18; x++) {
            b.set(x, 2, 4, "minecraft:spruce_planks");
            b.set(x, 3, 4, x % 4 == 2 ? "minecraft:spruce_planks" : Pal.PANE);
            b.set(x, 4, 4, "minecraft:spruce_planks");
            for (int z = 4; z <= 6; z++) {
                b.set(x, 5, z, "minecraft:smooth_stone_slab[type=bottom]");
            }
        }
        for (int x : new int[] {6, 12, 18}) {
            b.set(x, 2, 6, "minecraft:polished_blackstone_wall").set(x, 3, 6, "minecraft:polished_blackstone_wall").set(x, 4, 6, "minecraft:polished_blackstone_wall");
        }
        b.set(12, 4, 5, Keys.lantern(true));
        b.set(9, 4, 5, Keys.lantern(true));
        b.set(15, 4, 5, Keys.lantern(true));
        b.stamp(Props.bench(), 8, 2, 4, 0);
        b.set(14, 2, 5, "minecraft:cauldron");
        // station plate "ЛЕВАНДІВКА / Кінцева" on the back wall, facing the platform
        b.set(12, 3, 4, Pal.note(0, Dir.SOUTH));
        // street lamps at both ends
        b.stamp(Props.streetLamp(), 0, 2, 5, 0);
        b.stamp(Props.streetLamp(), 22, 2, 5, 0);
        // three old ticket validators on the platform edge (they only start working in the Last Tram event)
        b.interact("validator_1", 4, 2, 7, Pal.validator(Dir.SOUTH, "none"));
        b.interact("validator_2", 12, 2, 7, Pal.validator(Dir.SOUTH, "none"));
        b.interact("validator_3", 20, 2, 7, Pal.validator(Dir.SOUTH, "none"));
        // first seeds of the neighbourhood: a bag under the bench
        b.interact("stash_seeds", 10, 2, 4, Pal.stash());
        // arrival point
        b.marker("spawn", 14, 2, 6);
        b.marker("platform_center", 12, 2, 6);
        b.region("platform", 0, 1, 4, STOP_W - 1, 4, 7);
        b.region("body", 0, 0, 0, STOP_W - 1, 6, STOP_D - 1);
        return b.build();
    }

    public static final int TRAM_LEN = 22;

    /**
     * The tram of the Last Tram event, 22 x 6 x 3, driving along +x. The platform is on the north
     * (z = 0) side; both long sides have three door gaps. Markers: {@code fare_box}, {@code door_n1..3},
     * {@code door_s1..3} (outside of each door gap), {@code cabin}.
     */
    public static Blueprint lastTram() {
        BlueprintBuilder b = new BlueprintBuilder("last_tram", TRAM_LEN, 6, 3);
        // floor, skirts, roof
        b.fill(0, 0, 0, TRAM_LEN - 1, 0, 2, Pal.CONCRETE_GREY);
        b.fill(0, 0, 0, TRAM_LEN - 1, 0, 0, Pal.CONCRETE_BLACK);
        b.fill(0, 0, 2, TRAM_LEN - 1, 0, 2, Pal.CONCRETE_BLACK);
        b.fill(0, 4, 0, TRAM_LEN - 1, 4, 2, "minecraft:white_concrete");
        b.fill(1, 3, 1, TRAM_LEN - 2, 3, 1, Pal.AIR);
        b.fill(0, 3, 0, TRAM_LEN - 1, 3, 0, "minecraft:red_concrete");
        b.fill(0, 3, 2, TRAM_LEN - 1, 3, 2, "minecraft:red_concrete");
        // side walls: red lower band, glass upper band
        for (int x = 0; x < TRAM_LEN; x++) {
            for (int z : new int[] {0, 2}) {
                b.set(x, 1, z, x % 6 == 0 ? Pal.CONCRETE_DARK : "minecraft:red_concrete");
                b.set(x, 2, z, x % 6 == 0 ? Pal.CONCRETE_DARK : Pal.PANE);
            }
        }
        // door gaps (two blocks wide) at x = 4-5, 10-11, 16-17 on both sides
        int[] doors = {4, 10, 16};
        for (int i = 0; i < 3; i++) {
            int x = doors[i];
            b.fill(x, 1, 0, x + 1, 2, 0, Pal.AIR);
            b.fill(x, 1, 2, x + 1, 2, 2, Pal.AIR);
        }
        // ends: cab windows
        for (int z = 0; z < 3; z++) {
            b.set(0, 1, z, "minecraft:red_concrete");
            b.set(0, 2, z, z == 1 ? Pal.GLASS : "minecraft:red_concrete");
            b.set(TRAM_LEN - 1, 1, z, "minecraft:red_concrete");
            b.set(TRAM_LEN - 1, 2, z, z == 1 ? Pal.GLASS : "minecraft:red_concrete");
        }
        b.set(0, 1, 1, "minecraft:yellow_concrete");
        b.set(TRAM_LEN - 1, 1, 1, "minecraft:yellow_concrete");
        // interior: seats along the walls, poles, ceiling light
        b.fill(1, 1, 1, TRAM_LEN - 2, 2, 1, Pal.AIR);
        for (int x = 2; x < TRAM_LEN - 2; x++) {
            boolean doorX = (x >= 4 && x <= 5) || (x >= 10 && x <= 11) || (x >= 16 && x <= 17);
            if (!doorX) {
                b.set(x, 1, 1, Keys.stairs("minecraft:spruce_stairs", x % 2 == 0 ? Dir.NORTH : Dir.SOUTH, false));
            }
        }
        for (int x : new int[] {3, 8, 13, 18}) {
            b.set(x, 3, 1, Pal.light(13));
        }
        for (int x : new int[] {7, 14}) {
            b.set(x, 1, 1, "minecraft:iron_bars").set(x, 2, 1, "minecraft:iron_bars");
        }
        // the fare box that has to be protected in wave two
        b.set(8, 1, 1, Keys.barrel(Dir.NORTH));
        b.marker("fare_box", 8, 1, 1);
        // pantograph
        b.set(11, 5, 1, "minecraft:iron_bars");
        b.set(11, 4, 1, "minecraft:iron_bars");
        b.set(10, 5, 1, "minecraft:lightning_rod[facing=west]");
        b.set(12, 5, 1, "minecraft:lightning_rod[facing=east]");
        for (int i = 0; i < 3; i++) {
            b.marker("door_n" + (i + 1), doors[i], 1, 0);
            b.marker("door_s" + (i + 1), doors[i], 1, 2);
        }
        b.marker("cabin", 11, 1, 1);
        b.region("body", 0, 0, 0, TRAM_LEN - 1, 5, 2);
        return b.build();
    }
}
