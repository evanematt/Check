package com.lewandivka.world.service;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.command.SelfTest;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.gen.Loot;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code /lewandivka loot}: what the containers of the buildings hold. Every table is rolled forty times the way a chest rolls it when
 * a player opens it (so that a table that does not load, or that names something that is not there, shows as empty chests), and a few
 * containers of every kind are looked at in the world: each has to carry the table of its kind.
 */
public final class LootCheck {

    private LootCheck() {
    }

    private static final int ROLLS = 40;

    public static SelfTest.Report run(MinecraftServer server) {
        SelfTest.Report report = new SelfTest.Report(new ArrayList<>(), new ArrayList<>());
        ServerWorld district = Dimensions.district(server);
        if (district == null) {
            report.problems().add("the district is not loaded");
            return report;
        }
        for (String kind : Loot.KINDS) {
            Identifier table = new Identifier(Loot.table(kind));
            int emptyChests = 0;
            int stacks = 0;
            Set<String> items = new TreeSet<>();
            for (int i = 0; i < ROLLS; i++) {
                ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState());
                chest.setWorld(district);
                chest.setLootTable(table, 7919L * (i + 1) + kind.hashCode());
                int here = 0;
                for (int slot = 0; slot < chest.size(); slot++) {
                    ItemStack stack = chest.getStack(slot);
                    if (!stack.isEmpty()) {
                        here++;
                        items.add(Registries.ITEM.getId(stack.getItem()).toString());
                    }
                }
                stacks += here;
                emptyChests += here == 0 ? 1 : 0;
            }
            report.notes().add(kind + ": " + stacks / (double) ROLLS + " stacks of " + items.size() + " kinds per chest");
            if (emptyChests > ROLLS / 10) {
                report.problems().add(kind + ": " + emptyChests + " of " + ROLLS + " chests came out empty (the table '" + table + "' does not load?)");
            }
            if (items.size() < 12) {
                report.problems().add(kind + ": only " + items.size() + " kinds of things in " + ROLLS + " chests: " + items);
            }
        }
        report.notes().add(ModLoot.summary());
        inTheWorld(server, report);
        return report;
    }

    /** The first container of every kind of place and one in the middle of the list, looked at in the world. */
    private static void inTheWorld(MinecraftServer server, SelfTest.Report report) {
        Map<String, java.util.List<BlockPos>> byKind = new HashMap<>();
        Map<BlockPos, String> dimension = new HashMap<>();
        int markers = 0;
        for (Structures.Site site : Structures.sites()) {
            for (Blueprint.Marker m : site.placement().blueprint().markers()) {
                if (!Loot.isLootMarker(m.name())) {
                    continue;
                }
                String table = Loot.tableOf(m.data());
                if (table == null || !table.startsWith("lewandivka:chests/")) {
                    report.problems().add("container " + site.placement().id() + ":" + m.name() + " names no table of the mod: " + m.data());
                    continue;
                }
                markers++;
                BlockPos at = new BlockPos(site.placement().x() + m.x(), site.placement().y() + m.y(), site.placement().z() + m.z());
                byKind.computeIfAbsent(table.substring("lewandivka:chests/".length()), k -> new ArrayList<>()).add(at);
                dimension.put(at, site.dimension());
            }
        }
        int looked = 0;
        for (Map.Entry<String, java.util.List<BlockPos>> e : byKind.entrySet()) {
            java.util.List<BlockPos> list = e.getValue();
            for (BlockPos at : new BlockPos[] {list.get(0), list.get(list.size() / 2)}) {
                ServerWorld world = Dimensions.world(server, dimension.get(at));
                BlockEntity be = world == null ? null : world.getBlockEntity(at);
                looked++;
                if (!(be instanceof LootableContainerBlockEntity)) {
                    report.problems().add(e.getKey() + ": no container with loot at " + at.toShortString() + " (" + (world == null ? "no world" : world.getBlockState(at)) + ")");
                    continue;
                }
                NbtCompound nbt = be.createNbt();
                String has = nbt.getString("LootTable");
                if (!has.equals("lewandivka:chests/" + e.getKey()) && !nbt.contains("Items")) {
                    report.problems().add(e.getKey() + ": the container at " + at.toShortString() + " has the table '" + has + "'");
                }
            }
        }
        report.notes().add(markers + " containers in " + byKind.size() + " kinds of places, " + looked + " looked at in the world");
        LewandivkaMod.LOGGER.info("[loot] {}", report.notes());
    }
}
