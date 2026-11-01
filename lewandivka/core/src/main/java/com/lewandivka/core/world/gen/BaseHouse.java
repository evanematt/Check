package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * The central base house of Chromandivka: a witch-hat cottage (the reference image "Witch's Reach")
 * with three beds, a litter tray, cardboard boxes, empty cat places, four artifact pedestals and the
 * coloured portal alcove back to Lewandivka.
 *
 * <p>Layer 0 is the floor (replaces the surface). The house is centred at (C, C).</p>
 */
public final class BaseHouse {

    private BaseHouse() {
    }

    public static final int SIZE = 31;
    public static final int C = 15;
    public static final int HEIGHT = 30;
    private static Blueprint cache;

    public static synchronized Blueprint base() {
        if (cache == null) {
            cache = build();
        }
        return cache;
    }

    private static Blueprint build() {
        BlueprintBuilder b = new BlueprintBuilder("base", SIZE, HEIGHT, SIZE);
        b.clip(true);
        // grounds: a mossy apron and the planked floor
        b.disc(C, 0, C, 13, "minecraft:moss_block");
        b.disc(C, 0, C, 9.4, "minecraft:spruce_planks");
        b.ring(C, 0, C, 6, "minecraft:dark_oak_planks");
        // walls
        for (int y = 1; y <= 6; y++) {
            b.disc(C, y, C, 8.4, Pal.AIR);
            b.ring(C, y, C, 9.4, y % 3 == 0 ? "minecraft:stripped_birch_log[axis=y]" : "minecraft:white_terracotta");
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = C + (int) Math.round(Math.cos(a) * 9);
            int pz = C + (int) Math.round(Math.sin(a) * 9);
            for (int y = 1; y <= 6; y++) {
                b.set(px, y, pz, "minecraft:spruce_log[axis=y]");
            }
        }
        // windows between the pillars, glowing warm
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int wx = C + (int) Math.round(Math.cos(a) * 9);
            int wz = C + (int) Math.round(Math.sin(a) * 9);
            if (k == 2) {
                continue; // door side (south)
            }
            b.set(wx, 3, wz, Pal.PANE);
            b.set(wx, 4, wz, Pal.PANE);
        }
        // door on the south side
        b.fill(C - 1, 1, C + 9, C, 2, C + 9, Pal.AIR);
        b.set(C - 1, 1, C + 9, Keys.door("minecraft:spruce_door", Dir.SOUTH, false, false, false));
        b.set(C - 1, 2, C + 9, Keys.door("minecraft:spruce_door", Dir.SOUTH, true, false, false));
        b.set(C, 1, C + 9, Keys.door("minecraft:spruce_door", Dir.SOUTH, false, false, true));
        b.set(C, 2, C + 9, Keys.door("minecraft:spruce_door", Dir.SOUTH, true, false, true));
        b.set(C - 1, 1, C + 10, "minecraft:stone_brick_stairs[facing=south,half=bottom]");
        b.set(C, 1, C + 10, "minecraft:stone_brick_stairs[facing=south,half=bottom]");
        b.set(C - 2, 3, C + 10, Keys.lantern(false));
        b.set(C + 1, 3, C + 10, Keys.lantern(false));
        b.set(C - 2, 2, C + 10, "minecraft:spruce_fence").set(C + 1, 2, C + 10, "minecraft:spruce_fence");
        b.set(C - 2, 1, C + 10, "minecraft:spruce_fence").set(C + 1, 1, C + 10, "minecraft:spruce_fence");
        b.fill(C - 2, 4, C + 10, C + 1, 4, C + 10, "minecraft:spruce_slab[type=bottom]");
        // hat roof: a bent cone in purple/magenta/pink bands with a wide brim
        String[] bands = {"minecraft:purple_terracotta", "minecraft:magenta_terracotta", "minecraft:pink_terracotta", "minecraft:purple_concrete", "minecraft:magenta_concrete"};
        for (int y = 7; y <= 27; y++) {
            double t = (y - 7) / 20.0;
            double r = 12.0 - 11.4 * Math.pow(t, 0.75);
            double bend = Math.max(0, y - 13) * 0.32;
            int cx = C + (int) Math.round(bend);
            if (y == 7) {
                b.disc(C, y, C, 12.3, "minecraft:dark_oak_slab[type=bottom]");
                b.disc(C, y, C, 9.6, Pal.AIR);
                b.ring(C, y, C, 12.3, bands[0]);
                continue;
            }
            if (r < 0.8) {
                b.set(cx, y, C, bands[(y / 2) % bands.length]);
                continue;
            }
            b.disc(cx, y, C, r - 1.1, Pal.AIR);
            b.ring(cx, y, C, r, bands[(y / 2 + (y % 2)) % bands.length]);
        }
        // ceiling beam of the first roof layer so the room has a "ceiling line" and a light
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = C + (int) Math.round(Math.cos(a) * 8);
            int pz = C + (int) Math.round(Math.sin(a) * 8);
            b.set(px, 7, pz, "minecraft:spruce_log[axis=y]");
        }
        b.set(C, 9, C, Keys.lantern(true));
        b.set(C, 10, C, "minecraft:chain[axis=y]");
        b.set(C, 11, C, "minecraft:chain[axis=y]");
        b.set(C, 12, C, "minecraft:chain[axis=y]");
        b.set(C, 8, C, Pal.light(14));
        b.set(C - 6, 5, C + 3, Pal.light(12));
        b.set(C + 6, 5, C - 3, Pal.light(12));
        // roof windows (a few round panes in the cone)
        b.set(C - 4, 12, C + 8, Pal.GLASS);
        // ---- interior: three beds along the west wall (heads to the wall)
        String[] bedColors = {"minecraft:purple_bed", "minecraft:magenta_bed", "minecraft:cyan_bed"};
        for (int i = 0; i < 3; i++) {
            int z = C - 3 + i * 3;
            b.set(C - 7, 1, z, Keys.bed(bedColors[i], Dir.WEST, true));
            b.set(C - 6, 1, z, Keys.bed(bedColors[i], Dir.WEST, false));
            b.marker("bed_" + (i + 1), C - 5, 1, z);
        }
        // litter tray, north-east corner
        b.fill(C + 4, 1, C - 6, C + 6, 1, C - 5, "minecraft:cobblestone_slab[type=bottom]");
        b.fill(C + 5, 1, C - 6, C + 5, 1, C - 5, "minecraft:sand");
        b.marker("litter", C + 5, 1, C - 5);
        // cardboard boxes
        b.set(C + 6, 1, C + 1, Pal.box(false));
        b.set(C + 6, 1, C + 3, Pal.box(false));
        b.set(C + 6, 2, C + 1, Pal.box(true));
        // empty cat places with their portraits (framed notes 20 and 21)
        b.set(C - 3, 1, C + 7, "minecraft:spruce_slab[type=bottom]");
        b.set(C - 3, 2, C + 7, "minecraft:black_carpet");
        b.set(C - 3, 4, C + 8, Pal.note(20, Dir.NORTH));
        b.set(C + 1, 1, C + 7, "minecraft:spruce_slab[type=bottom]");
        b.set(C + 1, 2, C + 7, "minecraft:gray_carpet");
        b.set(C + 1, 4, C + 8, Pal.note(21, Dir.NORTH));
        b.marker("cat_spot_chinazik", C - 3, 2, C + 6);
        b.marker("cat_spot_metadonna", C + 1, 2, C + 6);
        // storage along the north wall
        b.set(C - 7, 1, C - 5, "minecraft:chest[facing=east]");
        b.set(C - 7, 1, C - 6, "minecraft:chest[facing=east]");
        b.set(C - 6, 1, C - 7, Keys.barrel(Dir.SOUTH));
        // four pedestals in an arc on the north side
        String[] kinds = {"kettle", "package", "composter", "token"};
        for (int i = 0; i < 4; i++) {
            int px = C - 4 + i * 3;
            int pz = C - 7;
            b.set(px, 1, pz, Pal.pedestal(kinds[i]));
            b.marker("pedestal_" + kinds[i], px, 1, pz);
        }
        // the coloured portal alcove on the east wall: frame 5 wide, 5 tall; plane appears when activated
        for (int z = C - 2; z <= C + 2; z++) {
            for (int y = 1; y <= 5; y++) {
                boolean edge = z == C - 2 || z == C + 2 || y == 5;
                b.set(C + 8, y, z, edge ? Pal.PORTAL_FRAME : Pal.AIR);
            }
        }
        b.set(C + 7, 1, C, "minecraft:purple_carpet");
        b.region("portal_plane", C + 8, 1, C - 1, C + 8, 4, C + 1, "plane=" + Pal.portal('z'));
        b.marker("portal", C + 7, 1, C);
        // arrival and checkpoint
        b.interact("checkpoint", C, 1, C - 3, Pal.checkpoint());
        b.marker("spawn", C, 1, C + 2);
        b.marker("door", C, 1, C + 9);
        // mushrooms and fireflies around the house
        for (int i = 0; i < 9; i++) {
            double a = i * 0.7 + 0.3;
            int mx = C + (int) Math.round(Math.cos(a) * 11.5);
            int mz = C + (int) Math.round(Math.sin(a) * 11.5);
            if (Math.abs(mx - C) < 3 && mz > C + 8) {
                continue;
            }
            b.set(mx, 1, mz, "minecraft:red_mushroom");
            if (i % 3 == 0) {
                b.set(mx, 1, mz, "minecraft:allium");
            }
        }
        b.region("body", C - 10, 0, C - 10, C + 10, 7, C + 10);
        return b.build();
    }
}
