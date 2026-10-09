package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.BlueprintBuilder;
import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.world.Noise;
import com.lewandivka.core.world.Pal;

/**
 * A panel apartment block you can walk into. It is a row of sections; each section has its own entrance, a stairwell in the
 * middle and four flats on every floor (two on each side of the stairwell, one at the front and one at the back), so a block of
 * three sections and five floors has sixty flats, every one of them furnished.
 *
 * <pre>
 *   x (along the facade):  0 | 1 .. 5 | 6 | 7 .. 10 | 11 | 12 .. 16 | 17 = 0 of the next section
 *                           wall  left   wall stairwell wall  right      party wall
 *   z (from the facade):   0 facade | 1 .. 5 front flats | 6 partition | 7 .. 11 back flats | 12 facade
 *   y (in storeys of four): the floor layer, three layers of room; the floor of one storey is the ceiling of the one below
 * </pre>
 *
 * <p>The stairwell has two flights of two stairs side by side with a landing between them at the back, a U that brings the
 * visitor back to the front of the stairwell on every floor, where the doors of the flats are (z = 4 and z = 8). The stairs go
 * on up to the roof, where a little hut covers the exit.</p>
 */
final class PanelBlock {

    private PanelBlock() {
    }

    static final int PITCH = 17;
    static final int DEPTH = 13;
    static final int STOREY = 4;
    /** Margin in front of the facade for the canopies and the sills (as in {@link Buildings}). */
    static final int FRONT = 3;

    static final int CORE_FROM = 7;
    static final int CORE_TO = 10;
    static final int WALL_LEFT = 6;
    static final int WALL_RIGHT = 11;
    static final int PARTITION = 6;
    /** The rows of the doors of the flats (front flats and back flats). */
    static final int DOOR_FRONT = 4;
    static final int DOOR_BACK = 8;
    /** The floor row in front of the stairs (the landing); the flights are on the rows after it. */
    static final int LANDING = 8;
    private static final String STAIRS = "minecraft:polished_andesite_stairs";
    private static final String SLAB_TOP = "minecraft:polished_andesite_slab[type=top]";

    static int length(int sections) {
        return PITCH * sections + 1;
    }

    static int roofLayer(int floors) {
        return STOREY * floors;
    }

    /** The plot of one of the four flats of a section: {@code right} is the flat right of the stairwell, {@code back} the one at the back. */
    static Plot flat(int sectionX, boolean right, boolean back) {
        Dir along = right ? Dir.WEST : Dir.EAST;
        Dir across = back ? Dir.NORTH : Dir.SOUTH;
        return new Plot(sectionX + (right ? 16 : 1), back ? 11 : 1, along, across);
    }

    static Blueprint build(int sections, int floors, Buildings.Theme t, int turns, long seed) {
        final int length = length(sections);
        final int roof = roofLayer(floors);
        final int height = roof + 8;
        final int sx = length;
        final int sz = DEPTH + FRONT + 1;
        boolean odd = (turns & 1) == 1;
        BlueprintBuilder b = new BlueprintBuilder("panel_block", odd ? sz : sx, height, odd ? sx : sz);
        b.orient(turns, sx, sz);
        b.at(0, 0, FRONT);

        // ---- the shell: panels on the outside, concrete in the party walls, rooms of air inside
        for (int y = 1; y <= roof; y++) {
            for (int x = 0; x < length; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    boolean outer = x == 0 || x == length - 1 || z == 0 || z == DEPTH - 1;
                    b.set(x, y, z, outer ? Buildings.panelWall(t, seed, x, y, z) : y % STOREY == 0 && y < roof ? Pal.CONCRETE_GREY : Keys.AIR);
                }
            }
        }
        for (int x = 0; x < length; x++) {
            for (int z = 0; z < DEPTH; z++) {
                b.set(x, 0, z, ((x * 7 + z * 3) % 5 == 0) ? Pal.BRICKS_MOSSY : Pal.BRICKS);
            }
        }
        b.fill(0, roof, 0, length - 1, roof, DEPTH - 1, Pal.CONCRETE_DARK);
        for (int e = 1; e < sections; e++) {
            b.fill(PITCH * e, 1, 1, PITCH * e, roof - 1, DEPTH - 2, Pal.CONCRETE_GREY);
        }

        for (int e = 0; e < sections; e++) {
            int u0 = PITCH * e;
            for (int k = 0; k < floors; k++) {
                int f = STOREY * k;
                b.fill(u0 + 1, f + 1, PARTITION, u0 + 5, f + 3, PARTITION, Pal.CONCRETE_GREY);
                b.fill(u0 + 12, f + 1, PARTITION, u0 + 16, f + 3, PARTITION, Pal.CONCRETE_GREY);
                stairwell(b, t, u0, k, floors, seed);
                for (int side = 0; side < 4; side++) {
                    flat(b, u0, k, side, seed);
                }
            }
            roofHut(b, t, u0, roof);
            entrance(b, t, u0, seed);
        }
        facades(b, t, sections, floors, seed);
        roof(b, t, sections, length, roof, seed);
        // spots the development tools (and the pictures of the client test) go to
        b.marker("entrance", 8, 1, 2);
        Plot front = flat(0, false, false);
        b.marker("flat_a", front.x(2, 2), STOREY + 1, front.z(2, 2));
        Plot back = flat(0, true, true);
        b.marker("flat_b", back.x(2, 2), 1, back.z(2, 2));
        b.marker("landing", 8, 2 * STOREY + 1, LANDING - 2);
        b.marker("roof", 9, roof + 1, 5);
        b.region("body", 0, 0, 0, length - 1, roof + 5, DEPTH - 1);
        return b.build();
    }

    // ------------------------------------------------------------------ the stairwell

    /** One storey of the stairwell of the section whose party wall is at {@code u0}: the floor, the doors, the stairs up. */
    private static void stairwell(BlueprintBuilder b, Buildings.Theme t, int u0, int k, int floors, long seed) {
        int f = STOREY * k;
        int y = f + 1;
        // the floor of the lobby / the landings, with a hole where the flights of the storey below come up
        for (int u = CORE_FROM; u <= CORE_TO; u++) {
            for (int v = 1; v <= DEPTH - 2; v++) {
                boolean hole = k > 0 && v >= LANDING + 2;
                boolean lastStair = k > 0 && v == LANDING + 1 && u >= CORE_FROM + 2;
                // over the first stair of the left flight the floor is a slab in the upper half: the head of the one who climbs
                // the second stair needs the room, and the first stair of the next flight stands on the slab
                boolean overFirstStair = k > 0 && v == LANDING + 1 && u < CORE_FROM + 2;
                String floor = (u + v) % 7 == 0 ? Pal.BRICKS : Pal.POL_ANDESITE;
                b.set(u0 + u, f, v, hole ? Keys.AIR : lastStair ? Keys.stairs(STAIRS, Dir.NORTH, false) : overFirstStair ? SLAB_TOP : floor);
            }
        }
        // the walls of the stairwell with the doors of the four flats
        for (int w : new int[] {WALL_LEFT, WALL_RIGHT}) {
            b.fill(u0 + w, y, 1, u0 + w, y + 2, DEPTH - 2, Pal.CONCRETE_GREY);
            for (int v : new int[] {DOOR_FRONT, DOOR_BACK}) {
                long h = Noise.hash(seed, u0 * 31 + w, k * 17 + v);
                String door = Furnish.door(Furnish.WOODS[(int) Math.floorMod(h, (long) Furnish.WOODS.length)]);
                Dir facing = w == WALL_LEFT ? Dir.WEST : Dir.EAST;
                b.set(u0 + w, y, v, Keys.door(door, facing, false, false, false));
                b.set(u0 + w, y + 1, v, Keys.door(door, facing, true, false, false));
            }
        }
        // the flights: two stairs up on the left, the landing, two stairs back on the right
        // (on the ground floor the space under the flights is solid; higher up it is the way the flights below go)
        boolean solid = k == 0;
        for (int u = CORE_FROM; u <= CORE_FROM + 1; u++) {
            b.set(u0 + u, y, LANDING + 1, Keys.stairs(STAIRS, Dir.SOUTH, false));
            b.set(u0 + u, y + 1, LANDING + 2, Keys.stairs(STAIRS, Dir.SOUTH, false));
            if (solid) {
                b.set(u0 + u, y, LANDING + 2, Pal.CONCRETE_GREY);
            }
        }
        for (int u = CORE_FROM + 2; u <= CORE_TO; u++) {
            b.set(u0 + u, y + 2, LANDING + 2, Keys.stairs(STAIRS, Dir.NORTH, false));
            b.set(u0 + u, y + 3, LANDING + 1, Keys.stairs(STAIRS, Dir.NORTH, false));
            if (solid) {
                b.fill(u0 + u, y, LANDING + 2, u0 + u, y + 1, LANDING + 2, Pal.CONCRETE_GREY);
                b.fill(u0 + u, y, LANDING + 1, u0 + u, y + 2, LANDING + 1, Pal.CONCRETE_GREY);
            }
        }
        for (int u = CORE_FROM; u <= CORE_TO; u++) {
            b.set(u0 + u, y + 1, LANDING + 3, Pal.POL_ANDESITE);
            if (solid) {
                b.set(u0 + u, y, LANDING + 3, Pal.CONCRETE_GREY);
            }
        }
        // light and the plants of the landing
        b.set(u0 + 8, y + 2, 3, Keys.lantern(true));
        b.set(u0 + 9, y + 2, LANDING - 1, Keys.lantern(true));
        if (k == 0) {
            lobby(b, u0, seed);
        } else {
            b.set(u0 + CORE_FROM, y, 2, Furnish.POTS[(int) Math.floorMod(Noise.hash(seed, u0, k), (long) Furnish.POTS.length)]);
        }
    }

    /** The ground floor of the stairwell: mailboxes, a bench, a plant and a note on the wall. */
    private static void lobby(BlueprintBuilder b, int u0, long seed) {
        int y = 1;
        // the mailboxes: a block of iron flaps on the wall of the left flats
        for (int v = 2; v <= 4; v++) {
            for (int yy = 2; yy <= 3; yy++) {
                b.set(u0 + CORE_FROM, yy, v, Keys.of("minecraft:iron_trapdoor", "facing", "east", "half", "bottom", "open", "true"));
            }
        }
        b.set(u0 + CORE_TO, y, 2, Keys.stairs("minecraft:spruce_stairs", Dir.EAST, false));
        b.set(u0 + CORE_TO, y, 3, Keys.stairs("minecraft:spruce_stairs", Dir.EAST, false));
        b.set(u0 + CORE_TO, y, 6, Furnish.POTS[0]);
        b.set(u0 + CORE_TO, y + 1, 5, Pal.note(1, Dir.WEST));
        b.set(u0 + CORE_FROM, y, 1, "minecraft:gray_carpet");
        b.set(u0 + CORE_FROM + 1, y, 1, "minecraft:gray_carpet");
        b.set(u0 + CORE_FROM + 2, y, 1, "minecraft:gray_carpet");
        b.set(u0 + CORE_TO, y, 1, "minecraft:gray_carpet");
    }

    // ------------------------------------------------------------------ the flats

    private static void flat(BlueprintBuilder b, int u0, int k, int side, long seed) {
        boolean right = (side & 1) == 1;
        boolean back = (side & 2) == 2;
        long h = Noise.hash(seed, u0 * 131 + k, side);
        String wood = Furnish.WOODS[(int) Math.floorMod(h, (long) Furnish.WOODS.length)];
        boolean ruin = (h >>> 40) % 10 < 2;
        Furnish.Style style = new Furnish.Style(
                wood, Furnish.planks(wood), Furnish.WALLS[(int) Math.floorMod(h >>> 8, (long) Furnish.WALLS.length)],
                Furnish.BEDS[(int) Math.floorMod(h >>> 16, (long) Furnish.BEDS.length)],
                Furnish.SOFAS[(int) Math.floorMod(h >>> 24, (long) Furnish.SOFAS.length)],
                Furnish.RUGS[(int) Math.floorMod(h >>> 32, (long) Furnish.RUGS.length)],
                !ruin && (h >>> 48) % 10 < 7, ruin ? 1 : 0);
        String[] rows = RoomKit.FLATS[(int) Math.floorMod(h >>> 52, (long) RoomKit.FLATS.length)];
        RoomKit.place(b, flat(u0, right, back), STOREY * k, RoomKit.parse(rows), style, h);
    }

    // ------------------------------------------------------------------ the entrance

    private static void entrance(BlueprintBuilder b, Buildings.Theme t, int u0, long seed) {
        int cx = u0 + 8;
        b.fill(cx - 1, 1, 0, cx + 2, 3, 0, t.frame);
        b.fill(cx, 1, 0, cx + 1, 2, 0, Keys.AIR);
        b.set(cx, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(cx, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        b.set(cx + 1, 1, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, true));
        b.set(cx + 1, 2, 0, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, true));
        // canopy with lanterns and two posts
        b.fill(cx - 1, 4, -1, cx + 2, 4, -2, "minecraft:stone_brick_slab[type=bottom]");
        for (int yy = 1; yy <= 3; yy++) {
            b.set(cx - 1, yy, -2, "minecraft:stone_brick_wall");
            b.set(cx + 2, yy, -2, "minecraft:stone_brick_wall");
        }
        b.set(cx, 3, -1, Keys.lantern(true));
        b.set(cx + 1, 3, -1, Keys.lantern(true));
        // steps
        b.fill(cx, 0, -1, cx + 1, 0, -1, Pal.BRICKS);
        b.fill(cx - 1, 0, -2, cx + 2, 0, -2, "minecraft:stone_brick_slab[type=bottom]");
    }

    // ------------------------------------------------------------------ windows

    private static void facades(BlueprintBuilder b, Buildings.Theme t, int sections, int floors, long seed) {
        int length = length(sections);
        for (int e = 0; e < sections; e++) {
            int u0 = PITCH * e;
            for (int k = 0; k < floors; k++) {
                int f = STOREY * k;
                for (int side = 0; side < 4; side++) {
                    boolean right = (side & 1) == 1;
                    boolean back = (side & 2) == 2;
                    long h = Noise.hash(seed + 7, u0 * 131 + k, side);
                    // three panes between two piers, at the outside wall of the flat
                    int zWall = back ? DEPTH - 1 : 0;
                    int out = back ? 1 : -1;
                    int first = right ? u0 + 13 : u0 + 2;
                    int pierLeft = right ? u0 + 16 : u0 + 1;
                    int pierRight = right ? u0 + 12 : u0 + 5;
                    window(b, t, first, 3, pierLeft, pierRight, f, zWall, out, h);
                    // the flats on the courtyard side of the upper floors have a balcony with a door in the middle of the window
                    if (!back && k > 0 && (h >>> 8) % 100 < 38) {
                        balcony(b, first + 1, f, h);
                    }
                }
                // the window of the stairwell above the entrance, and at the back at the level of the landing
                if (k > 0) {
                    window(b, t, u0 + 8, 2, u0 + 7, u0 + 10, f, 0, -1, Noise.hash(seed + 9, u0, k));
                }
                int wy = f + 3;
                b.fill(u0 + 8, wy, DEPTH - 1, u0 + 9, Math.min(wy + 1, STOREY * floors - 1), DEPTH - 1, Pal.PANE);
            }
        }
        // windows in the end walls: one for the front flat and one for the back flat
        for (int k = 0; k < floors; k++) {
            int f = STOREY * k;
            for (int x : new int[] {0, length - 1}) {
                b.fill(x, f + 2, 2, x, f + 3, 3, Pal.PANE);
                b.fill(x, f + 2, DEPTH - 4, x, f + 3, DEPTH - 3, Pal.PANE);
            }
        }
    }

    /** A window of {@code width} panes (two layers high) in an outside wall, between two white piers, with a sill and a flower pot. */
    private static void window(BlueprintBuilder b, Buildings.Theme t, int first, int width, int pierA, int pierB, int f, int zWall, int out, long h) {
        for (int dy = 2; dy <= 3; dy++) {
            b.set(pierA, f + dy, zWall, t.frame);
            b.set(pierB, f + dy, zWall, t.frame);
        }
        for (int i = 0; i < width; i++) {
            int x = first + i;
            for (int dy = 2; dy <= 3; dy++) {
                b.set(x, f + dy, zWall, Pal.PANE);
            }
            b.set(x, f + 1, zWall + out, "minecraft:stone_brick_slab[type=top]");
        }
        if ((h & 3) == 0) {
            b.set(first, f + 2, zWall + out, "minecraft:potted_red_tulip");
        } else if ((h & 3) == 1) {
            b.set(first + (width > 1 ? 1 : 0), f + 2, zWall + out, "minecraft:potted_fern");
        }
    }

    /** A balcony in front of the front wall: a door instead of the middle of the window, a slab, a railing of bars. */
    private static void balcony(BlueprintBuilder b, int cx, int f, long h) {
        String door = Furnish.door(Furnish.WOODS[(int) Math.floorMod(h >>> 20, (long) Furnish.WOODS.length)]);
        b.set(cx, f + 1, 0, Keys.door(door, Dir.NORTH, false, false, false));
        b.set(cx, f + 2, 0, Keys.door(door, Dir.NORTH, true, false, false));
        b.fill(cx - 1, f, -2, cx + 1, f, -1, Pal.CONCRETE_GREY);
        b.fill(cx - 1, f + 1, -1, cx + 1, f + 1, -1, Keys.AIR);
        b.fill(cx - 1, f + 1, -2, cx + 1, f + 1, -2, Pal.BARS);
        b.set(cx - 1, f + 1, -1, Pal.BARS);
        b.set(cx + 1, f + 1, -1, Pal.BARS);
        if ((h >>> 28) % 3 == 0) {
            b.set(cx, f + 1, -1, Furnish.POTS[(int) Math.floorMod(h >>> 32, (long) Furnish.POTS.length)]);
        }
    }

    // ------------------------------------------------------------------ the roof

    /** The hut over the exit of the stairs: walls, a double door to the roof, a ceiling and a lamp. */
    private static void roofHut(BlueprintBuilder b, Buildings.Theme t, int u0, int roof) {
        int y = roof + 1;
        int x1 = u0 + WALL_LEFT;
        int x2 = u0 + WALL_RIGHT;
        b.fill(x1, y, LANDING, x2, y + 2, DEPTH - 1, Keys.AIR);
        b.walls(x1, y, LANDING, x2, y + 2, DEPTH - 1, Pal.BRICKS);
        b.fill(x1, y + 3, LANDING, x2, y + 3, DEPTH - 1, Pal.CONCRETE_DARK);
        b.fill(x1, y + 4, LANDING, x2, y + 4, DEPTH - 1, "minecraft:stone_brick_slab[type=bottom]");
        // the way out: the two cells above the exit of the right flight
        b.fill(u0 + 9, y, LANDING, u0 + 10, y + 1, LANDING, Keys.AIR);
        b.set(u0 + 9, y, LANDING, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, false));
        b.set(u0 + 9, y + 1, LANDING, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, false));
        b.set(u0 + 10, y, LANDING, Keys.door("minecraft:dark_oak_door", Dir.NORTH, false, false, true));
        b.set(u0 + 10, y + 1, LANDING, Keys.door("minecraft:dark_oak_door", Dir.NORTH, true, false, true));
        b.set(u0 + 8, y + 2, LANDING + 2, Keys.lantern(true));
        // the stairs of the last flight end in the roof layer, and the opening above the landing is open to the hut
        for (int u = CORE_FROM; u <= CORE_TO; u++) {
            for (int v = LANDING + 2; v <= DEPTH - 2; v++) {
                b.set(u0 + u, roof, v, Keys.AIR);
            }
        }
        // (the last stairs of the right flight stand in the roof layer itself)
        for (int u = CORE_FROM + 2; u <= CORE_TO; u++) {
            b.set(u0 + u, roof, LANDING + 1, Keys.stairs(STAIRS, Dir.NORTH, false));
        }
        for (int u = CORE_FROM; u <= CORE_FROM + 1; u++) {
            b.set(u0 + u, roof, LANDING + 1, SLAB_TOP);
        }
    }

    /** The parapet, the ventilation shafts and the aerial on the roof (the huts are drawn with the sections). */
    private static void roof(BlueprintBuilder b, Buildings.Theme t, int sections, int length, int roof, long seed) {
        int y = roof + 1;
        for (int x = 0; x < length; x++) {
            b.set(x, y, 0, "minecraft:stone_brick_slab[type=bottom]");
            b.set(x, y, DEPTH - 1, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int z = 1; z < DEPTH - 1; z++) {
            b.set(0, y, z, "minecraft:stone_brick_slab[type=bottom]");
            b.set(length - 1, y, z, "minecraft:stone_brick_slab[type=bottom]");
        }
        for (int i = 0; i < Math.max(2, length / 14); i++) {
            int vx = 3 + Noise.range(seed, i, 1, length - 6);
            if (insideHut(vx, sections) || insideHut(vx + 1, sections)) {
                continue;
            }
            b.fill(vx, y, 3, vx + 1, y + 1, 4, Pal.BRICKS);
            b.set(vx, y + 2, 3, "minecraft:stone_brick_wall");
        }
        int ax = 3 + Noise.range(seed, 9, 9, length - 6);
        if (!insideHut(ax, sections)) {
            b.set(ax, y, 6, "minecraft:iron_bars").set(ax, y + 1, 6, "minecraft:iron_bars");
            b.set(ax, y + 2, 6, "minecraft:lightning_rod[facing=up]");
        }
    }

    private static boolean insideHut(int x, int sections) {
        int u = x % PITCH;
        return u >= WALL_LEFT && u <= WALL_RIGHT;
    }
}
