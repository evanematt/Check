package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Pal;

/**
 * The approach to the Head of District Tower: a grey traversal exam that uses every ability.
 * Players walk north (towards smaller z):
 * <ol>
 *   <li>start plaza with a checkpoint (z 108..119)</li>
 *   <li>Dash doors: three doors that open only for a dashing player (z 88..107)</li>
 *   <li>a "normal wall" that the cats can open (z 78..87)</li>
 *   <li>the spring shaft with a wind updraft to the glider deck (z 68..77)</li>
 *   <li>the glider chasm, 40 blocks wide, with a pool below (z 20..59)</li>
 *   <li>far platform, checkpoint and the tower gate (z 0..19)</li>
 * </ol>
 * Layer {@link #S} coincides with the ground surface.
 */
public final class TowerApproach {

    private TowerApproach() {
    }

    public static final int SX = 40;
    public static final int SY = 60;
    public static final int SZ = 120;
    public static final int S = 16;
    public static final int ORIGIN_X = -20;
    public static final int ORIGIN_Y = 72 - S;
    public static final int ORIGIN_Z = -167;
    /** Depth of the gorge below the walkway level. */
    public static final int GORGE = 13;

    private static Blueprint cache;

    public static synchronized Blueprint blueprint() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("tower_approach", SX, SY, SZ);
        // the whole footprint is a gorge: only the built walkways can be walked, everything else is a fall
        b.fill(0, S - GORGE, 0, SX - 1, S + 1, SZ - 1, Pal.AIR);
        plaza(b);
        dashCorridor(b);
        hiddenPassage(b);
        springShaft(b);
        chasm(b);
        farSide(b);
        return b.build();
    }

    private static void floor(BlueprintBuilder b, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                b.set(x, y, z, ((x * 5 + z * 3) % 7 == 0) ? Pal.CONCRETE_DARK : Pal.CONCRETE_GREY);
            }
        }
    }

    private static void plaza(BlueprintBuilder b) {
        floor(b, 10, 108, 30, 119, S);
        for (int x = 10; x <= 30; x += 4) {
            b.set(x, S + 1, 108, "minecraft:polished_blackstone_wall");
            b.set(x, S + 2, 108, Keys.lantern(false));
        }
        b.set(20, S + 2, 110, Pal.note(70, Dir.NORTH));
        b.interact("cp_start", 24, S + 1, 112, Pal.checkpoint());
        b.marker("approach_start", 20, S + 1, 114);
    }

    private static void dashCorridor(BlueprintBuilder b) {
        // 5-wide concrete corridor x 18..22 with three dash doors; each door is 5 wide, 3 high
        floor(b, 16, 88, 24, 107, S);
        b.walls(17, S + 1, 88, 23, S + 5, 107, Pal.CONCRETE_GREY);
        b.fill(16, S + 6, 88, 24, S + 6, 107, Pal.CONCRETE_DARK);
        b.fill(18, S + 1, 88, 22, S + 5, 107, Pal.AIR);
        int[] zs = {102, 96, 90};
        for (int i = 0; i < 3; i++) {
            b.gate("dash_door_" + (i + 1), 18, S + 1, zs[i], 22, S + 3, zs[i], Pal.dashDoor(), true);
            b.fill(18, S + 4, zs[i], 22, S + 5, zs[i], Pal.CONCRETE_DARK);
            b.set(20, S + 4, zs[i] + 1, Pal.note(71 + i, Dir.SOUTH));
            b.set(18, S + 5, zs[i] + 1, Pal.light(12));
            b.set(22, S + 5, zs[i] + 1, Pal.light(12));
        }
        b.fill(18, S + 1, 107, 22, S + 4, 107, Pal.AIR);
        b.interact("cp_dash", 20, S + 1, 93, Pal.checkpoint());
        b.marker("dash_start", 20, S + 1, 105);
        b.marker("dash_end", 20, S + 1, 88);
    }

    private static void hiddenPassage(BlueprintBuilder b) {
        // the corridor ends in a plain wall; behind it the passage continues
        floor(b, 16, 78, 24, 87, S);
        b.walls(17, S + 1, 78, 23, S + 5, 87, Pal.CONCRETE_GREY);
        b.fill(16, S + 6, 78, 24, S + 6, 87, Pal.CONCRETE_DARK);
        b.fill(18, S + 1, 78, 22, S + 5, 87, Pal.AIR);
        b.gate("hidden_wall", 18, S + 1, 83, 22, S + 3, 83, Pal.CONCRETE_GREY, true);
        b.fill(18, S + 4, 83, 22, S + 5, 83, Pal.CONCRETE_GREY);
        b.set(20, S + 4, 84, Pal.note(74, Dir.SOUTH));
        b.set(18, S + 5, 85, Pal.light(10));
        b.set(18, S + 5, 80, Pal.light(10));
        b.marker("cat_spot", 20, S + 1, 85);
        b.marker("hidden_front", 20, S + 1, 84);
        b.marker("hidden_back", 20, S + 1, 82);
        b.interact("cp_hidden", 21, S + 1, 80, Pal.checkpoint());
    }

    private static void springShaft(BlueprintBuilder b) {
        // chamber x 16..24, z 68..77; shaft 5 x 5 rising 26 blocks; exit onto the glider deck to the north
        floor(b, 16, 68, 24, 77, S);
        b.walls(15, S + 1, 67, 25, S + 30, 78, Pal.CONCRETE_GREY);
        b.fill(18, S + 1, 69, 22, S + 29, 76, Pal.AIR);
        b.fill(18, S + 1, 78, 22, S + 3, 78, Pal.AIR);
        b.fill(16, S + 30, 67, 24, S + 30, 78, Pal.CONCRETE_DARK);
        b.interact("hatch", 20, S, 73, Pal.springHatch("up"));
        b.marker("shaft_bottom", 20, S + 1, 73);
        // an opening on the north side near the top leads to the glider deck
        b.fill(19, S + 25, 67, 21, S + 28, 68, Pal.AIR);
        b.fill(19, S + 24, 66, 21, S + 24, 68, Pal.CONCRETE_GREY);
        b.region("wind_shaft", 18, S + 20, 69, 22, S + 29, 76, "dir=north,speed=0.5");
        for (int y = S + 4; y <= S + 28; y += 6) {
            b.set(18, y, 70, Pal.light(12));
            b.set(22, y, 75, Pal.light(12));
        }
        b.interact("cp_shaft", 17, S + 1, 72, Pal.checkpoint());
    }

    private static void chasm(BlueprintBuilder b) {
        // glider deck on top of the shaft: y = S + 24, z 60..66
        floor(b, 14, 60, 26, 66, S + 24);
        for (int x = 14; x <= 26; x++) {
            b.set(x, S + 25, 66, "minecraft:polished_blackstone_wall");
        }
        for (int z = 60; z <= 66; z++) {
            b.set(14, S + 25, z, "minecraft:polished_blackstone_wall");
            b.set(26, S + 25, z, "minecraft:polished_blackstone_wall");
        }
        b.fill(19, S + 25, 66, 21, S + 25, 66, Pal.AIR);
        b.marker("glide_start", 20, S + 25, 62);
        b.interact("cp_deck", 17, S + 25, 63, Pal.checkpoint());
        b.set(20, S + 26, 60, Pal.note(75, Dir.SOUTH));
        // the chasm z 20..59: a deep pool far below catches everyone (and the fall zone teleports them back)
        b.fill(8, S - 12, 20, 32, S - 12, 59, Pal.CONCRETE_DARK);
        b.fill(8, S - 11, 20, 32, S - 3, 59, Pal.WATER);
        b.region("chasm", 8, S - 12, 20, 32, S + 24, 59);
        b.region("fall_zone", 0, S - GORGE, 0, SX - 1, S - 2, SZ - 1);
        b.marker("pool_return", 20, S + 25, 64);
    }

    private static void farSide(BlueprintBuilder b) {
        // landing platform 12 higher than the ground in front of the tower, built on a solid plateau
        b.fill(12, S + 1, 14, 28, S + 11, 19, Pal.CONCRETE_DARK);
        floor(b, 12, 14, 28, 19, S + 12);
        b.interact("cp_landing", 24, S + 13, 17, Pal.checkpoint());
        b.marker("glide_end", 20, S + 13, 17);
        // stairs down to the gate: twelve steps, descending northwards (stairs ascend towards the south)
        for (int i = 0; i < 12; i++) {
            int z = 13 - i;
            int y = S + 11 - i;
            for (int x = 18; x <= 22; x++) {
                b.set(x, y, z, Keys.stairs("minecraft:stone_brick_stairs", Dir.SOUTH, false));
                if (y > S + 1) {
                    b.fill(x, S + 1, z, x, y - 1, z, Pal.CONCRETE_DARK);
                }
            }
            b.set(17, y, z, "minecraft:polished_blackstone_wall");
            b.set(23, y, z, "minecraft:polished_blackstone_wall");
        }
        floor(b, 12, 0, 28, 1, S);
        b.interact("cp_tower", 24, S + 1, 1, Pal.checkpoint());
        b.marker("tower_gate", 20, S + 1, 1);
        b.marker("tower_approach_end", 20, S + 1, 0);
    }
}
