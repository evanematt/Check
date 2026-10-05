package ua.lewandivka.logic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import ua.lewandivka.entity.BorzhnykEntity;
import ua.lewandivka.entity.GopnikEntity;
import ua.lewandivka.item.QuestNoteItem;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModItems;
import ua.lewandivka.world.Builder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Районні квести Пана Шлагбаума: Боржник, Гараж № 13, Останній трамвай. */
public final class DistrictQuests {
    public static boolean start(QuestNoteItem.Quest quest, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return switch (quest) {
            case BORZHNYK -> borzhnyk(world, player);
            case GARAGE -> garage(world, player);
            case TRAM -> tram(world, player);
        };
    }

    private static BlockPos surface(ServerWorld w, int x, int z) {
        return new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean borzhnyk(ServerWorld world, ServerPlayerEntity player) {
        Random r = world.random;
        double a = r.nextDouble() * Math.PI * 2;
        int dist = 14 + r.nextInt(8);
        BlockPos pos = surface(world, (int) (player.getX() + Math.cos(a) * dist), (int) (player.getZ() + Math.sin(a) * dist));
        BorzhnykEntity b = ModEntities.BORZHNYK.create(world);
        if (b == null) {
            return false;
        }
        b.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360, 0);
        b.setGlowing(true);
        b.setCustomName(Text.literal("Боржник із третього під'їзду"));
        world.spawnEntity(b);
        player.sendMessage(Text.literal("Боржник із третього під'їзду десь поруч (він світиться — від сорому). Наздожени і забери чайник. "
                + "Можна по-доброму: 3 смарагди в руку і ПКМ.").formatted(Formatting.YELLOW), false);
        return true;
    }

    private static boolean garage(ServerWorld world, ServerPlayerEntity player) {
        Direction f = player.getHorizontalFacing();
        BlockPos c = player.getBlockPos().offset(f, 18);
        BlockPos ground = surface(world, c.getX(), c.getZ()).down();
        Builder b = new Builder(world, ground);
        Random r = world.random;
        b.prepare(9, 9, 5, Blocks.STONE.getDefaultState());
        b.fill(-8, 0, -8, 8, 0, 8, Blocks.GRAY_CONCRETE.getDefaultState());
        // Лабіринт 7x7 клітинок -> сітка 15x15 (x,z від -7 до 7).
        boolean[][] open = new boolean[15][15];
        boolean[][] seen = new boolean[7][7];
        int[][] depth = new int[7][7];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, 0});
        seen[0][0] = true;
        open[1][1] = true;
        int[] far = {0, 0};
        int farDepth = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<int[]> next = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0], nz = cur[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < 7 && nz < 7 && !seen[nx][nz]) {
                    next.add(new int[]{nx, nz, d[0], d[1]});
                }
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] n = next.get(r.nextInt(next.size()));
            seen[n[0]][n[1]] = true;
            depth[n[0]][n[1]] = depth[cur[0]][cur[1]] + 1;
            if (depth[n[0]][n[1]] > farDepth) {
                farDepth = depth[n[0]][n[1]];
                far = new int[]{n[0], n[1]};
            }
            open[cur[0] * 2 + 1 + n[2]][cur[1] * 2 + 1 + n[3]] = true;
            open[n[0] * 2 + 1][n[1] * 2 + 1] = true;
            stack.push(new int[]{n[0], n[1]});
        }
        open[1][0] = true; // вхід
        BlockState[] walls = {Blocks.ORANGE_TERRACOTTA.getDefaultState(), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(),
                Blocks.CYAN_TERRACOTTA.getDefaultState(), Blocks.BROWN_TERRACOTTA.getDefaultState()};
        for (int gx = 0; gx < 15; gx++) {
            for (int gz = 0; gz < 15; gz++) {
                if (!open[gx][gz]) {
                    BlockState w = walls[(gx * 7 + gz * 3) % walls.length];
                    b.fill(gx - 7, 1, gz - 7, gx - 7, 3, gz - 7, w);
                }
            }
        }
        b.set(1 - 7, 4, -7 - 1, Blocks.LANTERN.getDefaultState());
        b.set(-7 - 1, 1, -7 - 1, Blocks.LANTERN.getDefaultState());
        // Посилка в найдальшій клітинці.
        BlockPos chestPos = b.at(far[0] * 2 + 1 - 7, 1, far[1] * 2 + 1 - 7);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH));
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setStack(13, new ItemStack(ModItems.GARAGE_PARCEL));
            chest.setStack(4, new ItemStack(ModItems.DISTRICT_TOKEN, 3));
            chest.setStack(22, new ItemStack(Items.BREAD, 4));
        }
        // Охорона.
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                if ((i > 1 || j > 1) && !(i == far[0] && j == far[1])) {
                    cells.add(new int[]{i, j});
                }
            }
        }
        Collections.shuffle(cells, new java.util.Random(r.nextLong()));
        for (int i = 0; i < 3 && i < cells.size(); i++) {
            spawnGuard(world, ModEntities.GOPNIK, b.at(cells.get(i)[0] * 2 + 1 - 7, 1, cells.get(i)[1] * 2 + 1 - 7), false);
        }
        player.sendMessage(Text.literal("Попереду виріс Гараж № 13 — лабіринт гаражів. Вхід позначений ліхтарем. Посилка в найдальшому куті, "
                + "охорона не спить. Посилка бурчить — тримайтесь разом!").formatted(Formatting.YELLOW), false);
        return true;
    }

    private static boolean tram(ServerWorld world, ServerPlayerEntity player) {
        BlockPos center = player.getBlockPos();
        int[] waves = {4, 5, 3};
        for (int w = 0; w < waves.length; w++) {
            final int wave = w;
            Scheduler.after(world.getServer(), 20 + w * 400, () -> {
                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (p.squaredDistanceTo(center.getX(), center.getY(), center.getZ()) < 48 * 48) {
                        p.sendMessage(Text.literal(wave < 2 ? "Хвиля безквиткових " + (wave + 1) + "/3!" : "Остання хвиля — з ними Ватажок безквиткових! У нього компостер.")
                                .formatted(Formatting.RED), false);
                    }
                }
                for (int i = 0; i < waves[wave]; i++) {
                    double a = world.random.nextDouble() * Math.PI * 2;
                    int d = 8 + world.random.nextInt(7);
                    BlockPos pos = surface(world, center.getX() + (int) (Math.cos(a) * d), center.getZ() + (int) (Math.sin(a) * d));
                    spawnGuard(world, ModEntities.SHADE, pos, false);
                }
                if (wave == 2) {
                    spawnGuard(world, ModEntities.SHADE, surface(world, center.getX() + 6, center.getZ()), true);
                }
            });
        }
        player.sendMessage(Text.literal("Дзинь-дзинь… Останній трамвай. Тіньові безквиткові вже лізуть з-під коліс. Тримайте зупинку!")
                .formatted(Formatting.YELLOW), false);
        return true;
    }

    private static void spawnGuard(ServerWorld world, EntityType<GopnikEntity> type, BlockPos pos, boolean leader) {
        GopnikEntity g = type.create(world);
        if (g == null) {
            return;
        }
        g.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
        g.setPersistent();
        if (leader) {
            g.setLeader(true);
            g.setCustomName(Text.literal("Ватажок безквиткових").formatted(Formatting.DARK_PURPLE));
            g.setGlowing(true);
            g.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(50);
            g.setHealth(50);
        }
        world.spawnEntity(g);
    }

    private DistrictQuests() {
    }
}
