package ua.lewandivka.world;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.registry.ModBlocks;

/** Архітектура міста: котедж, панельки з балконами, ринок, парк, транспорт. */
final class CityArch {
    private static BlockState s(Block b) {
        return b.getDefaultState();
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.getDefaultState().with(PillarBlock.AXIS, axis);
    }

    private static BlockState leaves(Block b) {
        return b.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
    }

    private static void door(Builder b, int x, int y, int z, Block door, Direction facing) {
        b.set(x, y, z, door.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(x, y + 1, z, door.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void shutter(Builder b, int x, int y, int z, Direction facing) {
        b.set(x, y, z, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(TrapdoorBlock.FACING, facing).with(TrapdoorBlock.OPEN, true));
    }

    // ------------------------------------------------------------------ котедж

    /** Наш будинок: кам'яний цоколь, біла штукатурка з дерев'яним каркасом, блакитний другий поверх, черепичний дах з димарем. */
    static void cottage(Builder b, LewState st) {
        b.fill(-9, 0, -9, 9, 0, 8, s(Blocks.GRASS_BLOCK));
        b.fill(-9, 1, -9, 9, 18, 8, s(Blocks.AIR));
        // Садок з парканом і стежкою.
        for (int x = -9; x <= 9; x++) {
            for (int z : new int[]{-9, 8}) {
                boolean post = x % 3 == 0 || Math.abs(x) == 9;
                b.set(x, 1, z, s(post ? Blocks.STONE_BRICK_WALL : Blocks.IRON_BARS));
                b.set(x, 2, z, s(post ? Blocks.STONE_BRICK_WALL : Blocks.IRON_BARS));
            }
        }
        for (int z = -9; z <= 8; z++) {
            for (int x : new int[]{-9, 9}) {
                boolean post = z % 3 == 0 || z == -9 || z == 8;
                b.set(x, 1, z, s(post ? Blocks.STONE_BRICK_WALL : Blocks.IRON_BARS));
                b.set(x, 2, z, s(post ? Blocks.STONE_BRICK_WALL : Blocks.IRON_BARS));
            }
        }
        b.fill(-1, 1, -9, 1, 2, -9, s(Blocks.AIR));
        b.fill(-1, 0, -9, 1, 0, -8, s(Blocks.DIRT_PATH));
        Block[] flowers = {Blocks.POPPY, Blocks.ALLIUM, Blocks.CORNFLOWER, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.LILY_OF_THE_VALLEY};
        int fi = 0;
        for (int x = -8; x <= 8; x++) {
            if (Math.abs(x) <= 2) {
                continue;
            }
            b.set(x, 0, -8, s(Blocks.COARSE_DIRT));
            b.set(x, 1, -8, s(flowers[fi++ % flowers.length]));
        }
        b.set(-7, 1, 6, s(Blocks.FLOWERING_AZALEA));
        b.set(7, 1, 6, s(Blocks.AZALEA));

        // Цоколь і підлога.
        b.fill(-6, 0, -5, 6, 1, 5, s(Blocks.STONE_BRICKS));
        b.fill(-5, 1, -4, 5, 1, 4, s(Blocks.SPRUCE_PLANKS));
        // Перший поверх: штукатурка + каркас.
        b.walls(-6, 2, -5, 6, 5, 5, s(Blocks.WHITE_TERRACOTTA));
        b.walls(-6, 6, -5, 6, 6, 5, s(Blocks.SPRUCE_PLANKS));
        for (int x = -6; x <= 6; x++) {
            b.set(x, 6, -5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            b.set(x, 6, 5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        }
        for (int z = -5; z <= 5; z++) {
            b.set(-6, 6, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
            b.set(6, 6, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
        }
        // Другий поверх: блакитна штукатурка.
        b.walls(-6, 7, -5, 6, 9, 5, s(Blocks.LIGHT_BLUE_TERRACOTTA));
        b.walls(-6, 10, -5, 6, 10, 5, s(Blocks.SPRUCE_PLANKS));
        b.fill(-5, 6, -4, 5, 6, 4, s(Blocks.SPRUCE_PLANKS));
        b.fill(-5, 10, -4, 5, 10, 4, s(Blocks.SPRUCE_PLANKS));
        for (int[] c : new int[][]{{-6, -5}, {6, -5}, {-6, 5}, {6, 5}}) {
            b.fill(c[0], 2, c[1], c[0], 10, c[1], log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        }
        for (int x : new int[]{-3, 3}) {
            b.fill(x, 2, -5, x, 5, -5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
            b.fill(x, 2, 5, x, 5, 5, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        }
        // Вікна з віконницями і квітниками.
        for (int[] w : new int[][]{{-5, 3}, {5, 3}, {-5, 7}, {0, 7}, {5, 7}}) {
            int x = w[0], y = w[1];
            for (int z : new int[]{-5, 5}) {
                int out = z < 0 ? -1 : 1;
                Direction face = z < 0 ? Direction.NORTH : Direction.SOUTH;
                if (x == 0 && y == 3) {
                    continue;
                }
                b.fill(x, y, z, x, y + 1, z, s(Blocks.GLASS_PANE));
                if (Math.abs(x) < 6) {
                    shutter(b, x - 1, y, z + out, face);
                    shutter(b, x - 1, y + 1, z + out, face);
                    shutter(b, x + 1, y, z + out, face);
                    shutter(b, x + 1, y + 1, z + out, face);
                }
                b.set(x, y - 1, z + out, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
            }
        }
        for (int z : new int[]{-2, 2}) {
            for (int y : new int[]{3, 7}) {
                b.fill(-6, y, z, -6, y + 1, z, s(Blocks.GLASS_PANE));
                b.fill(6, y, z, 6, y + 1, z, s(Blocks.GLASS_PANE));
            }
        }
        // Ганок з дахом і ліхтарем.
        door(b, 0, 2, -5, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        b.fill(-2, 1, -7, 2, 1, -6, s(Blocks.STONE_BRICKS));
        for (int x = -2; x <= 2; x++) {
            b.stairs(x, 1, -8, Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false);
            b.slab(x, 5, -6, Blocks.DARK_OAK_SLAB, true);
            b.slab(x, 5, -7, Blocks.DARK_OAK_SLAB, true);
        }
        b.fill(-2, 2, -7, -2, 4, -7, s(Blocks.SPRUCE_FENCE));
        b.fill(2, 2, -7, 2, 4, -7, s(Blocks.SPRUCE_FENCE));
        b.set(0, 4, -7, hangingLantern());
        b.set(-1, 2, -7, s(Blocks.POTTED_RED_TULIP));
        b.set(1, 2, -7, s(Blocks.POTTED_AZURE_BLUET));
        // Двосхилий черепичний дах з навісом.
        for (int i = 0; i <= 6; i++) {
            int zn = -6 + i, zs = 6 - i, y = 10 + i;
            for (int x = -7; x <= 7; x++) {
                if (zn == zs) {
                    b.slab(x, y, zn, Blocks.DEEPSLATE_TILE_SLAB, false);
                } else {
                    b.stairs(x, y, zn, Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH, false);
                    b.stairs(x, y, zs, Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH, false);
                }
            }
            for (int z = zn + 1; z <= zs - 1; z++) {
                b.set(-6, y, z, s(Blocks.SPRUCE_PLANKS));
                b.set(6, y, z, s(Blocks.SPRUCE_PLANKS));
            }
        }
        b.set(-6, 12, 0, s(Blocks.GLASS_PANE));
        b.set(6, 12, 0, s(Blocks.GLASS_PANE));
        // Димар з димком.
        b.fill(4, 10, 3, 4, 17, 3, s(Blocks.BRICKS));
        b.set(4, 18, 3, s(Blocks.CAMPFIRE));

        // Інтер'єр: перший поверх.
        b.fill(-2, 1, -2, 2, 1, 2, s(Blocks.RED_WOOL));
        b.set(-5, 2, -4, s(Blocks.CRAFTING_TABLE));
        b.set(-4, 2, -4, Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, Direction.SOUTH));
        b.set(-5, 2, -2, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        b.fill(-5, 2, 0, -5, 3, 2, s(Blocks.BOOKSHELF));
        b.set(4, 2, -4, s(ModBlocks.LITTER_BOX));
        b.set(-4, 2, 4, s(ModBlocks.CARDBOARD_BOX));
        b.set(-5, 2, 4, s(ModBlocks.CARDBOARD_BOX));
        b.set(-5, 3, 4, s(ModBlocks.CARDBOARD_BOX));
        b.set(0, 5, 0, hangingLantern());
        for (int y = 2; y <= 7; y++) {
            b.set(5, y, 4, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }
        // Другий поверх: три ліжка.
        Block[] beds = {Blocks.RED_BED, Blocks.PINK_BED, Blocks.CYAN_BED};
        for (int i = 0; i < 3; i++) {
            int x = -4 + i * 2;
            b.set(x, 7, -3, beds[i].getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
            b.set(x, 7, -4, beds[i].getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
        }
        b.fill(-3, 6, 0, 3, 6, 2, s(Blocks.LIGHT_BLUE_WOOL));
        b.set(3, 7, -4, s(ModBlocks.CARDBOARD_BOX));
        b.set(0, 9, 0, hangingLantern());
        b.set(-5, 7, 3, s(Blocks.POTTED_FERN));

        if (b.world.getBlockEntity(b.at(-5, 2, -2)) instanceof ChestBlockEntity chest) {
            chest.setStack(0, new ItemStack(Items.COOKED_COD, 16));
            chest.setStack(1, new ItemStack(Items.BREAD, 16));
            chest.setStack(2, new ItemStack(Items.IRON_SWORD));
            chest.setStack(3, new ItemStack(Items.IRON_SWORD));
            chest.setStack(4, new ItemStack(Items.IRON_SWORD));
            chest.setStack(5, new ItemStack(Items.TORCH, 32));
        }
        st.setPoint("city", "spawn", b.at(0, 2, 0));
        st.setPoint("city", "cat_home", b.at(2, 2, 2));
        b.finish();
    }

    // ------------------------------------------------------------------ панелька

    /** Панельна п'ятиповерхівка з балконами (front: +1 — під'їзди на +z, -1 — на -z). */
    static void panelBlock(Builder b, int floors, Block wall, Block seam, int front, Random r) {
        int h = floors * 4;
        int fz = 5 * front;
        b.fill(-11, 1, -7, 11, h + 3, 7, s(Blocks.AIR));
        b.walls(-10, 1, -5, 10, h, 5, s(wall));
        for (int k = 1; k < floors; k++) {
            b.fill(-9, 4 * k + 1, -4, 9, 4 * k + 1, 4, s(Blocks.SMOOTH_STONE));
            b.walls(-10, 4 * k + 1, -5, 10, 4 * k + 1, 5, s(seam));
        }
        for (int x = -10; x <= 10; x += 3) {
            b.fill(x, 1, -5, x, h, -5, s(seam));
            b.fill(x, 1, 5, x, h, 5, s(seam));
        }
        Block[] glazing = {Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE};
        Block[] laundry = {Blocks.RED_WOOL, Blocks.WHITE_WOOL, Blocks.BLUE_WOOL, Blocks.PINK_WOOL};
        for (int k = 0; k < floors; k++) {
            int y = 4 * k + 2;
            for (int x = -8; x <= 8; x += 3) {
                for (int z : new int[]{-5, 5}) {
                    b.fill(x, y, z, x + 1, y + 1, z, s(Blocks.GLASS_PANE));
                }
                // Лампа в кімнаті — вікна світяться вночі.
                if (r.nextInt(3) == 0) {
                    b.set(x, y + 2, front > 0 ? 3 : -3, s(Blocks.SHROOMLIGHT));
                }
                // Балкони.
                if (k >= 1 && ((x + 8) / 3) % 2 == 0) {
                    int z1 = fz + front, z2 = fz + 2 * front;
                    b.fill(x - 1, y - 1, z1, x + 2, y - 1, z2, s(Blocks.SMOOTH_STONE));
                    boolean glazed = r.nextInt(3) == 0;
                    Block rail = glazed ? glazing[r.nextInt(glazing.length)] : Blocks.IRON_BARS;
                    for (int xx = x - 1; xx <= x + 2; xx++) {
                        b.set(xx, y, z2, s(rail));
                        if (glazed) {
                            b.set(xx, y + 1, z2, s(rail));
                        }
                    }
                    b.set(x - 1, y, z1, s(rail));
                    b.set(x + 2, y, z1, s(rail));
                    if (glazed) {
                        b.set(x - 1, y + 1, z1, s(rail));
                        b.set(x + 2, y + 1, z1, s(rail));
                        b.fill(x - 1, y + 2, z1, x + 2, y + 2, z2, s(Blocks.SMOOTH_STONE_SLAB));
                    } else if (r.nextBoolean()) {
                        b.set(x, y + 1, z2, s(laundry[r.nextInt(laundry.length)]));
                        b.set(x + 1, y + 1, z2, s(laundry[r.nextInt(laundry.length)]));
                    } else {
                        b.set(x + 1, y, z1, s(Blocks.POTTED_RED_TULIP));
                    }
                }
            }
            for (int z : new int[]{-2, 2}) {
                b.set(-10, y, z, s(Blocks.GLASS_PANE));
                b.set(10, y, z, s(Blocks.GLASS_PANE));
            }
        }
        // Під'їзди з козирками.
        for (int x : new int[]{-6, 0, 6}) {
            b.fill(x - 1, 1, fz, x + 1, 3, fz, s(seam));
            door(b, x, 1, fz, Blocks.DARK_OAK_DOOR, front > 0 ? Direction.SOUTH : Direction.NORTH);
            b.fill(x - 1, 4, fz + front, x + 1, 4, fz + 2 * front, s(Blocks.SMOOTH_STONE_SLAB));
            b.set(x, 3, fz + front, hangingLantern());
            b.stairs(x + 2, 1, fz + 2 * front, Blocks.SPRUCE_STAIRS, front > 0 ? Direction.NORTH : Direction.SOUTH, false);
        }
        // Дах: парапет, антени, вентиляція.
        b.fill(-10, h + 1, -5, 10, h + 1, 5, s(Blocks.GRAY_CONCRETE));
        b.walls(-10, h + 2, -5, 10, h + 2, 5, s(seam));
        for (int[] a : new int[][]{{-7, 2}, {4, -2}, {8, 1}}) {
            b.fill(a[0], h + 2, a[1], a[0], h + 4, a[1], s(Blocks.IRON_BARS));
            b.set(a[0], h + 5, a[1], s(Blocks.LIGHTNING_ROD));
        }
        b.fill(-3, h + 2, -1, -1, h + 3, 1, s(Blocks.STONE_BRICKS));
        // Графіті на першому поверсі.
        Block[] paint = {Blocks.MAGENTA_CONCRETE, Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.CYAN_CONCRETE};
        for (int i = 0; i < 6; i++) {
            int x = -9 + r.nextInt(19);
            if (Math.abs(x) % 6 <= 1) {
                continue;
            }
            b.set(x, 2 + r.nextInt(2), fz, s(paint[r.nextInt(paint.length)]));
        }
        b.finish();
    }

    // ------------------------------------------------------------------ дрібниці

    static void tree(Builder b, int x, int z, int h, Block logB, Block leafB) {
        b.fill(x, 1, z, x, h, z, log(logB, Direction.Axis.Y));
        for (int dy = -2; dy <= 1; dy++) {
            int rad = dy <= -1 ? 2 : 1;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= rad + 1 && !(dx == 0 && dz == 0 && dy < 1)) {
                        b.set(x + dx, h + dy, z + dz, leaves(leafB));
                    }
                }
            }
        }
        b.set(x, h + 1, z, leaves(leafB));
    }

    static void streetLamp(Builder b, int x, int z) {
        b.set(x, 1, z, s(Blocks.STONE_BRICK_WALL));
        b.fill(x, 2, z, x, 4, z, s(Blocks.DARK_OAK_FENCE));
        b.set(x, 5, z, s(Blocks.LANTERN));
    }

    /** Дерев'яний електростовп з перекладиною і ліхтарем, як на старих вулицях. */
    static void powerPole(Builder b, int x, int z) {
        b.fill(x, 1, z, x, 8, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        b.fill(x - 1, 8, z, x + 1, 8, z, s(Blocks.SPRUCE_FENCE));
        b.set(x, 9, z, s(Blocks.LIGHTNING_ROD));
        b.set(x, 6, z + 1, s(Blocks.SPRUCE_FENCE));
        b.set(x, 5, z + 1, hangingLantern());
    }

    static void bench(Builder b, int x, int z, Direction facing) {
        Direction side = facing.rotateYClockwise();
        b.stairs(x, 1, z, Blocks.SPRUCE_STAIRS, facing.getOpposite(), false);
        b.stairs(x + side.getOffsetX(), 1, z + side.getOffsetZ(), Blocks.SPRUCE_STAIRS, facing.getOpposite(), false);
    }

    static void fountain(Builder b, int cx, int cz) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r <= 5.4) {
                    b.set(cx + dx, 0, cz + dz, s(Blocks.POLISHED_ANDESITE));
                }
                if (r <= 3.4) {
                    b.set(cx + dx, -1, cz + dz, s(Blocks.PRISMARINE_BRICKS));
                    b.set(cx + dx, 0, cz + dz, s(Blocks.WATER));
                } else if (r <= 4.4) {
                    b.slab(cx + dx, 1, cz + dz, Blocks.QUARTZ_SLAB, false);
                }
            }
        }
        b.fill(cx, 0, cz, cx, 3, cz, s(Blocks.QUARTZ_PILLAR));
        b.set(cx, 4, cz, s(Blocks.WATER));
        b.world.scheduleFluidTick(b.at(cx, 4, cz), Fluids.WATER, 2);
        for (int[] l : new int[][]{{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
            b.set(cx + l[0], 1, cz + l[1], s(Blocks.SEA_LANTERN));
        }
    }

    static void stall(Builder b, int x, int z, Block c1, Block c2, Block goods) {
        for (int[] p : new int[][]{{0, 0}, {3, 0}, {0, 2}, {3, 2}}) {
            b.fill(x + p[0], 1, z + p[1], x + p[0], 3, z + p[1], s(Blocks.SPRUCE_FENCE));
        }
        for (int dx = -1; dx <= 4; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                b.set(x + dx, 4, z + dz, s((dx & 1) == 0 ? c1 : c2));
            }
        }
        b.fill(x + 1, 1, z, x + 2, 1, z, s(Blocks.BARREL));
        b.set(x + 1, 2, z, s(goods));
        b.set(x + 2, 2, z, s(Blocks.FLOWER_POT));
    }

    static void tram(Builder b, int x0, int z) {
        for (int x = x0; x < x0 + 9; x++) {
            b.fill(x, 1, z - 1, x, 1, z + 1, s(Blocks.RED_CONCRETE));
            b.fill(x, 2, z - 1, x, 3, z + 1, s(x == x0 || x == x0 + 8 || x % 3 == 0 ? Blocks.WHITE_CONCRETE : Blocks.GLASS));
            b.set(x, 2, z, s(Blocks.AIR));
            b.set(x, 3, z, s(Blocks.AIR));
            b.fill(x, 4, z - 1, x, 4, z + 1, s(Blocks.LIGHT_GRAY_CONCRETE));
        }
        b.set(x0 + 4, 5, z, s(Blocks.IRON_BARS));
        b.set(x0 + 4, 6, z, s(Blocks.CHAIN));
        b.set(x0, 2, z, s(Blocks.GLASS));
        b.set(x0 + 8, 2, z, s(Blocks.GLASS));
        b.fill(x0 + 2, 2, z, x0 + 6, 2, z, s(Blocks.AIR));
    }

    static void car(Builder b, int x, int z, Block color) {
        b.set(x, 1, z, s(Blocks.COAL_BLOCK));
        b.set(x + 3, 1, z, s(Blocks.COAL_BLOCK));
        b.set(x + 1, 1, z, s(color));
        b.set(x + 2, 1, z, s(color));
        b.slab(x, 2, z, Blocks.SMOOTH_STONE_SLAB, false);
        b.set(x + 1, 2, z, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        b.set(x + 2, 2, z, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
        b.set(x + 3, 2, z, s(color));
    }

    private CityArch() {
    }
}
