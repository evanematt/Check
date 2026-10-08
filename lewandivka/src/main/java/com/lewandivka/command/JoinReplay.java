package com.lewandivka.command;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.quest.Lifecycle;
import com.lewandivka.quest.Travel;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Developer diagnostics for what a client is sent when it joins and moves between the dimensions. A player is created and
 * joined through the real {@code PlayerManager.onPlayerConnect} with a connection that has no socket behind it: the packets
 * that would have been sent stay in its queue, so they can be read back in order, and every chunk can be decoded the way the
 * client decodes it and compared with the chunks of the worlds of the server (the client once showed stone and diorite in the
 * district where the plan has air). {@code torture} moves such a player around the way the client test does, with the
 * movement checks of the server running between the steps, and reports the exceptions the teleports throw.
 */
public final class JoinReplay {

    private record Session(String label, ServerPlayerEntity player, ClientConnection connection) {
    }

    private record Step(int at, String name, Runnable action) {
    }

    private static final Map<String, Session> SESSIONS = new LinkedHashMap<>();
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static final StringBuilder SCRIPT_LOG = new StringBuilder();
    private static MinecraftServer scriptServer;
    private static ServerPlayerEntity scriptPlayer;
    private static int scriptTick;
    private static boolean scriptRunning;
    private static int scriptProblems;

    private JoinReplay() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(JoinReplay::tick);
    }

    // ------------------------------------------------------------------ the join

    /**
     * Joins a player; the packets of the join (and of everything after it) stay in the queue of its connection. The arrival
     * delay is in ticks, negative for the one the game uses; 0 is the bug the replay was written to find (see {@link Lifecycle}).
     */
    public static String start(MinecraftServer server, String label, int arrivalDelay) {
        if (arrivalDelay < 0) {
            arrivalDelay = Lifecycle.DEFAULT_ARRIVAL_DELAY;
        }
        Session old = SESSIONS.remove(label);
        if (old != null) {
            server.getPlayerManager().remove(old.player());
        }
        String name = "LewReplay" + label;
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), profile);
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        int before = Lifecycle.arrivalDelayTicks;
        Lifecycle.arrivalDelayTicks = arrivalDelay;
        try {
            server.getPlayerManager().onPlayerConnect(connection, player);
        } finally {
            Lifecycle.arrivalDelayTicks = before;
        }
        SESSIONS.put(label, new Session(label, player, connection));
        return label + " joined (arrival delay " + arrivalDelay + "): " + where(player) + " " + registrations(server, player);
    }

    /** Reads the packet queues of the started players, decodes the chunks, removes the players. */
    public static SelfTest.Report report(MinecraftServer server) {
        return report(server, null);
    }

    private static SelfTest.Report report(MinecraftServer server, java.util.Collection<String> only) {
        SelfTest.Report report = new SelfTest.Report(new ArrayList<>(), new ArrayList<>());
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (only != null && !only.contains(s.label())) {
                continue;
            }
            SelfTest.guarded(report, "join replay " + s.label(), () -> analyse(server, s, report));
            SelfTest.guarded(report, "removing " + s.label(), () -> server.getPlayerManager().remove(s.player()));
            SESSIONS.remove(s.label());
        }
        return report;
    }

    @SuppressWarnings("unchecked")
    private static List<Packet<?>> queued(ClientConnection connection) throws ReflectiveOperationException {
        Field queueField = ClientConnection.class.getDeclaredField("packetQueue");
        queueField.setAccessible(true);
        Queue<Object> queue = (Queue<Object>) queueField.get(connection);
        Field packetField = Class.forName("net.minecraft.network.ClientConnection$QueuedPacket").getDeclaredField("packet");
        packetField.setAccessible(true);
        List<Packet<?>> out = new ArrayList<>();
        for (Object queuedPacket : new ArrayList<>(queue)) {
            out.add((Packet<?>) packetField.get(queuedPacket));
        }
        return out;
    }

    private static void analyse(MinecraftServer server, Session s, SelfTest.Report report) throws ReflectiveOperationException {
        List<Packet<?>> packets = queued(s.connection());
        RegistryKey<World> current = World.OVERWORLD;
        List<String> events = new ArrayList<>();
        int unloads = 0;
        Map<String, Integer> contamination = new LinkedHashMap<>();
        boolean respawned = false;
        for (Packet<?> packet : packets) {
            String event = null;
            if (packet instanceof GameJoinS2CPacket join) {
                current = join.dimensionId();
                event = "join(" + current.getValue() + ")";
            } else if (packet instanceof PlayerRespawnS2CPacket respawn) {
                current = respawn.getDimension();
                respawned = true;
                event = "respawn(" + current.getValue() + ")";
            } else if (packet instanceof ChunkRenderDistanceCenterS2CPacket center) {
                event = "center(" + center.getChunkX() + "," + center.getChunkZ() + ")";
            } else if (packet instanceof PlayerPositionLookS2CPacket look) {
                event = String.format(Locale.ROOT, "look(%.1f,%.1f,%.1f)", look.getX(), look.getY(), look.getZ());
            } else if (packet instanceof UnloadChunkS2CPacket) {
                unloads++;
                event = "unload";
            } else if (packet instanceof ChunkDataS2CPacket chunk) {
                String from = classify(server, current, chunk);
                event = "chunk[client in " + current.getValue() + ", data of " + from + "]";
                if (respawned) {
                    contamination.merge(from, 1, Integer::sum);
                }
            }
            if (event == null) {
                continue;
            }
            int last = events.size() - 1;
            if (last >= 0 && events.get(last).replaceAll(" x\\d+$", "").equals(event)) {
                String previous = events.get(last);
                int n = previous.matches(".* x\\d+$") ? Integer.parseInt(previous.substring(previous.lastIndexOf('x') + 1)) : 1;
                events.set(last, event + " x" + (n + 1));
            } else {
                events.add(event);
            }
        }
        String who = s.label();
        report.notes().add(who + ": " + packets.size() + " packets, " + events);
        report.notes().add(who + ": chunks sent after the respawn, by the world their data come from: " + contamination + ", unloads " + unloads);
        report.notes().add(who + ": now " + where(s.player()) + " " + registrations(server, s.player()));
        int listedIn = worldsListing(server, s.player());
        if (listedIn != 1) {
            report.problems().add(who + ": the player is listed by " + listedIn + " worlds, " + registrations(server, s.player()));
        }
        for (Map.Entry<String, Integer> e : contamination.entrySet()) {
            if (!e.getKey().equals(Dimensions.idOf(s.player().getWorld()))) {
                report.problems().add(who + ": " + e.getValue() + " chunks the client received after the respawn carry the blocks of " + e.getKey()
                        + " while the player is in " + Dimensions.idOf(s.player().getWorld()));
            }
        }
    }

    /** The world whose chunk at that position has the blocks of the packet (the client decodes it as a chunk of {@code current}). */
    private static String classify(MinecraftServer server, RegistryKey<World> current, ChunkDataS2CPacket packet) {
        ServerWorld asSeenByTheClient = server.getWorld(current);
        if (asSeenByTheClient == null) {
            return "unknown world " + current.getValue();
        }
        int x = packet.getX();
        int z = packet.getZ();
        WorldChunk decoded = new WorldChunk(asSeenByTheClient, new ChunkPos(x, z));
        ChunkData data = packet.getChunkData();
        decoded.loadFromPacket(data.getSectionsDataBuf(), data.getHeightmap(), data.getBlockEntities(x, z));
        String best = "none";
        int bestPercent = -1;
        for (ServerWorld w : server.getWorlds()) {
            Chunk real = w.getChunk(x, z, ChunkStatus.FULL, false);
            if (real == null) {
                continue;
            }
            int percent = percent(decoded, real);
            if (percent > bestPercent) {
                bestPercent = percent;
                best = Dimensions.idOf(w);
            }
        }
        return bestPercent >= 98 ? best : "no world (best " + best + " " + bestPercent + "%)";
    }

    private static int percent(Chunk a, Chunk b) {
        int y0 = Math.max(a.getBottomY(), b.getBottomY());
        int y1 = Math.min(a.getTopY(), b.getTopY());
        int total = 0;
        int same = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        int x0 = a.getPos().getStartX();
        int z0 = a.getPos().getStartZ();
        for (int dx = 1; dx < 16; dx += 4) {
            for (int dz = 1; dz < 16; dz += 4) {
                for (int y = y0; y < y1; y++) {
                    p.set(x0 + dx, y, z0 + dz);
                    BlockState sa = a.getBlockState(p);
                    BlockState sb = b.getBlockState(p);
                    total++;
                    if (sa == sb) {
                        same++;
                    }
                }
            }
        }
        return total == 0 ? 0 : same * 100 / total;
    }

    // ------------------------------------------------------------------ who the chunk system believes is where

    /** In how many worlds the player is in the list of players (it must be one). */
    private static int worldsListing(MinecraftServer server, ServerPlayerEntity p) {
        int n = 0;
        for (ServerWorld w : server.getWorlds()) {
            if (w.getPlayers().contains(p)) {
                n++;
            }
        }
        return n;
    }

    private static String where(ServerPlayerEntity p) {
        return Dimensions.idOf(p.getWorld()) + String.format(Locale.ROOT, " %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ());
    }

    /** For every world: whether it lists the player, and in which chunks its ticket manager has the player. */
    @SuppressWarnings("unchecked")
    static String registrations(MinecraftServer server, ServerPlayerEntity p) {
        StringBuilder sb = new StringBuilder("[");
        for (ServerWorld w : server.getWorlds()) {
            List<String> chunks = new ArrayList<>();
            try {
                // the field is private in the game: the dev environment (this is a development tool) has the names of Yarn
                Field managerField = ThreadedAnvilChunkStorage.class.getDeclaredField("ticketManager");
                managerField.setAccessible(true);
                Object tickets = managerField.get(w.getChunkManager().threadedAnvilChunkStorage);
                Field f = ChunkTicketManager.class.getDeclaredField("playersByChunkPos");
                f.setAccessible(true);
                Map<Long, java.util.Set<ServerPlayerEntity>> map = (Map<Long, java.util.Set<ServerPlayerEntity>>) f.get(tickets);
                for (Map.Entry<Long, java.util.Set<ServerPlayerEntity>> e : map.entrySet()) {
                    if (e.getValue().contains(p)) {
                        chunks.add(ChunkPos.getPackedX(e.getKey()) + "," + ChunkPos.getPackedZ(e.getKey()));
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                chunks.add("?" + e.getClass().getSimpleName());
            }
            sb.append(Dimensions.idOf(w)).append("{listed=").append(w.getPlayers().contains(p)).append(" ticketChunks=").append(chunks).append("} ");
        }
        return sb.append("watched=").append(p.getWatchedSection().getSectionX()).append(',').append(p.getWatchedSection().getSectionY()).append(',')
                .append(p.getWatchedSection().getSectionZ()).append(']').toString();
    }

    // ------------------------------------------------------------------ moving around the way the client test does

    /**
     * Joins a player and moves it like the client test does (a forced gallery far away, teleports inside the district, the
     * crossing to Chromandivka and back), with the update of the chunk watchers between the steps that the movement packets
     * of a client would cause. The answer of {@link #tortureResult()} lists what every step did and every exception.
     */
    public static String torture(MinecraftServer server) {
        if (scriptRunning) {
            return "the torture is still running (tick " + scriptTick + ")";
        }
        SCRIPT.clear();
        SCRIPT_LOG.setLength(0);
        scriptProblems = 0;
        scriptServer = server;
        scriptTick = 0;
        ServerWorld district = Dimensions.district(server);
        ServerWorld chroma = Dimensions.chroma(server);
        if (district == null || chroma == null) {
            return "the dimensions are not loaded";
        }
        String joined = start(server, "T", -1);
        scriptPlayer = SESSIONS.get("T").player();
        log("joined: " + joined);
        int[] s = DistrictPlan.get().spawn();
        step(40, "forceload the gallery", () -> {
            for (int cx = 62; cx <= 65; cx++) {
                for (int cz = 61; cz <= 64; cz++) {
                    district.setChunkForced(cx, cz, true);
                }
            }
        });
        step(80, "tp to the gallery 1", () -> request(1020.5, 154, 997.5));
        step(140, "tp to the gallery 2", () -> request(1020.5, 154, 1010.5));
        step(240, "tp to the gallery 3", () -> request(1020.5, 154, 1022.5));
        step(320, "look at the base from above", () -> chroma.getTopY(Heightmap.Type.WORLD_SURFACE, 0, 40));
        step(330, "cross to the base", () -> Travel.toMarker(scriptPlayer, "base:spawn"));
        step(380, "back to the first arrival", () -> Travel.to(scriptPlayer, Dimensions.DISTRICT_ID, new net.minecraft.util.math.Vec3d(s[0] + 0.5, s[1], s[2] + 0.5)));
        step(430, "to the rainbow garage", () -> Travel.toMarker(scriptPlayer, "rainbow_garage:entrance"));
        step(480, "back to the district", () -> Travel.to(scriptPlayer, Dimensions.DISTRICT_ID, new net.minecraft.util.math.Vec3d(s[0] + 0.5, s[1], s[2] + 0.5)));
        step(530, "to the base again", () -> Travel.toMarker(scriptPlayer, "base:spawn"));
        step(600, "the end", () -> {
            for (int cx = 62; cx <= 65; cx++) {
                for (int cz = 61; cz <= 64; cz++) {
                    district.setChunkForced(cx, cz, false);
                }
            }
        });
        scriptRunning = true;
        return "torture started: " + SCRIPT.size() + " steps over 600 ticks";
    }

    public static String tortureResult() {
        return scriptRunning ? "RUNNING at tick " + scriptTick + " " + SCRIPT_LOG
                : "DONE " + (scriptProblems == 0 ? "torture: OK" : "torture: PROBLEMS " + scriptProblems) + " ; " + SCRIPT_LOG;
    }

    private static void request(double x, double y, double z) {
        // what /tp does for a player in the same world
        scriptPlayer.getServerWorld().getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.POST_TELEPORT,
                new ChunkPos(BlockPos.ofFloored(x, y, z)), 1, scriptPlayer.getId());
        scriptPlayer.networkHandler.requestTeleport(x, y, z, 0.0f, 24.0f);
    }

    private static void step(int at, String name, Runnable action) {
        SCRIPT.add(new Step(at, name, action));
    }

    private static void log(String line) {
        SCRIPT_LOG.append(line).append(" ; ");
        LewandivkaMod.LOGGER.info("[torture] {}", line);
    }

    private static void tick(MinecraftServer server) {
        if (!scriptRunning) {
            return;
        }
        scriptTick++;
        // what the movement packets of a client cause on every tick: the chunk watchers follow the player
        if (scriptPlayer != null && !scriptPlayer.isRemoved()) {
            try {
                scriptPlayer.getServerWorld().getChunkManager().updatePosition(scriptPlayer);
            } catch (RuntimeException e) {
                log("updatePosition threw " + e);
            }
        }
        for (Step step : SCRIPT) {
            if (step.at() == scriptTick) {
                try {
                    step.action().run();
                    int listed = worldsListing(server, scriptPlayer);
                    log("+" + scriptTick + " " + step.name() + (listed == 1 ? " ok: " : " LISTED BY " + listed + " WORLDS: ") + where(scriptPlayer) + " " + registrations(server, scriptPlayer));
                    if (listed != 1) {
                        scriptProblems++;
                    }
                } catch (Throwable t) {
                    LewandivkaMod.LOGGER.error("[torture] step '{}' threw", step.name(), t);
                    StringBuilder sb = new StringBuilder(t.toString());
                    StackTraceElement[] frames = t.getStackTrace();
                    for (int i = 0; i < Math.min(8, frames.length); i++) {
                        sb.append(" | ").append(frames[i].getClassName().substring(frames[i].getClassName().lastIndexOf('.') + 1)).append('.')
                                .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
                    }
                    scriptProblems++;
                    log("+" + scriptTick + " " + step.name() + " THREW " + sb + " now " + where(scriptPlayer) + " " + registrations(server, scriptPlayer));
                }
            }
        }
        if (scriptTick >= 620) {
            scriptRunning = false;
            Session s = SESSIONS.get("T");
            if (s != null) {
                SelfTest.Report r = report(server, List.of("T"));
                for (String n : r.notes()) {
                    log(n);
                }
                for (String p : r.problems()) {
                    log("PROBLEM " + p);
                }
                scriptProblems += r.problems().size();
            }
            log(scriptProblems == 0 ? "torture: OK" : "torture: PROBLEMS " + scriptProblems);
            if (!Structures.has("base:spawn")) {
                log("(no base:spawn marker)");
            }
        }
    }
}
