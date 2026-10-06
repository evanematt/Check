package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Pal;

/**
 * The way up: a start pad on the ground, four stepping islands with directional spring pads, a bounce
 * tunnel and the sky tram stop. Spring pads give a large launch only to players with the Spring
 * Insoles; every launch carries a fixed horizontal drift so nobody has to steer perfectly.
 *
 * <p>Local layer 0 is the ground surface of the start pad (world y = {@link #ORIGIN_Y}).</p>
 */
public final class SkyAscent {

    private SkyAscent() {
    }

    public static final int SX = 100;
    public static final int SY = 108;
    public static final int SZ = 60;
    public static final int ORIGIN_X = ChromaPlan.ASCENT_X - 14;
    public static final int ORIGIN_Y = 72;
    public static final int ORIGIN_Z = ChromaPlan.ASCENT_Z - 30;

    /** Local coordinates of the tram stop platform centre: world position of the sky stop. */
    public static final int STOP_X = 90;
    public static final int STOP_Y = 91;
    public static final int STOP_Z = 24;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static void island(BlueprintBuilder b, String name, int cx, int y, int cz, int r, int seed) {
        b.disc(cx, y, cz, r, Pal.GRASS);
        b.disc(cx, y - 1, cz, r - 1, Pal.DIRT);
        b.disc(cx, y - 2, cz, r - 2, Pal.DIRT);
        b.disc(cx, y - 3, cz, Math.max(1, r - 3), "minecraft:stone");
        b.disc(cx, y - 4, cz, Math.max(1, r - 4), Pal.TUFF);
        b.disc(cx, y - 5, cz, Math.max(1, r - 5), Pal.AMETHYST);
        b.set(cx + r - 2, y + 1, cz, "minecraft:allium");
        b.set(cx - r + 2, y + 1, cz + 1, "minecraft:pink_tulip");
        b.marker(name, cx, y + 1, cz);
    }

    /**
     * Launch calibration (vanilla physics, see the unit test): a pad launches with vertical speed 2.0
     * (apex +20 blocks) and horizontal speed 1.4 along its facing (about 14 blocks of drift); a tunnel hatch
     * launches straight up with speed 2.6 (apex +32 blocks).
     */
    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("sky_ascent", SX, SY, SZ);
        b.clip(false);
        // start platform on the ground
        b.disc(14, 0, 30, 7, Pal.BRICKS);
        b.ring(14, 0, 30, 7, Pal.BRICKS_MOSSY);
        b.disc(14, 0, 30, 2, Pal.CONCRETE_DARK);
        b.interact("pad_0", 14, 1, 30, Keys.mod("spring_pad", "facing", "east"));
        b.marker("ascent_start", 10, 1, 30);
        b.interact("cp_start", 8, 1, 28, Pal.checkpoint());
        b.set(10, 2, 33, Pal.note(60, Dir.WEST));
        // island 1 (surface y = 17), island 2 (33), island 3 (49): each 16 higher and 16-18 further east
        island(b, "isle_1", 24, 17, 30, 6, 1);
        b.interact("pad_1", 24, 18, 30, Keys.mod("spring_pad", "facing", "east"));
        island(b, "isle_2", 42, 33, 30, 5, 2);
        b.interact("pad_2", 42, 34, 30, Keys.mod("spring_pad", "facing", "east"));
        island(b, "isle_3", 58, 49, 30, 5, 3);
        b.stamp(Nature.crystals(1), 50, 50, 20, 0);
        b.interact("pad_3", 58, 50, 30, Keys.mod("spring_pad", "facing", "east"));
        b.interact("cp_isle3", 61, 50, 30, Pal.checkpoint());
        // island 4 carries the bounce tunnel: a glass tube around a spring hatch, doorway on the west side
        int tx = 70;
        int tz = 30;
        b.disc(tx, 62, tz, 6, Pal.GRASS);
        b.disc(tx, 61, tz, 5, Pal.DIRT);
        b.disc(tx, 60, tz, 4, "minecraft:stone");
        b.disc(tx, 59, tz, 2, Pal.AMETHYST);
        b.marker("isle_4", tx - 5, 63, tz);
        for (int y = 63; y <= 94; y++) {
            b.ring(tx, y, tz, 3, y % 4 == 0 ? Pal.CYAN_CONCRETE : "minecraft:light_blue_stained_glass");
            b.disc(tx, y, tz, 2, Pal.AIR);
        }
        b.disc(tx, 95, tz, 3, "minecraft:light_blue_stained_glass");
        b.fill(tx - 3, 63, tz - 1, tx - 3, 64, tz + 1, Pal.AIR);              // doorway to the west
        b.fill(tx + 3, 91, tz - 1, tx + 3, 92, tz + 1, Pal.AIR);              // exit to the stop platform in the east
        b.fill(tx - 1, 90, tz - 1, tx + 2, 90, tz + 1, Pal.AIR);
        b.interact("hatch_up", tx, 62, tz, Pal.springHatch("up"));
        b.set(tx, 62, tz, Pal.springHatch("up"));
        b.marker("tunnel_bottom", tx, 63, tz);
        // wind inside the top of the tube pushes the rider east, towards the exit opening
        b.region("wind_tube", tx - 2, 85, tz - 2, tx + 2, 95, tz + 2, "dir=east,speed=0.3");
        // sky tram stop: platform z 26..31 at y = 90 right next to the tube's east exit, track bed z 32..34
        b.fill(tx + 3, 90, 26, 97, 90, 31, "minecraft:smooth_stone");
        b.fill(tx + 3, 90, 32, 97, 90, 34, "minecraft:polished_andesite");
        for (int x = tx + 4; x <= 97; x++) {
            b.set(x, 91, 32, "minecraft:rail[shape=east_west]");
        }
        b.fill(76, 91, 27, 92, 91, 27, Pal.CONCRETE_GREY);
        for (int x : new int[] {78, 84, 90}) {
            b.fill(x, 92, 27, x, 94, 27, "minecraft:polished_blackstone_wall");
            b.set(x, 95, 28, Keys.lantern(true));
        }
        b.fill(76, 95, 27, 92, 95, 29, "minecraft:smooth_stone_slab[type=bottom]");
        b.set(84, 93, 27, Pal.note(61, Dir.SOUTH));
        b.interact("cp_stop", 80, 91, 29, Pal.checkpoint());
        b.marker("tram_stop_lower", 90, 91, 30);
        b.marker("stop_platform", 86, 91, 30);
        b.marker("tunnel_top", tx + 4, 91, tz);
        for (int x = tx + 3; x <= 97; x++) {
            b.set(x, 91, 26, "minecraft:polished_blackstone_wall");
        }
        b.region("body", 0, 0, 0, SX - 1, SY - 1, SZ - 1);
        return b.build();
    }
}
