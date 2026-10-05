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
        b.fill(-44, 0, -3, 44, 0, 3, s(Blocks.GRAY_CONCRETE));
        for (int x = -44; x <= 44; x += 4) {
            b.set(x, 0, 0, s(Blocks.WHITE_CONCRETE));
            b.set(x + 1, 0, 0, s(Blocks.WHITE_CONCRETE));
        }
        b.fill(-44, 0, -5, 44, 0, -4, s(Blocks.SMOOTH_STONE));
        b.fill(-44, 0, 4, 44, 0, 5, s(Blocks.SMOOTH_STONE));
        for (int x = -40; x <= 40; x += 10) {
            for (int z : new int[]{-5, 5}) {
                b.fill(x, 1, z, x, 3, z, s(Blocks.DARK_OAK_FENCE));
                b.set(x, 4, z, s(Blocks.LANTERN));
            }
        }
        for (int x = -44; x <= 44; x++) {
            b.set(x, 1, 2, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }

        // Наш будинок.
        Builder home = new Builder(w, b.at(0, 0, 16));
        SiteBuilders.house(home, st, "city", false);
        b.fill(-1, 0, 6, 1, 0, 9, s(Blocks.DIRT_PATH));

        // Кіоск Пана Шлагбаума.
        b.set(12, 1, -8, s(ModBlocks.KIOSK));
        b.set(13, 1, -8, s(ModBlocks.KIOSK));
        b.fill(11, 4, -9, 14, 4, -6, s(Blocks.RED_WOOL));
        b.fill(11, 1, -6, 11, 3, -6, s(Blocks.OAK_FENCE));
        b.fill(14, 1, -6, 14, 3, -6, s(Blocks.OAK_FENCE));
        VillagerEntity v = EntityType.VILLAGER.create(w);
        if (v != null) {
            BlockPos p = b.at(12, 1, -6);
            v.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            v.setVillagerData(v.getVillagerData().withProfession(ModVillagers.SHLAGBAUM).withLevel(1));
            v.setExperience(1);
            v.setCustomName(Text.literal("Пан Шлагбаум"));
            v.setCustomNameVisible(true);
            v.setPersistent();
            w.spawnEntity(v);
            st.setBoss("shlagbaum", v.getUuid());
        }

        // Сіра панелька, де ховається Боржник.
        Builder blk = new Builder(w, b.at(-26, 0, -18));
        blk.walls(-6, 1, -6, 6, 12, 6, s(Blocks.LIGHT_GRAY_CONCRETE));
        blk.fill(-6, 13, -6, 6, 13, 6, s(Blocks.GRAY_CONCRETE));
        for (int fy : new int[]{2, 6, 10}) {
            for (int c : new int[]{-3, 0, 3}) {
                blk.set(c, fy, 6, s(Blocks.GLASS));
                blk.set(c, fy, -6, s(Blocks.GLASS));
            }
        }
        for (int dx : new int[]{-3, 0, 3}) {
            blk.fill(dx, 1, 6, dx, 2, 6, s(Blocks.AIR));
        }
        blk.set(3, 3, 7, s(Blocks.LANTERN));
        BorzhnykEntity bz = ModEntities.BORZHNYK.create(w);
        if (bz != null) {
            BlockPos p = blk.at(3, 1, 8);
            bz.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            bz.setCustomName(Text.literal("Боржник із третього під'їзду"));
            w.spawnEntity(bz);
            st.setBoss("city_borzhnyk", bz.getUuid());
        }

        // Гараж № 13.
        DistrictQuests.buildGarage13(w, b.at(28, 0, -20));
        st.setPoint("city", "garage13", b.at(28, 1, -20));
        for (int[] g : new int[][]{{18, -8}, {36, -8}}) {
            GopnikEntity gp = ModEntities.GOPNIK.create(w);
            if (gp != null) {
                BlockPos p = b.at(g[0], 1, g[1]);
                gp.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
                gp.setPersistent();
                w.spawnEntity(gp);
            }
        }

        // Трамвайна зупинка «Левандівка».
        b.fill(-24, 1, -5, -24, 3, -5, s(Blocks.YELLOW_CONCRETE));
        b.fill(-18, 1, -5, -18, 3, -5, s(Blocks.YELLOW_CONCRETE));
        b.fill(-24, 4, -6, -18, 4, -4, s(Blocks.YELLOW_CONCRETE));
        b.set(-21, 3, -5, s(Blocks.BELL));
        b.fill(-23, 1, -6, -19, 1, -6, s(Blocks.SPRUCE_SLAB));
        st.setPoint("city", "tram", b.at(-21, 1, -4));

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
