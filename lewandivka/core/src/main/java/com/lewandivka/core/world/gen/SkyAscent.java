package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Launch;
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
     * Where the island that a pad throws its rider to has to be: its top must start beyond the point that the rider has
     * reached when he passes the height of the top (he is still rising there), and he must come down well inside it.
     * {@code LaunchTest} flies every jump through the finished blueprint to prove it.
     */
    static int islandCentre(int padX, int heightDifference, int radius) {
        int clear = (int) Math.ceil(Launch.clearance(Launch.PAD_UP, Launch.PAD_DRIFT, heightDifference) + 0.5);
        int land = (int) Math.round(Launch.landing(Launch.PAD_UP, Launch.PAD_DRIFT, heightDifference) + 1.5);
        return padX + Math.max(clear + radius, land);
    }

    /**
     * Launch calibration (vanilla physics, see {@link Launch}): a pad launches with vertical speed 2.4 (apex +27 blocks)
     * and horizontal speed 1.8 along its facing; the islands stand where that throw puts the rider. A tunnel hatch
     * launches straight up with speed 2.6 (apex +31 blocks) right next to the exit of the tube.
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
        // island 1 (surface y = 17), island 2 (33), island 3 (49): each 16 higher and about 14 further east
        int x1 = islandCentre(14, 17, 6);
        int x2 = islandCentre(x1, 16, 5);
        int x3 = islandCentre(x2, 16, 5);
        island(b, "isle_1", x1, 17, 30, 6, 1);
        b.interact("pad_1", x1, 18, 30, Keys.mod("spring_pad", "facing", "east"));
        island(b, "isle_2", x2, 33, 30, 5, 2);
        b.interact("pad_2", x2, 34, 30, Keys.mod("spring_pad", "facing", "east"));
        island(b, "isle_3", x3, 49, 30, 5, 3);
        b.stamp(Nature.crystals(1), x3 - 8, 50, 20, 0);
        b.interact("pad_3", x3, 50, 30, Keys.mod("spring_pad", "facing", "east"));
        b.interact("cp_isle3", x3 + 3, 50, 30, Pal.checkpoint());
        // island 4 carries the bounce tunnel: a glass tube around a spring hatch, doorway on the west side
        // the rider comes down on the grass west of the tube (five blocks before its axis), the doorway is two steps away
        int tx = x3 + (int) Math.round(Launch.landing(Launch.PAD_UP, Launch.PAD_DRIFT, 13) + 5.0);
        int tz = 30;
        b.disc(tx, 62, tz, 6, Pal.GRASS);
        b.disc(tx, 61, tz, 5, Pal.DIRT);
        b.disc(tx, 60, tz, 4, "minecraft:stone");
        b.disc(tx, 59, tz, 2, Pal.AMETHYST);
        b.marker("isle_4", tx - 5, 63, tz);
        for (int y = 63; y <= 98; y++) {
            b.ring(tx, y, tz, 3, y % 4 == 0 ? Pal.CYAN_CONCRETE : "minecraft:light_blue_stained_glass");
            b.disc(tx, y, tz, 2, Pal.AIR);
        }
        b.disc(tx, 99, tz, 3, "minecraft:light_blue_stained_glass");
        b.fill(tx - 3, 63, tz - 1, tx - 3, 64, tz + 1, Pal.AIR);              // doorway to the west
        // the exit to the stop platform in the east is five blocks high: at the top of the throw the rider is still
        // above the platform, and a player is 1.8 blocks tall
        b.fill(tx + 2, 91, tz - 1, tx + 3, 95, tz + 1, Pal.AIR);
        b.fill(tx - 1, 90, tz - 1, tx + 2, 90, tz + 1, Pal.AIR);
        // the hatch sits at the east wall, next to the exit: the throw only has to carry the rider a few steps sideways
        b.interact("hatch_up", tx + 2, 62, tz, Pal.springHatch("up"));
        b.set(tx + 2, 62, tz, Pal.springHatch("up"));
        b.marker("tunnel_bottom", tx, 63, tz);
        // wind inside the top of the tube pushes the rider east, towards the exit opening
        b.region("wind_tube", tx - 2, 85, tz - 2, tx + 2, 97, tz + 2, "dir=east,speed=0.5");
        // sky tram stop: platform z 26..31 at y = 90 right next to the tube's east exit, track bed z 32..34
        b.fill(tx + 3, 90, 26, 97, 90, 31, "minecraft:smooth_stone");
        b.fill(tx + 3, 90, 32, 97, 90, 34, "minecraft:polished_andesite");
        for (int x = tx + 4; x <= 97; x++) {
            b.set(x, 91, 32, "minecraft:rail[shape=east_west]");
        }
        // the shelter over the platform starts two blocks behind the exit of the tube, which stays open and free
        int shelter = tx + 5;
        b.fill(shelter, 91, 27, 96, 91, 27, Pal.CONCRETE_GREY);
        for (int x = tx + 8; x <= 95; x += 6) {
            b.fill(x, 92, 27, x, 94, 27, "minecraft:polished_blackstone_wall");
            b.set(x, 95, 28, Keys.lantern(true));
        }
        b.fill(shelter, 95, 27, 96, 95, 29, "minecraft:smooth_stone_slab[type=bottom]");
        b.set(tx + 9, 93, 27, Pal.note(61, Dir.SOUTH));
        b.interact("cp_stop", shelter, 91, 29, Pal.checkpoint());
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
