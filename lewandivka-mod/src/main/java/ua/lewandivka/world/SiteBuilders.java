package ua.lewandivka.world;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import ua.lewandivka.block.BossAltarBlock;
import ua.lewandivka.entity.GopnikEntity;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.logic.Travel;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModItems;

/** Будування всіх локацій Хромандівки. y=0 — рівень підлоги. */
final class SiteBuilders {
    private static final BlockState[] RAINBOW = {
            Blocks.RED_CONCRETE.getDefaultState(), Blocks.ORANGE_CONCRETE.getDefaultState(), Blocks.YELLOW_CONCRETE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(), Blocks.CYAN_CONCRETE.getDefaultState(), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(),
            Blocks.PURPLE_CONCRETE.getDefaultState(), Blocks.MAGENTA_CONCRETE.getDefaultState()};

    private static BlockState s(Block b) {
        return b.getDefaultState();
    }

    private static void spawnMob(Builder b, EntityType<GopnikEntity> type, int x, int y, int z) {
        GopnikEntity g = type.create(b.world);
        if (g == null) {
            return;
        }
        BlockPos p = b.at(x, y, z);
        g.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, b.world.random.nextFloat() * 360, 0);
        g.setPersistent();
        b.world.spawnEntity(g);
    }

    private static void altar(Builder b, LewState st, String site, int index, int x, int y, int z) {
        b.set(x, y, z, ModBlocks.BOSS_ALTAR.getDefaultState().with(BossAltarBlock.BOSS, index));
        st.setPoint(site, "arena", b.at(x, y, z));
    }

    private static void node(Builder b, LewState st, String site, Block block, int x, int y, int z) {
        b.set(x, y, z, s(block));
        st.addNode(site, b.at(x, y, z));
    }

    // ---------------------------------------------------------------- база

    static void base(Builder b, LewState st) {
        b.prepare(10, 10, 16, s(Blocks.STONE));
        b.fill(-10, 0, -10, 10, 0, 10, s(Blocks.GRASS_BLOCK));
        b.fill(-1, 0, -10, 1, 0, -7, s(Blocks.DIRT_PATH));
        // Панелька: стіни, кольорові смуги, вікна.
        b.fill(-6, 0, -6, 6, 0, 6, s(Blocks.OAK_PLANKS));
        b.walls(-6, 1, -6, 6, 9, 6, s(Blocks.LIGHT_GRAY_CONCRETE));
        b.walls(-6, 4, -6, 6, 4, 6, s(Blocks.PINK_CONCRETE));
        b.walls(-6, 9, -6, 6, 9, 6, s(Blocks.CYAN_CONCRETE));
        for (int y : new int[]{2, 3, 6, 7}) {
            for (int c : new int[]{-3, 3}) {
                b.set(c, y, -6, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                b.set(c, y, 6, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                b.set(-6, y, c, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                b.set(6, y, c, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
            }
        }
        b.fill(-5, 5, -5, 5, 5, 5, s(Blocks.OAK_PLANKS));
        // Загнутий фіолетовий дах.
        b.fill(-7, 10, -7, 7, 10, 7, s(Blocks.PURPLE_CONCRETE));
        b.fill(-5, 11, -5, 5, 11, 5, s(Blocks.MAGENTA_CONCRETE));
        b.fill(-3, 12, -3, 3, 12, 3, s(Blocks.PINK_CONCRETE));
        b.fill(-1, 13, -1, 1, 13, 1, s(ModBlocks.CRYSTAL_ROSE));
        for (int[] c : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            b.set(c[0], 11, c[1], s(Blocks.PURPLE_CONCRETE));
            b.set(c[0], 12, c[1], s(ModBlocks.CRYSTAL_AQUA));
        }
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            b.set(c[0], 5, c[1], s(Blocks.SHROOMLIGHT));
            b.set(c[0], 10, c[1], s(Blocks.SHROOMLIGHT));
        }
        // Двері.
        b.set(0, 1, -6, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, 2, -6, Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        b.set(-1, 3, -7, s(Blocks.LANTERN));
        b.set(1, 3, -7, s(Blocks.LANTERN));
        // Драбина на другий поверх.
        for (int y = 1; y <= 6; y++) {
            b.set(5, y, 5, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }
        // Перший поверх.
        b.set(-5, 1, -5, s(Blocks.CRAFTING_TABLE));
        b.set(-4, 1, -5, Blocks.FURNACE.getDefaultState().with(FurnaceBlock.FACING, Direction.SOUTH));
        b.set(-5, 1, -3, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        b.set(5, 1, -5, s(ModBlocks.LITTER_BOX));
        b.set(-5, 1, 5, s(ModBlocks.CARDBOARD_BOX));
        b.set(-4, 1, 5, s(ModBlocks.CARDBOARD_BOX));
        b.set(-5, 2, 5, s(ModBlocks.CARDBOARD_BOX));
        b.set(0, 1, 5, s(ModBlocks.CHROMA_PORTAL));
        // Другий поверх: три ліжка.
        int[] bedX = {-4, -2, 0};
        Block[] beds = {Blocks.RED_BED, Blocks.PINK_BED, Blocks.CYAN_BED};
        for (int i = 0; i < 3; i++) {
            b.set(bedX[i], 6, -3, beds[i].getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
            b.set(bedX[i], 6, -4, beds[i].getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
        }
        b.set(3, 6, -4, s(ModBlocks.CARDBOARD_BOX));
        b.set(4, 6, -4, s(ModBlocks.CARDBOARD_BOX));
        // Двір.
        b.set(-9, 1, -9, s(ModBlocks.CRYSTAL_AQUA));
        b.set(9, 1, 9, s(ModBlocks.CRYSTAL_CITRINE));
        b.set(9, 1, -9, s(ModBlocks.CRYSTAL_ROSE));
        b.set(-8, 1, 8, s(ModBlocks.LAUNCH_PAD));

        BlockPos chestPos = b.at(-5, 1, -3);
        if (b.world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setStack(0, new ItemStack(ModItems.COLLAR_CHINAZIK));
            chest.setStack(1, new ItemStack(ModItems.COLLAR_METADONNA));
            ItemStack note = new ItemStack(Items.PAPER);
            note.setCustomName(net.minecraft.text.Text.literal("Записка: «Вони вже були тут. Вони знають дорогу.»"));
            chest.setStack(2, note);
            chest.setStack(4, new ItemStack(Items.COOKED_COD, 12));
            chest.setStack(5, new ItemStack(Items.BREAD, 16));
            chest.setStack(6, new ItemStack(Items.TORCH, 32));
            chest.setStack(8, Travel.guideBook());
        }
        st.setPoint("base", "spawn", b.at(0, 1, 0));
        st.setPoint("base", "cat_home", b.at(2, 1, 2));
    }

    // ---------------------------------------------------------------- гараж

    static void garage(Builder b, LewState st) {
        b.prepare(18, 18, 12, s(Blocks.STONE));
        for (int x = -17; x <= 17; x++) {
            for (int z = -17; z <= 17; z++) {
                boolean line = Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0;
                b.set(x, 0, z, s(line ? Blocks.YELLOW_CONCRETE : Blocks.GRAY_CONCRETE));
                if (Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0 && (x != 0 || z != 0)) {
                    b.set(x, 0, z, s(Blocks.SEA_LANTERN));
                }
            }
        }
        // Ряди веселкових гаражів по периметру.
        for (int y = 1; y <= 5; y++) {
            for (int a = -17; a <= 17; a++) {
                BlockState c = RAINBOW[Math.floorMod(Math.floorDiv(a + 17, 5), RAINBOW.length)];
                BlockState door = y <= 3 && Math.floorMod(a + 17, 5) != 0 && Math.floorMod(a + 17, 5) != 4 ? s(Blocks.IRON_BARS) : c;
                b.set(a, y, -17, y <= 3 ? door : c);
                b.set(a, y, 17, y <= 3 ? door : c);
                b.set(-17, y, a, y <= 3 ? door : c);
                b.set(17, y, a, y <= 3 ? door : c);
            }
        }
        b.walls(-17, 6, -17, 17, 6, 17, s(Blocks.BLACK_CONCRETE));
        b.fill(-2, 1, -17, 2, 4, -17, s(Blocks.AIR));
        // Укриття-«гаражі» всередині.
        int i = 0;
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            b.fill(c[0] - 1, 1, c[1] - 2, c[0] + 1, 3, c[1] + 2, RAINBOW[(i++ * 2) % RAINBOW.length]);
            b.fill(c[0] - 1, 4, c[1] - 2, c[0] + 1, 4, c[1] + 2, s(Blocks.BLACK_CONCRETE));
        }
        // Три підйомники.
        for (int[] n : new int[][]{{-14, 14}, {14, 14}, {14, -14}}) {
            node(b, st, "garage", ModBlocks.LIFT_SWITCH, n[0], 1, n[1]);
            for (int y = 2; y <= 5; y++) {
                b.set(n[0], y, n[1], s(Blocks.CHAIN));
            }
            b.set(n[0], 6, n[1], s(Blocks.IRON_BLOCK));
        }
        altar(b, st, "garage", 0, 0, 1, 0);
        spawnMob(b, ModEntities.SHADE, -10, 1, 4);
        spawnMob(b, ModEntities.SHADE, 10, 1, -4);
        spawnMob(b, ModEntities.GOPNIK, 4, 1, 10);
    }

    // ---------------------------------------------------------------- притулок

    static void shelter(Builder b, LewState st) {
        Random r = b.world.random;
        b.prepare(9, 9, 10, s(Blocks.STONE));
        b.fill(-9, 0, -9, 9, 0, 9, s(Blocks.GRASS_BLOCK));
        b.fill(-3, 1, -3, 3, 4, 3, s(Blocks.PINK_TERRACOTTA));
        b.fill(-2, 1, -2, 2, 3, 2, s(Blocks.AIR));
        b.fill(-4, 5, -4, 4, 5, 4, s(Blocks.PURPLE_CONCRETE));
        b.fill(-3, 6, -3, 3, 6, 3, s(Blocks.PURPLE_CONCRETE));
        b.fill(-2, 7, -2, 2, 7, 2, s(Blocks.MAGENTA_CONCRETE));
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            b.set(c[0], 6, c[1], s(Blocks.PURPLE_CONCRETE));
        }
        b.set(0, 8, 0, s(ModBlocks.CRYSTAL_AQUA));
        b.set(-3, 2, 0, s(Blocks.MAGENTA_STAINED_GLASS));
        b.set(3, 2, 0, s(Blocks.MAGENTA_STAINED_GLASS));
        b.set(0, 1, -3, s(ModBlocks.SHELTER_DOOR));
        b.set(0, 2, -3, s(ModBlocks.SHELTER_DOOR));
        b.set(-2, 1, -5, s(Blocks.LANTERN));
        b.set(2, 1, -5, s(Blocks.LANTERN));
        b.set(4, 1, -5, s(ModBlocks.CARDBOARD_BOX));
        st.setPoint("shelter", "ext_arrival", b.at(0, 1, -5));

        // Всередині — набагато більше, ніж зовні.
        Builder in = new Builder(b.world, b.at(0, -36, 0));
        in.fill(-17, -1, -17, 17, 9, 17, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        in.fill(-16, 1, -16, 16, 7, 16, s(Blocks.AIR));
        in.fill(-16, 0, -16, 16, 0, 16, s(Blocks.DARK_OAK_PLANKS));
        for (int x = -12; x <= 12; x += 6) {
            for (int z = -12; z <= 12; z += 6) {
                in.set(x, 8, z, s(Blocks.SHROOMLIGHT));
            }
        }
        in.set(0, 1, 17, s(ModBlocks.SHELTER_DOOR));
        in.set(0, 2, 17, s(ModBlocks.SHELTER_DOOR));
        in.set(0, 1, 15, s(ModBlocks.DOOR_MAT));
        st.setPoint("shelter", "int_arrival", in.at(0, 1, 14));
        st.setPoint("shelter", "doormat", in.at(0, 1, 15));
        // Стіна з великими дверима Колекціонера.
        in.fill(-16, 1, 4, 16, 7, 4, s(Blocks.PURPLE_TERRACOTTA));
        in.fill(-2, 1, 4, 2, 5, 4, s(ModBlocks.COLLECTOR_DOOR));
        st.setPoint("shelter", "door_min", in.at(-2, 1, 4));
        st.setPoint("shelter", "door_max", in.at(2, 5, 4));
        // Три важелі.
        for (int[] n : new int[][]{{-12, 12}, {12, 12}, {0, 8}}) {
            in.set(n[0], 1, n[1], s(ModBlocks.DOOR_LEVER));
            st.addNode("shelter", in.at(n[0], 1, n[1]));
        }
        // Кімната з сотнями коробок.
        in.fill(3, 1, -16, 3, 7, 3, s(Blocks.PURPLE_TERRACOTTA));
        in.fill(3, 1, -6, 3, 2, -6, s(Blocks.AIR));
        in.fill(5, 1, -16, 16, 3, 3, s(ModBlocks.CARDBOARD_BOX));
        int tx = 6 + r.nextInt(10);
        int tz = -15 + r.nextInt(17);
        in.set(tx, 1, tz, s(ModBlocks.TINY_BOX));
        st.setPoint("shelter", "tiny_box", in.at(tx, 1, tz));
        st.setPoint("shelter", "chinazik_spawn", in.at(0, 1, 2));
        st.setPoint("shelter", "collector_spawn", in.at(-8, 1, -8));
        for (int[] c : new int[][]{{-15, 15}, {15, 15}, {-15, -15}, {-10, 5}}) {
            in.set(c[0], 1, c[1], s(ModBlocks.CARDBOARD_BOX));
        }
        in.set(-15, 1, 10, s(Blocks.LANTERN));
        in.set(15, 1, 10, s(Blocks.LANTERN));
    }

    // ---------------------------------------------------------------- аквапарк

    static void aqua(Builder b, LewState st) {
        b.prepare(23, 23, 14, s(Blocks.STONE));
        for (int x = -23; x <= 23; x++) {
            for (int z = -23; z <= 23; z++) {
                double r = Math.sqrt(x * x + z * z);
                BlockState floor;
                if (r <= 15.5) {
                    if (z >= 0) {
                        floor = s(x >= 0 ? Blocks.CYAN_CONCRETE : Blocks.LIGHT_BLUE_CONCRETE);
                    } else {
                        floor = s(x < 0 ? Blocks.YELLOW_CONCRETE : Blocks.PINK_CONCRETE);
                    }
                } else if (r <= 17) {
                    floor = s(Blocks.PRISMARINE_BRICKS);
                } else {
                    floor = s(Blocks.SMOOTH_SANDSTONE);
                }
                b.set(x, 0, z, floor);
                if (r > 17 && r <= 18 && Math.abs(x) > 1 && Math.abs(z) > 1) {
                    b.set(x, 1, z, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                    b.set(x, 2, z, s(Blocks.LIGHT_BLUE_STAINED_GLASS));
                }
            }
        }
        // Колони з кольоровими водоспадами.
        for (int[] c : new int[][]{{14, 14}, {-14, 14}, {-14, -14}, {14, -14}}) {
            b.fill(c[0], 1, c[1], c[0], 9, c[1], s(Blocks.QUARTZ_PILLAR));
            b.set(c[0], 10, c[1], s(Blocks.WATER));
            b.world.scheduleFluidTick(b.at(c[0], 10, c[1]), Fluids.WATER, 2);
        }
        // Гірки-платформи.
        for (int[] c : new int[][]{{9, 9}, {-9, 9}, {-9, -9}, {9, -9}}) {
            b.fill(c[0] - 1, 1, c[1] - 1, c[0] + 1, 1, c[1] + 1, s(Blocks.WHITE_CONCRETE));
        }
        for (int[] c : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            b.set(c[0], 0, c[1], s(Blocks.SEA_LANTERN));
        }
        altar(b, st, "aqua", 1, 0, 1, 0);
        spawnMob(b, ModEntities.SHADE, 20, 1, 0);
        spawnMob(b, ModEntities.SHADE, -20, 1, 0);
    }

    // ---------------------------------------------------------------- депо

    private static void tramCar(Builder b, int x0, int y, int z0) {
        b.fill(x0 - 1, y, z0 - 3, x0 + 1, y + 2, z0 + 3, s(Blocks.RED_CONCRETE));
        for (int z = z0 - 2; z <= z0 + 2; z++) {
            b.set(x0 - 1, y + 1, z, s(Blocks.GLASS));
            b.set(x0 + 1, y + 1, z, s(Blocks.GLASS));
        }
        b.fill(x0 - 1, y + 3, z0 - 3, x0 + 1, y + 3, z0 + 3, s(Blocks.WHITE_CONCRETE));
        b.set(x0, y + 4, z0, s(Blocks.CHAIN));
    }

    static void depot(Builder b, LewState st) {
        b.prepare(8, 8, 6, s(Blocks.STONE));
        b.fill(-8, 0, -8, 8, 0, 8, s(Blocks.POLISHED_ANDESITE));
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            b.fill(c[0], 1, c[1], c[0], 3, c[1], s(Blocks.YELLOW_CONCRETE));
        }
        b.fill(-4, 4, -4, 4, 4, 4, s(Blocks.YELLOW_CONCRETE));
        b.set(0, 4, 0, s(Blocks.SEA_LANTERN));
        for (int x = -8; x <= 8; x++) {
            b.set(x, 1, 6, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        b.set(0, 1, 0, s(ModBlocks.TRAM_STOP));
        st.setPoint("depot", "stop_ground", b.at(0, 1, 0));
        st.setPoint("depot", "arrive_ground", b.at(0, 1, -2));

        int h = 70;
        b.fill(-14, h - 1, -14, 14, h + 8, 14, s(Blocks.AIR));
        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                b.set(x, h, z, s(Math.floorMod(x, 4) == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE));
            }
        }
        b.walls(-13, h + 1, -13, 13, h + 1, 13, s(Blocks.WHITE_STAINED_GLASS));
        for (int x = -12; x <= 12; x++) {
            b.set(x, h + 1, -10, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
            b.set(x, h + 1, 10, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        tramCar(b, -7, h + 1, 0);
        tramCar(b, 7, h + 1, 0);
        for (int[] c : new int[][]{{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            b.set(c[0], h, c[1], s(Blocks.SEA_LANTERN));
        }
        node(b, st, "depot", ModBlocks.TICKET_VALIDATOR, -11, h + 1, -7);
        node(b, st, "depot", ModBlocks.TICKET_VALIDATOR, 11, h + 1, -7);
        node(b, st, "depot", ModBlocks.TICKET_VALIDATOR, 0, h + 1, 11);
        altar(b, st, "depot", 2, 0, h + 1, 0);
        b.set(0, h + 1, -12, s(ModBlocks.TRAM_STOP));
        st.setPoint("depot", "stop_sky", b.at(0, h + 1, -12));
        st.setPoint("depot", "arrive_sky", b.at(0, h + 1, -9));
        // Трамваї між островами.
        for (int[] isl : new int[][]{{-30, h - 6, 10}, {28, h + 5, -16}}) {
            b.fill(isl[0] - 3, isl[1], isl[2] - 4, isl[0] + 3, isl[1], isl[2] + 4, s(Blocks.GRASS_BLOCK));
            b.fill(isl[0] - 2, isl[1] - 1, isl[2] - 3, isl[0] + 2, isl[1] - 2, isl[2] + 3, s(Blocks.DIRT));
            b.set(isl[0], isl[1] - 3, isl[2], s(ModBlocks.CRYSTAL_AQUA));
            tramCar(b, isl[0], isl[1] + 1, isl[2]);
        }
    }

    // ---------------------------------------------------------------- вежа

    static void tower(Builder b, LewState st) {
        b.prepare(13, 13, 58, s(Blocks.STONE));
        b.fill(-13, 0, -13, 13, 0, 13, s(Blocks.SMOOTH_STONE));
        for (int k = 0; k < 8; k++) {
            b.fill(-8, 6 * k, -8, 8, 6 * k, 8, s(Blocks.LIGHT_GRAY_CONCRETE));
        }
        // Дах-арена.
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                b.set(x, 48, z, s(Math.floorMod(x + z, 4) == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE));
            }
        }
        b.walls(-8, 1, -8, 8, 47, 8, s(Blocks.GRAY_CONCRETE));
        for (int k = 1; k < 8; k++) {
            b.walls(-8, 6 * k, -8, 8, 6 * k, 8, s(k % 2 == 0 ? Blocks.MAGENTA_CONCRETE : Blocks.CYAN_CONCRETE));
        }
        for (int k = 0; k < 8; k++) {
            for (int y : new int[]{6 * k + 2, 6 * k + 3}) {
                for (int c : new int[]{-5, -2, 1, 4}) {
                    b.set(c, y, -8, s(Blocks.LIGHT_GRAY_STAINED_GLASS));
                    b.set(c, y, 8, s(Blocks.LIGHT_GRAY_STAINED_GLASS));
                    b.set(-8, y, c, s(Blocks.LIGHT_GRAY_STAINED_GLASS));
                    b.set(8, y, c, s(Blocks.LIGHT_GRAY_STAINED_GLASS));
                }
            }
            for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
                b.set(c[0], 6 * (k + 1), c[1], s(Blocks.SHROOMLIGHT));
            }
        }
        b.fill(-1, 1, -8, 1, 3, -8, s(Blocks.AIR));
        // Драбини: кожен поверх — інший кут.
        int[][] corners = {{-7, -7}, {7, -7}, {7, 7}, {-7, 7}};
        for (int k = 0; k < 8; k++) {
            int[] c = corners[k % 4];
            Direction facing = c[1] < 0 ? Direction.SOUTH : Direction.NORTH;
            for (int y = 6 * k + 1; y <= 6 * k + 6; y++) {
                b.set(c[0], y, c[1], Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing));
            }
        }
        BlockState door = s(ModBlocks.GUARD_DOOR);
        BlockState bars = s(ModBlocks.CHROMA_BARS);
        BlockState wall = s(Blocks.GRAY_CONCRETE);
        // Поверх 1: охоронні двері — потрібен Чіназік.
        for (int y = 7; y <= 11; y++) {
            BlockState s = y <= 9 ? door : wall;
            b.set(6, y, -7, s);
            b.set(6, y, -6, s);
            b.set(7, y, -6, s);
        }
        // Поверх 2: ґрати — потрібна Метадонна.
        for (int y = 13; y <= 17; y++) {
            BlockState s = y <= 15 ? bars : wall;
            b.set(6, y, 7, s);
            b.set(6, y, 6, s);
            b.set(7, y, 6, s);
        }
        boxSwitchCage(b, 13, 7, 6);
        // Поверх 5: і те, і те.
        for (int y = 31; y <= 35; y++) {
            b.set(6, y, -7, y <= 33 ? door : wall);
            b.set(6, y, -6, y <= 33 ? door : wall);
            b.set(7, y, -6, y <= 33 ? bars : wall);
        }
        boxSwitchCage(b, 31, -7, -6);
        for (int k = 1; k < 8; k++) {
            spawnMob(b, k % 2 == 0 ? ModEntities.COLORLESS : ModEntities.SHADE, -3, 6 * k + 1, 0);
            spawnMob(b, ModEntities.COLORLESS, 3, 6 * k + 1, 2);
        }
        for (int[] c : new int[][]{{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            b.set(c[0], 49, c[1], s(ModBlocks.CRYSTAL_ROSE));
        }
        altar(b, st, "tower", 4, 0, 49, 0);
    }

    /** Кнопка в коробці за ґратами біля стіни (z — ряд біля стіни, inner — ряд всередину). */
    private static void boxSwitchCage(Builder b, int y, int z, int inner) {
        b.set(0, y, z, s(ModBlocks.BOX_SWITCH));
        BlockState bars = s(ModBlocks.CHROMA_BARS);
        for (int x = -1; x <= 1; x++) {
            for (int dy = 0; dy <= 1; dy++) {
                if (x == 0 && dy == 0) {
                    b.set(0, y, inner, bars);
                    continue;
                }
                b.set(x, y + dy, z, bars);
                b.set(x, y + dy, inner, bars);
            }
        }
    }

    private SiteBuilders() {
    }
}
