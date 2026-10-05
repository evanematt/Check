package ua.lewandivka.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import ua.lewandivka.entity.BorzhnykEntity;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.entity.GopnikEntity;
import ua.lewandivka.logic.DistrictQuests;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModVillagers;

/** Маленька Левандівка на старті світу: наш будинок, кіоск Шлагбаума, сіра панелька Боржника, Гараж № 13, зупинка. */
public final class City {
    private static BlockState s(net.minecraft.block.Block b) {
        return b.getDefaultState();
    }

    public static void ensure(MinecraftServer server) {
        LewState st = LewState.get(server);
        if (st.has("city_built")) {
            return;
        }
        st.set("city_built");
        ServerWorld w = server.getOverworld();
        BlockPos sp = w.getSpawnPos();
        w.getChunk(sp.getX() >> 4, sp.getZ() >> 4);
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
        int y = Math.max(w.getSeaLevel() + 1, Math.min(top, 140)) - 1;
        Builder b = new Builder(w, new BlockPos(sp.getX(), y, sp.getZ()));

        b.prepare(44, 32, 16, s(Blocks.DIRT));
        b.fill(-44, 0, -32, 44, 0, 32, s(Blocks.GRASS_BLOCK));
        // Вулиця з розміткою і тротуарами.
        net.minecraft.block.Block[] cobble = {Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.COBBLESTONE, Blocks.ANDESITE,
                Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.POLISHED_ANDESITE};
        for (int x = -44; x <= 44; x++) {
            for (int z = -3; z <= 3; z++) {
                b.set(x, 0, z, s(cobble[w.random.nextInt(cobble.length)]));
            }
        }
        b.fill(-44, 0, -5, 44, 0, -4, s(Blocks.SMOOTH_STONE));
        b.fill(-44, 0, 4, 44, 0, 5, s(Blocks.SMOOTH_STONE));
        for (int x = -44; x <= 44; x++) {
            b.set(x, 1, 2, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }

        net.minecraft.util.math.random.Random r = w.random;
        // Тротуарні дерева і ліхтарі.
        for (int x = -40; x <= 40; x += 12) {
            CityArch.powerPole(b, x, -6);
            CityArch.streetLamp(b, x + 6, 6);
        }
        for (int x = -42; x <= 42; x += 16) {
            if (Math.abs(x) > 8) {
                CityArch.tree(b, x, -8, 5, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
            }
        }

        // Наш котедж (південь вулиці).
        CityArch.cottage(new Builder(w, b.at(0, 0, 17)), st);

        // Панельки: сіра (Боржник) на півночі, пастельна на півдні.
        CityArch.panelBlock(new Builder(w, b.at(-28, 0, -19)), 5, Blocks.LIGHT_GRAY_CONCRETE, Blocks.WHITE_CONCRETE, 1, r);
        CityArch.panelBlock(new Builder(w, b.at(-28, 0, 20)), 4, Blocks.PINK_TERRACOTTA, Blocks.WHITE_TERRACOTTA, -1, r);
        BorzhnykEntity bz = ModEntities.BORZHNYK.create(w);
        if (bz != null) {
            BlockPos p = b.at(-28, 1, -11);
            bz.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            bz.setCustomName(Text.literal("Боржник із третього під'їзду"));
            w.spawnEntity(bz);
            st.setBoss("city_borzhnyk", bz.getUuid());
        }

        // Кіоск Пана Шлагбаума і ринок.
        b.fill(8, 0, -14, 22, 0, -7, s(Blocks.STONE_BRICKS));
        b.fill(10, 1, -12, 12, 3, -10, s(Blocks.RED_TERRACOTTA));
        b.fill(11, 1, -10, 11, 2, -10, s(Blocks.AIR));
        b.set(11, 1, -10, s(ModBlocks.KIOSK));
        b.set(10, 2, -10, s(Blocks.GLASS_PANE));
        b.set(12, 2, -10, s(Blocks.GLASS_PANE));
        for (int x = 9; x <= 13; x++) {
            for (int z = -13; z <= -9; z++) {
                b.set(x, 4, z, s(((x + z) & 1) == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL));
            }
        }
        b.set(11, 5, -11, s(Blocks.LANTERN));
        CityArch.stall(b, 15, -13, Blocks.YELLOW_WOOL, Blocks.WHITE_WOOL, Blocks.MELON);
        CityArch.stall(b, 20, -13, Blocks.LIME_WOOL, Blocks.WHITE_WOOL, Blocks.PUMPKIN);
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v != null) {
            BlockPos p = b.at(11, 1, -8);
            v.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            v.setVillagerData(v.getVillagerData().withProfession(ModVillagers.SHLAGBAUM).withLevel(1));
            v.setExperience(1);
            v.setCustomName(Text.literal("Пан Шлагбаум"));
            v.setCustomNameVisible(true);
            v.setPersistent();
            w.spawnEntity(v);
            st.setBoss("shlagbaum", v.getUuid());
        }

        // Парк з фонтаном навпроти дому.
        b.fill(12, 0, 8, 40, 0, 30, s(Blocks.GRASS_BLOCK));
        b.fill(25, 0, 8, 27, 0, 30, s(Blocks.GRAVEL));
        b.fill(12, 0, 18, 40, 0, 20, s(Blocks.GRAVEL));
        CityArch.fountain(b, 26, 19);
        for (int[] t : new int[][]{{15, 11}, {37, 11}, {15, 27}, {37, 27}, {20, 24}, {32, 14}}) {
            CityArch.tree(b, t[0], t[1], 5 + r.nextInt(2), Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        }
        CityArch.tree(b, 33, 25, 6, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES);
        CityArch.tree(b, 19, 13, 6, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES);
        CityArch.bench(b, 22, 17, Direction.SOUTH);
        CityArch.bench(b, 29, 21, Direction.NORTH);
        CityArch.bench(b, 24, 23, Direction.EAST);
        for (int x = 13; x <= 39; x += 2) {
            b.set(x, 1, 9, s(x % 4 == 1 ? Blocks.PEONY : Blocks.ROSE_BUSH));
        }

        // Гараж № 13.
        DistrictQuests.buildGarage13(w, b.at(30, 0, -24));
        st.setPoint("city", "garage13", b.at(30, 1, -24));
        for (int[] g : new int[][]{{24, -12}, {34, -12}}) {
            GopnikEntity gp = ModEntities.GOPNIK.create(w);
            if (gp != null) {
                BlockPos p = b.at(g[0], 1, g[1]);
                gp.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
                gp.setPersistent();
                w.spawnEntity(gp);
            }
        }

        // Трамвайна зупинка «Левандівка» і трамвай на колії.
        b.fill(-24, 1, -5, -24, 3, -5, s(Blocks.YELLOW_CONCRETE));
        b.fill(-18, 1, -5, -18, 3, -5, s(Blocks.YELLOW_CONCRETE));
        b.fill(-25, 4, -6, -17, 4, -4, s(Blocks.YELLOW_CONCRETE));
        b.fill(-23, 1, -5, -19, 3, -5, s(Blocks.GLASS_PANE));
        b.set(-21, 3, -4, s(Blocks.BELL));
        CityArch.bench(b, -22, -4, Direction.SOUTH);
        st.setPoint("city", "tram", b.at(-21, 1, -3));
        CityArch.tram(b, -12, 2);
        CityArch.car(b, -38, -2, Blocks.BLUE_CONCRETE);
        CityArch.car(b, 36, 1, Blocks.YELLOW_CONCRETE);
        b.finish();

        // Коти вдома: сидять і дивляться в одну порожню стіну.
        BlockPos catHome = st.point("city", "cat_home");
        String[] keys = {"home_cat1", "home_cat2"};
        EntityType<?>[] types = {ModEntities.CHINAZIK, ModEntities.METADONNA};
        for (int i = 0; i < 2 && catHome != null; i++) {
            if (types[i].create(w) instanceof ChromaCatEntity cat) {
                cat.refreshPositionAndAngles(catHome.getX() + 0.5 + i, catHome.getY(), catHome.getZ() + 0.5, 90, 0);
                cat.setQuestMode(true);
                w.spawnEntity(cat);
                st.setBoss(keys[i], cat.getUuid());
            }
        }
        BlockPos spawn = st.point("city", "spawn");
        if (spawn != null) {
            w.setSpawnPos(spawn, 0);
        }
    }

    /** Перший вхід гравця — одразу вдома. */
    public static void onJoin(ServerPlayerEntity p) {
        LewState st = LewState.get(p.getServer());
        BlockPos spawn = st.point("city", "spawn");
        if (spawn == null || st.has("joined_" + p.getUuid())) {
            return;
        }
        st.set("joined_" + p.getUuid());
        p.teleport(p.getServer().getOverworld(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        p.sendMessage(Text.literal("Левандівка. Ти вдома. Коти чомусь пильно дивляться в порожню стіну…").formatted(Formatting.LIGHT_PURPLE), false);
    }

    /** Коти зникають з дому, щойно ви вперше потрапили в Хромандівку. */
    public static void removeHomeCats(MinecraftServer server) {
        LewState st = LewState.get(server);
        for (String k : new String[]{"home_cat1", "home_cat2"}) {
            java.util.UUID id = st.boss(k);
            if (id != null && server.getOverworld().getEntity(id) != null) {
                server.getOverworld().getEntity(id).discard();
            }
            st.setBoss(k, null);
        }
    }

    private City() {
    }
}
