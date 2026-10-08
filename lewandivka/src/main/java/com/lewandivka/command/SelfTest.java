package com.lewandivka.command;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.quest.Travel;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.dimension.PlanBlockView;
import com.lewandivka.world.structure.Structures;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The self test of a running server ({@code /lewandivka selftest}, run over rcon by the CI smoke test): the checks that
 * need the real dimensions and cannot be done by a GameTest, because the vanilla test server does not load them.
 *
 * <ul>
 *   <li>the chunks the real dimensions generated are the chunks the plan paints (nothing else writes into the world);</li>
 *   <li>the first arrival is not inside a block and has floor under it;</li>
 *   <li>a player (a Fabric fake player with a dummy connection) can cross between the dimensions with the same
 *       {@link Travel} calls the story uses, the first time and the following times.</li>
 * </ul>
 */
public final class SelfTest {

    private SelfTest() {
    }

    /** What happened: {@code notes} describe, {@code problems} fail the test. */
    public record Report(List<String> notes, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    public static Report run(MinecraftServer server) {
        Report report = new Report(new ArrayList<>(), new ArrayList<>());
        ServerWorld district = Dimensions.district(server);
        ServerWorld chroma = Dimensions.chroma(server);
        if (district == null || chroma == null) {
            report.problems().add("a custom dimension is not loaded (district " + (district != null) + ", chromandivka " + (chroma != null) + ")");
            return report;
        }
        guarded(report, "district against the plan", () -> compare(district, DistrictPlan.get(), -4, -4, 5, 4, DistrictPlan.GROUND + 1, DistrictPlan.GROUND + 45, report));
        guarded(report, "chromandivka against the plan", () -> compare(chroma, ChromaPlan.get(), -2, -2, 2, 2, 60, 140, report));
        guarded(report, "first arrival", () -> firstArrival(district, report));
        guarded(report, "crossing", () -> crossing(server, district, chroma, report));
        return report;
    }

    private interface Check {
        void run() throws Exception;
    }

    private static void guarded(Report report, String what, Check check) {
        try {
            check.run();
        } catch (Throwable t) {
            LewandivkaMod.LOGGER.error("Self test step '{}' failed with an exception", what, t);
            report.problems().add(what + ": " + describe(t));
        }
    }

    /** The exception and the first frames of its stack, short enough for one line. */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < Math.min(5, frames.length); i++) {
            sb.append(" | ").append(frames[i].getClassName().substring(frames[i].getClassName().lastIndexOf('.') + 1))
                    .append('.').append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
        }
        if (t.getCause() != null) {
            sb.append(" | caused by ").append(t.getCause());
        }
        return sb.toString();
    }

    private static String id(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    // ------------------------------------------------------------------ the generated world is the plan

    /**
     * Compares the blocks of the generated chunks with the plan above the ground. Everything that is not the plan (stone
     * where the plan has air, ores, villages, leftovers of a vanilla generator) shows up here with its position.
     */
    private static void compare(ServerWorld world, WorldPlan plan, int cx0, int cz0, int cx1, int cz1, int y0, int y1, Report report) {
        PlanBlockView view = new PlanBlockView(plan, world.getRegistryManager());
        BlockPos.Mutable p = new BlockPos.Mutable();
        Map<String, Integer> pairs = new LinkedHashMap<>();
        List<String> first = new ArrayList<>();
        int differ = 0;
        long cells = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                world.getChunk(cx, cz);
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        for (int y = y0; y <= y1; y++) {
                            p.set(x, y, z);
                            Block expected = view.getBlockState(p).getBlock();
                            Block actual = world.getBlockState(p).getBlock();
                            cells++;
                            if (expected != actual) {
                                differ++;
                                String pair = id(expected) + " -> " + id(actual);
                                pairs.merge(pair, 1, Integer::sum);
                                if (first.size() < 6) {
                                    first.add(x + "," + y + "," + z + " " + pair);
                                }
                            }
                        }
                    }
                }
            }
        }
        String line = Dimensions.idOf(world) + " against the plan: " + cells + " cells, " + differ + " differ";
        if (differ > 0) {
            List<String> top = new ArrayList<>();
            pairs.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6).forEach(e -> top.add(e.getKey() + " x" + e.getValue()));
            line += " " + top + ", for example " + first;
            report.problems().add(line);
        }
        report.notes().add(line);
    }

    // ------------------------------------------------------------------ the first arrival

    private static String column(ServerWorld world, int x, int z, int y0, int y1) {
        StringBuilder sb = new StringBuilder();
        String last = null;
        int from = y0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = y0; y <= y1 + 1; y++) {
            String now = y <= y1 ? id(world.getBlockState(p.set(x, y, z)).getBlock()) : null;
            if (now == null ? last != null : !now.equals(last)) {
                if (last != null) {
                    sb.append(from == y - 1 ? String.valueOf(from) : from + ".." + (y - 1)).append('=').append(last).append(' ');
                }
                last = now;
                from = y;
            }
        }
        return sb.toString().trim();
    }

    /** The column of blocks at a position, as the {@code /lewandivka probe} command prints it. */
    public static String probe(ServerWorld world, int x, int y0, int y1, int z) {
        world.getChunk(x >> 4, z >> 4);
        return Dimensions.idOf(world) + " " + x + "," + z + ": " + column(world, x, z, y0, y1);
    }

    private static void firstArrival(ServerWorld district, Report report) {
        int[] s = DistrictPlan.get().spawn();
        BlockPos feet = new BlockPos(s[0], s[1], s[2]);
        district.getChunk(feet);
        report.notes().add("spawn column " + probe(district, s[0], s[1] - 4, s[1] + 5, s[2]));
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos at = feet.up(dy);
            BlockState state = district.getBlockState(at);
            if (!state.getCollisionShape(district, at).isEmpty()) {
                report.problems().add("the first arrival is inside " + id(state.getBlock()) + " at " + at.toShortString());
            }
        }
        boolean floor = false;
        for (int i = 1; i <= 4; i++) {
            BlockPos at = feet.down(i);
            floor |= !district.getBlockState(at).getCollisionShape(district, at).isEmpty();
        }
        if (!floor) {
            report.problems().add("nothing solid within four blocks under the first arrival " + feet.toShortString());
        }
    }

    // ------------------------------------------------------------------ crossing between the dimensions

    private static String where(ServerPlayerEntity p) {
        return Dimensions.idOf(p.getWorld()) + String.format(java.util.Locale.ROOT, " %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ());
    }

    private static void expectIn(ServerPlayerEntity p, String dimension, String what, Report report) {
        if (!dimension.equals(Dimensions.idOf(p.getWorld()))) {
            report.problems().add(what + ": the player is in " + where(p) + ", expected " + dimension);
        } else {
            report.notes().add(what + ": " + where(p));
        }
    }

    private static void crossing(MinecraftServer server, ServerWorld district, ServerWorld chroma, Report report) {
        ServerWorld overworld = server.getOverworld();
        ServerPlayerEntity player = FakePlayer.get(overworld, new GameProfile(UUID.randomUUID(), "lewandivka-selftest"));
        overworld.onPlayerConnected(player);
        try {
            int[] s = DistrictPlan.get().spawn();
            Vec3d spawn = new Vec3d(s[0] + 0.5, s[1], s[2] + 0.5);
            Travel.to(player, Dimensions.DISTRICT_ID, spawn);
            expectIn(player, Dimensions.DISTRICT_ID, "to the first arrival", report);
            for (String marker : new String[] {"base:spawn", "rainbow_garage:entrance", "base:spawn"}) {
                if (!Structures.has(marker)) {
                    report.problems().add("no marker " + marker);
                    continue;
                }
                boolean moved = Travel.toMarker(player, marker);
                if (!moved) {
                    report.problems().add("Travel.toMarker(" + marker + ") refused");
                }
                expectIn(player, Dimensions.CHROMA_ID, "to " + marker, report);
                Structures.Marker m = Structures.marker(marker);
                if (m != null && player.squaredDistanceTo(m.stand()) > 16 * 16) {
                    report.problems().add("to " + marker + ": the player is far from the marker, at " + where(player));
                }
                Travel.to(player, Dimensions.DISTRICT_ID, spawn);
                expectIn(player, Dimensions.DISTRICT_ID, "back to the district", report);
            }
        } finally {
            player.getServerWorld().removePlayer(player, Entity.RemovalReason.DISCARDED);
        }
    }
}
