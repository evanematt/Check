package com.lewandivka.client.dev;

import com.lewandivka.client.ClientNet;
import com.lewandivka.client.ClientState;
import com.lewandivka.client.screen.ConfigScreen;
import com.lewandivka.client.screen.CreditsScreen;
import com.lewandivka.client.screen.NotebookScreen;
import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.world.dimension.PlanBlockView;
import com.lewandivka.world.dimension.Plans;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Developer and CI tool, active only when the environment variable LEWANDIVKA_AUTOTEST is set: the client joins a
 * server, tours every structure of both dimensions, shows every entity and block, opens the screens and takes
 * screenshots, then writes {@code autotest-result.txt} and stops. It never runs for players.
 */
public final class AutoTest {

    private record Step(String name, Runnable action, int delay) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final StringBuilder LOG = new StringBuilder();
    private static boolean built;
    private static int index;
    private static int wait;
    private static int shots;
    /** While set, the script is paused until the condition has held for {@link #STABLE_TICKS} ticks (or the time is up). */
    private static java.util.function.BooleanSupplier awaiting;
    private static String awaitingName = "";
    private static int awaitLeft;
    private static int stable;
    private static final int STABLE_TICKS = 40;
    private static int stableNeeded = STABLE_TICKS;
    private static boolean awaitingTerrain;
    private static int timeouts;
    private static int failures;
    /** True while the script itself has a screen open (notebook, settings, credits); any other screen is closed. */
    private static boolean screenWanted;
    private static String traceName = "";
    private static int traceLeft;
    /** The trace writes a line every this many ticks. */
    private static int traceEvery = 4;
    private static int traceSetEvery = 4;
    /** Where the portal frame of the Nether leg stands (the block the player stood in when it was built). */
    private static BlockPos portalSite = BlockPos.ORIGIN;

    private AutoTest() {
    }

    /** {@code LEWANDIVKA_AUTOTEST=cars}: the second run of the client test, in a world of its own with the cars of Trep's Cars (see {@link #buildCars}). */
    private static final boolean CARS_MODE = "cars".equals(System.getenv("LEWANDIVKA_AUTOTEST"));

    public static void register() {
        if (System.getenv("LEWANDIVKA_AUTOTEST") == null) {
            return;
        }
        LewandivkaMod.LOGGER.info("AutoTest is enabled{}", CARS_MODE ? " (the cars)" : "");
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void note(String line) {
        LOG.append(line).append('\n');
        LewandivkaMod.LOGGER.info("[autotest] {}", line);
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) {
            if (CARS_MODE) {
                clickThrough(c);
            }
            return;
        }
        if (!built) {
            built = true;
            build(c);
            note("script has " + STEPS.size() + " steps");
        }
        if (!screenWanted && c.currentScreen != null && !(c.currentScreen instanceof DownloadingTerrainScreen)) {
            note("closed a screen nobody asked for: " + c.currentScreen.getClass().getSimpleName());
            c.setScreen(null);
        }
        if (traceLeft > 0) {
            if (traceLeft % traceEvery == 0) {
                Vec3d v = c.player.getVelocity();
                note(String.format(Locale.ROOT, "trace %s: %.2f %.2f %.2f v=(%.2f %.2f %.2f) ground=%s", traceName,
                        c.player.getX(), c.player.getY(), c.player.getZ(), v.x, v.y, v.z, c.player.isOnGround()));
            }
            traceLeft--;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (awaiting != null) {
            stable = awaiting.getAsBoolean() ? stable + 1 : 0;
            if (stable >= stableNeeded || --awaitLeft <= 0) {
                if (stable < stableNeeded) {
                    timeouts += awaitingTerrain ? 1 : 0;
                    note("await " + awaitingName + " timed out");
                }
                awaiting = null;
                where(c, awaitingName);
            }
            return;
        }
        if (index >= STEPS.size()) {
            finish(c, true);
            return;
        }
        Step s = STEPS.get(index++);
        note("step " + index + "/" + STEPS.size() + " " + s.name());
        try {
            s.action().run();
        } catch (RuntimeException e) {
            note("FAILED " + s.name() + ": " + e);
            LewandivkaMod.LOGGER.error("autotest step failed", e);
        }
        wait = s.delay();
        flush(c);
    }

    /** The result file is written as the script goes, so that CI can publish what is known long before the end. */
    private static void flush(MinecraftClient c) {
        try {
            Files.writeString(Path.of(c.runDirectory.toURI()).resolve("autotest-result.txt"), LOG.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // the write at the end reports a real problem
        }
    }

    private static void finish(MinecraftClient c, boolean ok) {
        ok = ok && failures == 0;
        note(ok ? "AUTOTEST-RESULT OK shots=" + shots : "AUTOTEST-RESULT FAILED checks=" + failures);
        try {
            Files.writeString(Path.of(c.runDirectory.toURI()).resolve("autotest-result.txt"), LOG.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LewandivkaMod.LOGGER.error("could not write the autotest result", e);
        }
        c.scheduleStop();
    }

    private static int loadedFor;

    /**
     * True when the player is out of the loading screen and the chunk around them has been loaded for a while: the mesh
     * builder is idle, or (it can stay busy for a long time with software rendering) at least ten seconds have passed.
     */
    private static boolean terrainReady(MinecraftClient c) {
        boolean loaded = c.player != null && c.world != null && c.currentScreen == null
                && c.world.getChunkManager().isChunkLoaded(c.player.getBlockPos().getX() >> 4, c.player.getBlockPos().getZ() >> 4);
        loadedFor = loaded ? loadedFor + 1 : 0;
        return loaded && (c.worldRenderer.isTerrainRenderComplete() || loadedFor >= 200);
    }

    /** What the camera sees: position, the block at the eye, the light there (to understand dark or blocked shots). */
    private static void where(MinecraftClient c, String name) {
        BlockPos eye = BlockPos.ofFloored(c.player.getEyePos());
        note(String.format("at %s: %s %.1f %.1f %.1f yaw %.0f pitch %.0f eye=%s inWall=%s sky=%d block=%d time=%d", name,
                c.world.getRegistryKey().getValue(), c.player.getX(), c.player.getY(), c.player.getZ(), c.player.getYaw(), c.player.getPitch(),
                c.world.getBlockState(eye).getBlock().getTranslationKey(), c.player.isInsideWall(), c.world.getLightLevel(LightType.SKY, eye),
                c.world.getLightLevel(LightType.BLOCK, eye), c.world.getTimeOfDay()));
        if (c.player.isInsideWall()) {
            // the blocks of the column under and over the camera as the client knows them (name#raw state id): compare with
            // the server's answer to /lewandivka probe to tell a generation problem from a client-side one
            note("  column of the client at " + eye.getX() + "," + eye.getZ() + ": " + column(c, eye, 4, 3)
                    + " chunkEmpty=" + c.world.getChunk(eye.getX() >> 4, eye.getZ() >> 4).isEmpty());
        }
    }

    private static String column(MinecraftClient c, BlockPos at, int below, int above) {
        StringBuilder sb = new StringBuilder();
        for (int dy = -below; dy <= above; dy++) {
            BlockPos p = at.up(dy);
            BlockState state = c.world.getBlockState(p);
            sb.append(p.getY()).append('=').append(Registries.BLOCK.getId(state.getBlock())).append('#').append(Block.getRawIdFromState(state)).append(' ');
        }
        return sb.toString().trim();
    }

    /** The column as the client knows it, runs of the same block together (the form of the server's {@code /lewandivka probe}). */
    private static String rle(MinecraftClient c, int x, int z, int y0, int y1) {
        StringBuilder sb = new StringBuilder();
        String last = null;
        int from = y0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = y0; y <= y1 + 1; y++) {
            String now = y <= y1 ? Registries.BLOCK.getId(c.world.getBlockState(p.set(x, y, z)).getBlock()).toString() : null;
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

    /**
     * Which chunks around the player hold the blocks of the plan: '#' the plan, '+' mostly, 'x' something else, '.' not loaded.
     * The client once showed stone where the plan has air; this says which chunks, and (at another time) whether they heal.
     */
    private static void chunkGrid(MinecraftClient c, String when) {
        WorldPlan plan = Plans.byDimension(c.world.getRegistryKey().getValue().toString());
        if (plan == null) {
            note("chunk grid " + when + ": " + c.world.getRegistryKey().getValue() + " has no plan");
            return;
        }
        PlanBlockView view = new PlanBlockView(plan, c.world.getRegistryManager());
        int pcx = c.player.getBlockPos().getX() >> 4;
        int pcz = c.player.getBlockPos().getZ() >> 4;
        StringBuilder sb = new StringBuilder("chunk grid " + when + " around chunk " + pcx + "," + pcz + " ('#' plan, '+' mostly, 'x' other, '.' not loaded):");
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dz = -4; dz <= 4; dz++) {
            sb.append("\n   ");
            for (int dx = -4; dx <= 4; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                WorldChunk chunk = c.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    sb.append('.');
                    continue;
                }
                int total = 0;
                int same = 0;
                for (int ox = 1; ox < 16; ox += 4) {
                    for (int oz = 1; oz < 16; oz += 4) {
                        for (int y = 56; y <= 96; y++) {
                            p.set((cx << 4) + ox, y, (cz << 4) + oz);
                            total++;
                            if (c.world.getBlockState(p) == view.getBlockState(p)) {
                                same++;
                            }
                        }
                    }
                }
                int percent = same * 100 / total;
                sb.append(percent >= 98 ? '#' : percent >= 80 ? '+' : 'x');
            }
        }
        note(sb.toString());
    }

    // ------------------------------------------------------------------ script

    private static void add(String name, int wait, Runnable action) {
        STEPS.add(new Step(name, action, wait));
    }

    private static void cmd(MinecraftClient c, String command, int wait) {
        add("/" + command, wait, () -> c.player.networkHandler.sendChatCommand(command));
    }

    private static void shot(MinecraftClient c, String name, int wait) {
        add("shot " + name, wait, () -> {
            ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), text -> { });
            shots++;
        });
    }

    /** Pauses the script until the terrain around the player has loaded (a dimension change can take a while). */
    private static void settle(MinecraftClient c, String name, int maxTicks) {
        add("await " + name, 2, () -> {
            awaiting = () -> terrainReady(c);
            awaitingName = name;
            // a few slow loads are fine; once the machine keeps timing out the rest of the script must still finish
            awaitLeft = timeouts >= 2 ? Math.min(maxTicks, 60) : Math.min(maxTicks, 900);
            stable = 0;
            loadedFor = 0;
            stableNeeded = 20;
            awaitingTerrain = true;
        });
    }

    /** Pauses the script until the condition holds for {@code needed} ticks in a row (or the time is up). */
    private static void until(String name, int maxTicks, int needed, java.util.function.BooleanSupplier condition) {
        add("until " + name, 1, () -> {
            awaiting = condition;
            awaitingName = name;
            awaitLeft = maxTicks;
            stable = 0;
            stableNeeded = needed;
            awaitingTerrain = false;
        });
    }

    /** Writes the position and the velocity of the player every four ticks for a while (to understand a failed check). */
    private static void trace(MinecraftClient c, String name, int ticks) {
        trace(c, name, ticks, 4);
    }

    /** The same with a line every {@code every} ticks (1: the whole flight tick by tick). */
    private static void trace(MinecraftClient c, String name, int ticks, int every) {
        add("trace " + name, 0, () -> {
            traceName = name;
            traceLeft = ticks;
            traceEvery = every;
        });
    }

    /** A pass/fail check of the physical design (jumps, flights, doors): a failed check fails the whole test. */
    private static void expect(String what, boolean ok, String detail) {
        note((ok ? "CHECK OK " : "CHECK FAILED ") + what + ": " + detail);
        if (!ok) {
            failures++;
        }
    }

    /**
     * The server puts the camera on an aerial vantage point of the structure (see {@code /lewandivka teleport <id> view}).
     * With a step the campaign is first moved there, so the structure's encounter is in the state it is played in.
     */
    private static void tour(MinecraftClient c, String structure, String step) {
        if (step != null) {
            cmd(c, "lewandivka step " + step, 8);
        }
        cmd(c, "lewandivka teleport " + structure + " view", 20);
        settle(c, structure, 2400);
        add("check the camera at " + structure, 1, () -> expect("the camera over " + structure + " is not inside a block", !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        shot(c, "tour_" + structure, 6);
    }

    /**
     * Inside a building, at a spot the blueprint names (see {@code /lewandivka teleport <structure> spot <name>}), looking the way the
     * picture needs: what a visitor finds behind the door.
     */
    private static void room(MinecraftClient c, String structure, String spot, float yaw, float pitch, String name) {
        cmd(c, "lewandivka teleport " + structure + " spot " + spot, 20);
        settle(c, name, 2400);
        cmd(c, "tp @s ~ ~ ~ " + yaw + " " + pitch, 6);
        add("check " + name, 1, () -> expect("the player stands free in " + name, !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        shot(c, "room_" + name, 8);
    }

    /** A picture from a place given by its coordinates (the furniture of the streets has no spot of its own). */
    private static void spot(MinecraftClient c, double x, double y, double z, float yaw, float pitch, String name) {
        cmd(c, "tp @s " + x + " " + y + " " + z + " " + yaw + " " + pitch, 20);
        settle(c, name, 2400);
        add("check " + name, 1, () -> expect("the player stands free at " + name, !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        shot(c, "street_" + name, 8);
    }

    /** Another view from the place where the player stands: what the room has on its other walls. */
    private static void look(MinecraftClient c, float yaw, float pitch, String name) {
        cmd(c, "tp @s ~ ~ ~ " + yaw + " " + pitch, 6);
        shot(c, "room_" + name, 8);
    }

    /**
     * A building from the street: the player stands at the spot of its entrance, turns to {@code yaw} and steps back nine blocks
     * (backwards along the line of sight), so the picture shows the windows of its front.
     */
    private static void facade(MinecraftClient c, String structure, String spot, float yaw, String name) {
        facade(c, structure, spot, yaw, name, 9);
    }

    /** The same with the player {@code back} blocks from the entrance (a tree of the courtyard may stand where nine would put him). */
    private static void facade(MinecraftClient c, String structure, String spot, float yaw, String name, int back) {
        cmd(c, "lewandivka teleport " + structure + " spot " + spot, 20);
        settle(c, name, 2400);
        cmd(c, "tp @s ~ ~ ~ " + yaw + " 6", 4);
        cmd(c, "tp @s ^ ^ ^-" + back, 20);
        add("check " + name, 1, () -> expect("the player stands free in the street at " + name, !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        windows(c, name);
        shot(c, "street_" + name, 8);
    }

    /**
     * The windows as the client has them: blocks of tinted glass in the facade, and the panes and bars within reach (the railings of
     * the balconies, the gratings) have joined each other and the walls, a pane that has joined nothing is only a thin post.
     */
    private static void windows(MinecraftClient c, String name) {
        add("check the windows of " + name, 1, () -> {
            net.minecraft.util.math.BlockPos at = c.player.getBlockPos();
            net.minecraft.util.math.BlockPos.Mutable p = new net.minecraft.util.math.BlockPos.Mutable();
            int glass = 0;
            int panes = 0;
            int alone = 0;
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = -8; dy <= 12; dy++) {
                    for (int dz = -16; dz <= 16; dz++) {
                        net.minecraft.block.BlockState s = c.world.getBlockState(p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz));
                        if (s.isOf(net.minecraft.block.Blocks.LIGHT_BLUE_STAINED_GLASS)) {
                            glass++;
                        } else if (s.getBlock() instanceof net.minecraft.block.PaneBlock) {
                            panes++;
                            if (!s.get(net.minecraft.state.property.Properties.NORTH) && !s.get(net.minecraft.state.property.Properties.EAST)
                                    && !s.get(net.minecraft.state.property.Properties.SOUTH) && !s.get(net.minecraft.state.property.Properties.WEST)) {
                                alone++;
                            }
                        }
                    }
                }
            }
            expect("the facade of " + name + " has windows of tinted glass", glass >= 12, glass + " blocks of tinted glass within reach");
            expect("the panes and bars near " + name + " are joined", alone * 10 <= panes, panes + " panes and bars within reach, " + alone + " of them standing alone");
        });
    }

    /** A citizen or a trader from the front: the player stands at his place, steps back four blocks and looks at him. */
    private static void meet(MinecraftClient c, String structure, String spot, float yaw, String name) {
        cmd(c, "lewandivka teleport " + structure + " spot " + spot, 20);
        settle(c, name, 2400);
        cmd(c, "tp @s ~ ~ ~ " + yaw + " 6", 4);
        cmd(c, "tp @s ^ ^ ^-4", 30);
        add("check " + name, 1, () -> expect("the player stands free by " + name, !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        add("who is in sight at " + name, 1, () -> {
            java.util.List<net.minecraft.entity.mob.MobEntity> people = c.world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class,
                    c.player.getBoundingBox().expand(12.0),
                    e -> e instanceof com.lewandivka.entity.npc.VendorEntity || e instanceof com.lewandivka.entity.npc.CitizenEntity);
            StringBuilder sb = new StringBuilder();
            for (net.minecraft.entity.mob.MobEntity e : people) {
                boolean seen = c.world.raycast(new net.minecraft.world.RaycastContext(c.player.getEyePos(), e.getEyePos(),
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, c.player))
                        .getType() == net.minecraft.util.hit.HitResult.Type.MISS;
                sb.append(' ').append(net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).getPath())
                        .append(String.format("@%.1f", Math.sqrt(e.squaredDistanceTo(c.player)))).append(seen ? "(seen)" : "(hidden)");
            }
            note("people within twelve blocks of " + name + ":" + (people.isEmpty() ? " nobody" : sb));
        });
        shot(c, "people_" + name, 8);
    }

    /**
     * A real right click on the trader nearest to the player (the player stands at his counter): the trade screen of the game must open,
     * with offers in it, and it must close again. The picture shows what the customer sees.
     */
    private static void trade(MinecraftClient c, String name) {
        // what the customer sees at the counter, before he speaks to the trader
        shot(c, "counter_" + name, 6);
        add("trade with " + name, 20, () -> {
            net.minecraft.util.math.Box area = c.player.getBoundingBox().expand(8.0);
            java.util.List<com.lewandivka.entity.npc.VendorEntity> traders = c.world.getEntitiesByClass(com.lewandivka.entity.npc.VendorEntity.class, area, e -> true);
            expect("a trader stands by the counter at " + name, !traders.isEmpty(), traders.size() + " traders within eight blocks of " + c.player.getBlockPos().toShortString());
            if (traders.isEmpty()) {
                return;
            }
            com.lewandivka.entity.npc.VendorEntity nearest = traders.get(0);
            for (com.lewandivka.entity.npc.VendorEntity t : traders) {
                if (t.squaredDistanceTo(c.player) < nearest.squaredDistanceTo(c.player)) {
                    nearest = t;
                }
            }
            boolean seen = c.world.raycast(new net.minecraft.world.RaycastContext(c.player.getEyePos(), nearest.getEyePos(),
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, c.player))
                    .getType() == net.minecraft.util.hit.HitResult.Type.MISS;
            expect("the customer sees the trader at " + name + " over the counter", seen, "from " + c.player.getEyePos() + " to " + nearest.getEyePos());
            screenWanted = true;
            c.interactionManager.interactEntity(c.player, nearest, Hand.MAIN_HAND);
        });
        until("the trade screen of " + name, 100, 6, () -> c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen);
        add("check the trade screen of " + name, 1, () -> {
            boolean open = c.currentScreen instanceof net.minecraft.client.gui.screen.ingame.MerchantScreen;
            int offers = open && c.player.currentScreenHandler instanceof net.minecraft.screen.MerchantScreenHandler h ? h.getRecipes().size() : 0;
            expect("the trade screen of " + name + " is open with goods in it", open && offers > 0, "screen " + (c.currentScreen == null ? "none" : c.currentScreen.getClass().getSimpleName()) + ", " + offers + " offers");
        });
        shot(c, "trade_" + name, 8);
        add("leave the trade screen of " + name, 6, () -> {
            c.setScreen(null);
            screenWanted = false;
        });
    }

    /** A view of the open country from above and a stand on its ground (the generator and the game's features decide what is there). */
    private static void wild(MinecraftClient c, String name, int x, int z) {
        cmd(c, "lewandivka wild " + x + " " + z + " view", 20);
        settle(c, "the " + name + " from above", 2400);
        shot(c, "wild_" + name + "_view", 8);
        cmd(c, "lewandivka wild " + x + " " + z, 20);
        settle(c, "the " + name + " on the ground", 2400);
        add("check the open country at " + name, 1, () -> expect("the player stands free in the open country (" + name + ")", !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        shot(c, "wild_" + name, 8);
    }

    /** Inside a cave of the open country or of the rock under the city: the picture shows what the player finds when he digs. */
    private static void underground(MinecraftClient c, String name, double x, double y, double z, float yaw, float pitch) {
        cmd(c, atIn("lewandivka:district", x, y, z, yaw, pitch), 20);
        settle(c, "the " + name, 2400);
        add("check the " + name, 1, () -> expect("the player stands free in the " + name, !c.player.isInsideWall(),
                "the player is at " + c.player.getBlockPos().toShortString() + " in " + c.world.getRegistryKey().getValue()));
        shot(c, "wild_" + name, 8);
    }

    /**
     * The Nether from the open country. The frame of obsidian is lit with flint and steel by a real click (the game itself lights a
     * portal only in the overworld and in the Nether, see NetherGate), the player goes through and comes out of the Nether through a
     * second frame; the overworld of the ordinary game is not where the story is, so the way must end in the district (see Lifecycle).
     */
    private static void nether(MinecraftClient c) {
        cmd(c, "give @s minecraft:flint_and_steel", 6);
        cmd(c, "lewandivka wild 0 380", 20);
        settle(c, "the site of the portal", 2400);
        add("build the frame", 8, () -> {
            portalSite = c.player.getBlockPos();
            c.player.networkHandler.sendChatCommand("execute at @s run fill ~-1 ~ ~3 ~2 ~4 ~3 minecraft:obsidian");
            c.player.networkHandler.sendChatCommand("execute at @s run fill ~ ~1 ~3 ~1 ~3 ~3 minecraft:air");
        });
        add("light the frame with flint and steel", 14, () -> {
            expect("the hotbar holds flint_and_steel", hold(c, "flint_and_steel"), "the player has no flint and steel in the hotbar");
            BlockPos bottom = portalSite.add(0, 0, 3);
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(bottom).add(0, 0.5, 0), Direction.UP, bottom, false);
            c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        });
        add("check the portal", 6, () -> {
            int n = 0;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    n += c.world.getBlockState(portalSite.add(dx, dy, 3)).isOf(Blocks.NETHER_PORTAL) ? 1 : 0;
                }
            }
            expect("flint and steel lights a portal frame in the district", n == 6, n + " of 6 portal blocks in the frame at " + portalSite.toShortString());
        });
        shot(c, "wild_portal", 6);
        cmd(c, "execute at @s run tp @s ~0.5 ~1 ~3.0", 4);
        until("the Nether", 1200, 3, () -> c.world != null && c.world.getRegistryKey() == World.NETHER);
        settle(c, "the Nether", 2400);
        add("check the Nether", 1, () -> expect("the portal of the district leads to the Nether", c.world.getRegistryKey() == World.NETHER,
                "the player is in " + c.world.getRegistryKey().getValue() + " at " + c.player.getBlockPos().toShortString()));
        shot(c, "nether_arrival", 10);
        // out of the portal and out of its reach until the cooldown has run out, then out of the Nether through a second frame
        cmd(c, "tp @s ~ ~12 ~", 10);
        add("wait for the cooldown of the portal", 400, () -> { });
        cmd(c, "execute at @s run fill ~-1 ~ ~3 ~2 ~4 ~3 minecraft:obsidian", 4);
        cmd(c, "execute at @s run fill ~ ~1 ~3 ~1 ~3 ~3 minecraft:nether_portal[axis=x]", 4);
        cmd(c, "execute at @s run tp @s ~0.5 ~1 ~3.0", 4);
        until("the district again", 1800, 3, () -> in(c, "lewandivka:district"));
        settle(c, "the district after the Nether", 2400);
        add("check the way back", 1, () -> expect("the way out of the Nether ends in the district, on its ground", in(c, "lewandivka:district") && !c.player.isInsideWall(),
                "the player is in " + c.world.getRegistryKey().getValue() + " at " + c.player.getBlockPos().toShortString()));
        shot(c, "nether_return", 8);
    }

    /** Standing at the entrance of a structure (for the ones that lie underground). */
    private static void inside(MinecraftClient c, String structure, String step) {
        if (step != null) {
            cmd(c, "lewandivka step " + step, 8);
        }
        cmd(c, "lewandivka teleport " + structure, 20);
        settle(c, structure, 2400);
        shot(c, "inside_" + structure, 40);
    }

    /** Logs how many creatures of the mod the client knows about (the night population of the district, for example). */
    private static void census(MinecraftClient c, String what) {
        add("census " + what, 2, () -> {
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (Entity e : c.world.getEntities()) {
                var id = Registries.ENTITY_TYPE.getId(e.getType());
                if (id.getNamespace().equals("lewandivka")) {
                    counts.merge(id.getPath(), 1, Integer::sum);
                }
            }
            note("census " + what + ": " + counts);
        });
    }

    private static void show(MinecraftClient c, String name, String... entities) {
        cmd(c, "tp @s ~ ~ ~ 0 8", 10);
        for (int i = 0; i < entities.length; i++) {
            double offset = (i - (entities.length - 1) / 2.0) * 3.2;
            cmd(c, "execute at @s run summon lewandivka:" + entities[i] + " ^" + offset + " ^ ^7 {Silent:1b,PersistenceRequired:1b}", 4);
        }
        shot(c, "mob_" + name, 4);
        for (String e : entities) {
            cmd(c, "kill @e[type=lewandivka:" + e + "]", 2);
        }
    }


    // ------------------------------------------------------------------ physical checks of the level design

    private static String at(double x, double y, double z, float yaw, float pitch) {
        return String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch);
    }

    /** The same teleport, but into a dimension (the script may be anywhere when it needs to be somewhere else). */
    private static String atIn(String dimension, double x, double y, double z, float yaw, float pitch) {
        return "execute in " + dimension + " run " + at(x, y, z, yaw, pitch);
    }

    /** A teleport into Chromandivka (the physical checks all happen there, whatever dimension the server thinks the player is in). */
    private static String atC(double x, double y, double z, float yaw, float pitch) {
        return atIn("lewandivka:chromandivka", x, y, z, yaw, pitch);
    }

    private static Marker marker(String id) {
        Marker m = Structures.marker(id);
        if (m == null) {
            throw new IllegalStateException("no marker " + id);
        }
        return m;
    }

    private static void checkPosition(MinecraftClient c, String what, double x, double y, double z, double rx, double ry, double rz) {
        add("check " + what, 2, () -> {
            Vec3d p = c.player.getPos();
            boolean ok = Math.abs(p.x - x) <= rx && Math.abs(p.y - y) <= ry && Math.abs(p.z - z) <= rz;
            expect(what, ok, String.format(Locale.ROOT, "player at %.1f %.1f %.1f, expected %.1f %.1f %.1f (+-%.1f %.1f %.1f)", p.x, p.y, p.z, x, y, z, rx, ry, rz));
        });
    }

    /**
     * A real player in survival mode uses the springs, the hatches, the dash and the glider of the sky ascent and the tower
     * approach exactly as in play: the checks say whether the geometry of the dungeons and the physics of the game fit.
     */
    private static void physics(MinecraftClient c) {
        cmd(c, "lewandivka ability grant Tester spring_insoles", 4);
        cmd(c, "lewandivka ability grant Tester dash", 4);
        cmd(c, "lewandivka ability grant Tester glider", 4);
        cmd(c, "effect give @s minecraft:resistance 99999 4 true", 4);
        cmd(c, "gamemode survival", 2);

        // 1. the four spring pads of the sky ascent, island by island: the climber walks onto the pad (so he stands when the
        // spring fires) and lets himself be thrown
        String[][] jumps = {{"pad_0", "isle_1", "6"}, {"pad_1", "isle_2", "5"}, {"pad_2", "isle_3", "5"}, {"pad_3", "isle_4", "2.5"}};
        for (String[] j : jumps) {
            Marker pad = marker("sky_ascent:" + j[0]);
            Marker target = marker("sky_ascent:" + j[1]);
            cmd(c, atC(pad.x() + 0.5 - 2.0, pad.y(), pad.z() + 0.5, -90, 0), 4);
            settle(c, "beside " + j[0], 600);
            trace(c, j[0], 90);
            add("walk onto " + j[0], 1, () -> c.options.forwardKey.setPressed(true));
            until("the throw of " + j[0], 80, 1, () -> c.player.getY() > pad.y() + 1.5);
            add("let go of the keys", 1, () -> c.options.forwardKey.setPressed(false));
            until("the landing after " + j[0], 200, 5, () -> c.player.isOnGround());
            checkPosition(c, "spring " + j[0] + " carries the climber to " + j[1], target.x() + 0.5, target.y(), target.z() + 0.5, Double.parseDouble(j[2]), 0.3, 2.5);
        }

        // 2. the hatch of the glass tunnel throws the climber up to the platform of the sky tram: he walks onto it and keeps
        // walking east (a human steers a little in the air; the wind of the tube helps)
        Marker bottom = marker("sky_ascent:tunnel_bottom");
        Marker top = marker("sky_ascent:tunnel_top");
        Marker hatch = marker("sky_ascent:hatch_up");
        cmd(c, atC(bottom.x() + 0.5, bottom.y(), bottom.z() + 0.5, -90, 0), 4);
        settle(c, "beside the tunnel hatch", 600);
        // the hatch launches for a moment and then the climber is back at the bottom: what does the world look like there,
        // for the server and for the client, and what does the server think of the player tick by tick
        for (int dx = -1; dx <= 1; dx++) {
            cmd(c, "execute in lewandivka:chromandivka run lewandivka probe " + (hatch.x() + dx) + " " + (hatch.y() - 2) + " " + (hatch.y() + 45) + " " + hatch.z(), 2);
        }
        add("the columns of the client at the tunnel hatch", 1, () -> {
            for (int dx = -1; dx <= 1; dx++) {
                note("  client column at " + (hatch.x() + dx) + "," + hatch.z() + ": " + rle(c, hatch.x() + dx, hatch.z(), hatch.y() - 2, hatch.y() + 45));
            }
        });
        cmd(c, "lewandivka trace Tester 100", 1);
        trace(c, "tunnel hatch", 100, 1);
        add("walk onto the hatch", 1, () -> c.options.forwardKey.setPressed(true));
        until("the throw of the hatch", 80, 1, () -> c.player.getY() > bottom.y() + 2.0);
        until("the landing on the platform", 260, 5, () -> c.player.isOnGround() && c.player.getY() > top.y() - 0.5);
        add("let go of the keys", 1, () -> c.options.forwardKey.setPressed(false));
        checkPosition(c, "the tunnel hatch carries the climber to the platform", top.x() + 1.5, top.y(), top.z() + 0.5, 3.5, 0.3, 2.5);

        // 3. the spring shaft of the tower approach ends on the glider deck
        Marker shaft = marker("tower_approach:shaft_bottom");
        Marker deck = marker("tower_approach:glide_start");
        cmd(c, atC(shaft.x() + 0.5, shaft.y(), shaft.z() + 2.5, 180, 0), 4);
        settle(c, "beside the shaft hatch", 600);
        trace(c, "shaft hatch", 120);
        cmd(c, atC(shaft.x() + 0.5, shaft.y(), shaft.z() + 0.5, 180, 0), 160);
        checkPosition(c, "the shaft hatch carries the climber to the glider deck", deck.x(), deck.y(), deck.z() + 1, 7, 1.6, 6.5);

        // 3b. the hatch in the middle of the tower's fourth floor throws the climber up through the hole of the fifth floor onto
        // its ledge (the wind of the shaft carries him sideways; he keeps walking east like a player who steers)
        Marker f4 = marker("tower:hatch_f4_top");
        Marker ledge = marker("tower:f4_ledge");
        cmd(c, atC(f4.x() + 0.5 - 2.0, f4.y(), f4.z() + 0.5, -90, 0), 4);
        settle(c, "beside the tower hatch", 900);
        trace(c, "tower hatch", 100, 1);
        add("walk onto the tower hatch", 1, () -> c.options.forwardKey.setPressed(true));
        until("the throw of the tower hatch", 80, 1, () -> c.player.getY() > f4.y() + 3.0);
        until("the landing on the ledge", 300, 5, () -> c.player.isOnGround() && c.player.getY() > ledge.y() - 0.5);
        add("let go of the keys", 1, () -> c.options.forwardKey.setPressed(false));
        checkPosition(c, "the tower hatch carries the climber to the ledge of the fifth floor", ledge.x() + 0.5, ledge.y(), ledge.z() + 0.5, 4.5, 0.3, 3.0);

        // 4. a dash opens the first door of the dash corridor
        Marker door = marker("tower_approach:dash_door_1");
        cmd(c, atC(door.x() + 2.5, door.y(), door.z() + 3.6, 180, 0), 4);
        settle(c, "in front of the first dash door", 600);
        trace(c, "dash door", 40);
        add("dash", 40, () -> ClientNet.requestAbility(Ability.DASH, true));
        add("check the first dash door", 2, () -> {
            where(c, "after the dash");
            BlockPos middle = new BlockPos(door.x() + 2, door.y() + 1, door.z());
            expect("a dash opens the first door", c.world.getBlockState(middle).isAir(), "block at the door: " + c.world.getBlockState(middle).getBlock().getTranslationKey());
        });

        // 5. the dash pit of the shelter is seven blocks wide: sprint, jump, dash in mid-air
        Marker near = marker("shelter:platform_a");
        Marker far = marker("shelter:platform_b");
        Marker pit = marker("shelter:pit");
        cmd(c, atC(near.x() + 0.5, near.y(), near.z() + 0.5, 0, 0), 4);
        settle(c, "before the dash pit", 600);
        add("wait for the dash to recharge", 80, () -> { });
        add("run to the pit", 1, () -> {
            c.options.forwardKey.setPressed(true);
            c.options.sprintKey.setPressed(true);
        });
        trace(c, "pit", 60);
        until("the edge of the pit", 140, 1, () -> c.player.getZ() > pit.z() - 0.5);
        add("jump over the pit", 3, () -> c.options.jumpKey.setPressed(true));
        add("release the jump", 1, () -> c.options.jumpKey.setPressed(false));
        add("dash in mid-air", 1, () -> ClientNet.requestAbility(Ability.DASH, true));
        until("the landing behind the pit", 300, 5, () -> c.player.isOnGround() && c.player.getZ() > pit.z() + 0.5);
        add("stop running", 1, () -> {
            c.options.forwardKey.setPressed(false);
            c.options.sprintKey.setPressed(false);
        });
        // behind the pit the platform goes on for a good twelve blocks: landing anywhere on it is crossing the pit
        checkPosition(c, "the dash carries the player over the pit of the shelter", far.x(), far.y(), far.z() + 5.5, 9, 1.6, 6.5);

        // 6. the glider carries the player over the chasm to the far platform: run, jump, jump again in mid-air
        Marker start = marker("tower_approach:glide_start");
        Marker end = marker("tower_approach:glide_end");
        cmd(c, atC(start.x() + 0.5, start.y(), start.z() + 0.5, 180, 0), 4);
        settle(c, "on the glider deck", 600);
        add("run to the edge", 1, () -> {
            c.options.forwardKey.setPressed(true);
            c.options.sprintKey.setPressed(true);
        });
        // the deck ends two blocks in front of the marker (north is -z)
        double edgeZ = start.z() - 2.0;
        trace(c, "glide", 240);
        until("the edge of the deck", 140, 1, () -> c.player.getZ() < edgeZ + 0.5);
        add("jump", 2, () -> c.options.jumpKey.setPressed(true));
        add("release the jump", 1, () -> c.options.jumpKey.setPressed(false));
        until("the fall begins", 80, 1, () -> c.player.getVelocity().y < -0.05 && c.player.fallDistance > 0.7f);
        add("press jump in mid-air", 2, () -> c.options.jumpKey.setPressed(true));
        add("release the second jump", 1, () -> c.options.jumpKey.setPressed(false));
        until("the landing", 400, 5, () -> c.player.isOnGround());
        add("stop running", 1, () -> {
            c.options.forwardKey.setPressed(false);
            c.options.sprintKey.setPressed(false);
        });
        // the script holds the forward key all the way (a glide is faster that way), so the glider carries him past the platform
        // and down the stairs to the gate: anywhere on the far side, not in the chasm, is crossing it
        checkPosition(c, "the glider carries the player over the chasm to the far side", end.x(), end.y() - 3, end.z() - 8, 9, 6.0, 12.0);

        cmd(c, "gamemode creative", 4);
        cmd(c, "effect clear @s minecraft:resistance", 4);
    }


    /**
     * The sky tram: the climber steps onto the platform, the tram comes, takes him on board and really drives the two and a
     * half minutes of the route (a hundred and fifty seconds of waiting for the script); on the other side he stands on the
     * arrival rails of the depot and the story goes on.
     */
    private static void skyRide(MinecraftClient c) {
        Marker platform = marker("sky_ascent:stop_platform");
        Marker arrive = marker("sky_depot:tram_arrive");
        cmd(c, "gamemode creative", 4);
        cmd(c, "lewandivka step sky_ascent", 8);
        cmd(c, atIn("lewandivka:chromandivka", platform.x() + 0.5, platform.y(), platform.z() + 0.5, 90, 0), 4);
        settle(c, "on the sky platform", 900);
        until("the tram carries the climber", 600, 1, () -> c.player.hasVehicle());
        add("check the boarding", 1, () -> expect("the tram takes the player on board", c.player.hasVehicle(), "the player is not riding"));
        until("the story knows the way", 100, 1, () -> ClientState.step == QuestStep.SKY_RIDE || ClientState.step.isAfter(QuestStep.SKY_RIDE));
        trace(c, "sky ride", 120);
        until("the arrival at the depot", 4800, 1, () -> ClientState.step == QuestStep.SKY_SWITCHES);
        add("check the arrival", 20, () -> {
            where(c, "the end of the sky ride");
            expect("the sky ride ends at the depot", ClientState.step == QuestStep.SKY_SWITCHES, "the step is " + ClientState.step.key());
            expect("the passenger is put on the arrival rails of the depot", c.player.squaredDistanceTo(arrive.x() + 0.5, arrive.y(), arrive.z() + 0.5) < 15 * 15,
                    "the player is at " + c.player.getBlockPos().toShortString() + ", the rails at " + arrive.pos().toShortString());
        });
        shot(c, "sky_depot_arrival", 4);
    }

    /**
     * The five bosses in their own arenas: the campaign is moved to the step of the boss, the boss is summoned where the
     * dungeon flow would put it (the fight really starts, adds and all), then it is killed and the story must go on:
     * the next step, the ring fragment and, where there is one, the ability the boss gives.
     */
    private static void bossRewards(MinecraftClient c) {
        // structure, boss, step before, step after, ability (or "")
        String[][] chain = {
                {"rainbow_garage", "garage_king", "rg_boss", "sh_dash", "DASH"},
                {"shelter", "collar_collector", "sh_boss", "aq_find", ""},
                {"aquapark", "lady_vortex", "aq_boss", "sky_ascent", "SPRING_INSOLES"},
                {"sky_depot", "conductor", "sky_boss", "tower_ring", "GLIDER"},
                {"tower", "colorless_head", "final_fight", "epi_return", ""},
        };
        for (String[] b : chain) {
            Marker spawn = marker(b[0] + ":boss_spawn");
            int[] rings = new int[1];
            cmd(c, "lewandivka step " + b[2], 10);
            cmd(c, "lewandivka teleport " + b[0] + " view", 20);
            settle(c, b[1] + " arena", 900);
            add("remember the ring", 1, () -> rings[0] = ClientState.rings);
            cmd(c, String.format(Locale.ROOT, "summon lewandivka:%s %.1f %.1f %.1f", b[1], spawn.x() + 0.5, (double) spawn.y(), spawn.z() + 0.5), 100);
            shot(c, "boss_" + b[1], 4);
            cmd(c, "kill @e[type=lewandivka:" + b[1] + "]", 60);
            add("check the reward of " + b[1], 2, () -> {
                expect(b[1] + " leads to " + b[3], ClientState.step.key().equals(b[3]), "the step is " + ClientState.step.key());
                if (!b[4].isEmpty()) {
                    expect(b[1] + " gives " + b[4], ClientState.has(Ability.valueOf(b[4])), "ability mask " + ClientState.abilityMask);
                }
                if (!b[1].equals("colorless_head")) {
                    expect(b[1] + " restores a ring fragment", ClientState.rings == rings[0] + 1, "rings " + rings[0] + " -> " + ClientState.rings);
                }
            });
        }
    }


    // ------------------------------------------------------------------ playing the story with hands

    /** Puts the item of the mod into the hand (it was given with /give, so it is in the hotbar). */
    private static boolean hold(MinecraftClient c, String itemId) {
        var inventory = c.player.getInventory();
        for (int i = 0; i < 9; i++) {
            var stack = inventory.getStack(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().equals(itemId)) {
                inventory.selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    /** Whether the player carries the item anywhere (hotbar, inventory, off hand). */
    private static boolean has(MinecraftClient c, String itemId) {
        var inventory = c.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (!stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The quest-item safety net must hand out what the step needs without being asked: checks that the item arrived and,
     * when it did not, gives it so that the rest of the scenario can go on.
     */
    private static void expectDelivered(MinecraftClient c, String itemId, String why) {
        add("check the safety net delivered " + itemId, 1, () -> {
            boolean ok = has(c, itemId);
            expect("the safety net hands out " + itemId + " " + why, ok, ok ? "it is in the inventory" : "it is missing");
            if (!ok) {
                c.player.networkHandler.sendChatCommand("give @s lewandivka:" + itemId);
            }
        });
        add("let the item arrive", 6, () -> { });
    }

    /** Right click in the air with the item. */
    private static void useItem(MinecraftClient c, String itemId) {
        add("use " + itemId, 12, () -> {
            expect("the hotbar holds " + itemId, hold(c, itemId), "the player has no " + itemId + " in the hotbar");
            c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
        });
    }

    /** Right click on the block at a position with the item (the real packet, the real server-side checks). */
    private static void useItemOn(MinecraftClient c, String itemId, BlockPos pos) {
        add("use " + itemId + " on " + pos.toShortString(), 14, () -> {
            expect("the hotbar holds " + itemId, hold(c, itemId), "the player has no " + itemId + " in the hotbar");
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        });
    }

    /** How many creatures of the mod with this id the client knows about. */
    private static int count(MinecraftClient c, String entityId) {
        int n = 0;
        for (Entity e : c.world.getEntities()) {
            var id = Registries.ENTITY_TYPE.getId(e.getType());
            if (id.getNamespace().equals("lewandivka") && id.getPath().equals(entityId)) {
                n++;
            }
        }
        return n;
    }

    private static boolean in(MinecraftClient c, String dimension) {
        return c.world != null && c.world.getRegistryKey().getValue().toString().equals(dimension);
    }

    /**
     * The end of the first act with real clicks: build the kiosk on its foundation, swallow the Chroma tablet (the crossing
     * to Chromandivka, the compass), put the four artifacts on the pedestals of the base, walk through the coloured portal.
     */
    private static void crossing(MinecraftClient c) {
        cmd(c, "gamemode creative", 4);
        // /clear takes everything: the notebook (needed at every step) has to come back by itself
        cmd(c, "clear @s", 4);
        add("wait for the safety net", 120, () -> { });
        expectDelivered(c, "district_notebook", "after /clear");

        // the kiosk: placed on the painted foundation it becomes the shop of Mr. Shlahbaum
        Marker spot = marker("district:kiosk_spot");
        cmd(c, "lewandivka step place_kiosk", 10);
        cmd(c, atIn("lewandivka:district", spot.x() + 0.5, spot.y() + 1.0, spot.z() + 3.5, 180, 0), 4);
        settle(c, "at the kiosk foundation", 900);
        cmd(c, "give @s lewandivka:abandoned_kiosk", 6);
        useItemOn(c, "abandoned_kiosk", spot.pos());   // the marker is the painted foundation block itself
        add("let the shopkeeper arrive", 60, () -> { });
        cmd(c, "lewandivka creatures", 6);
        add("check the kiosk", 2, () -> {
            expect("the placed kiosk starts the talk with Mr. Shlahbaum", ClientState.step == QuestStep.TALK_SHLAHBAUM,
                    "the step is " + ClientState.step.key());
            expect("Mr. Shlahbaum appears next to the kiosk exactly once", count(c, "pan_shlahbaum") == 1,
                    "there are " + count(c, "pan_shlahbaum"));
        });
        census(c, "after the kiosk");

        // the Chroma tablet, swallowed by the whole (one-person) party, carries everybody over
        cmd(c, "lewandivka step chroma_take", 10);
        add("wait for the safety net", 100, () -> { });
        expectDelivered(c, "chroma_tablet", "(one for every participant)");
        useItem(c, "chroma_tablet");
        until("the crossing to Chromandivka", 1500, 30, () -> in(c, "lewandivka:chromandivka") && ClientState.step.isAtLeast(QuestStep.BASE_WAKE));
        add("check the crossing", 2, () -> expect("the Chroma tablet leads to the base of Chromandivka", in(c, "lewandivka:chromandivka") && ClientState.step.isAtLeast(QuestStep.BASE_WAKE),
                "dimension " + c.world.getRegistryKey().getValue() + ", step " + ClientState.step.key()));
        until("the compass arrives", 600, 1, () -> ClientState.step == QuestStep.BASE_PEDESTALS);
        add("check the compass", 2, () -> expect("the base gives the compass and asks for the artifacts", ClientState.step == QuestStep.BASE_PEDESTALS, "the step is " + ClientState.step.key()));

        // the four pedestals of the base
        add("check the portal is closed", 1, () -> expect("the portal stays closed until the artifacts are placed", !ClientState.portal, "portal " + ClientState.portal));
        String[][] artifacts = {{"kettle", "magic_kettle"}, {"package", "package"}, {"composter", "ticket_composter"}, {"token", "district_token"}};
        add("wait for the safety net", 100, () -> { });
        for (String[] a : artifacts) {
            expectDelivered(c, a[1], "for the pedestal");
        }
        for (String[] a : artifacts) {
            Marker pedestal = marker("base:pedestal_" + a[0]);
            cmd(c, atIn("lewandivka:chromandivka", pedestal.x() + 0.5, pedestal.y(), pedestal.z() + 2.5, 180, 0), 6);
            useItemOn(c, a[1], pedestal.pos());
        }
        add("let the portal open", 60, () -> { });
        add("check the portal", 2, () -> expect("four artifacts open the coloured portal", ClientState.portal && ClientState.step == QuestStep.RG_TRAVEL,
                "portal " + ClientState.portal + ", step " + ClientState.step.key()));

        // through the portal, back to Lewandivka
        Marker plane = marker("base:portal_plane");
        cmd(c, atIn("lewandivka:chromandivka", plane.x() + 0.5, plane.y() + 0.1, plane.z() + 0.5, 90, 0), 10);
        until("the way back to Lewandivka", 400, 5, () -> in(c, "lewandivka:district"));
        add("check the portal trip", 2, () -> expect("the coloured portal leads back to Lewandivka", in(c, "lewandivka:district"),
                "dimension " + c.world.getRegistryKey().getValue()));
    }

    // ------------------------------------------------------------------ the cars: a world of the game's own

    private static final java.util.Map<String, java.util.concurrent.CompletableFuture<Object>> ASKED = new java.util.HashMap<>();
    private static int promptTicks;

    /**
     * A screen in front of the world (a warning that the world uses experimental settings, a question about a backup) is answered with
     * "yes": the cars test plays a world that was made by another game. Waits a while first, the loading screens have no buttons anyway.
     */
    private static void clickThrough(MinecraftClient c) {
        net.minecraft.client.gui.screen.Screen screen = c.currentScreen;
        if (screen == null || screen instanceof net.minecraft.client.gui.screen.TitleScreen || screen instanceof DownloadingTerrainScreen) {
            promptTicks = 0;
            return;
        }
        promptTicks++;
        if (promptTicks < 100 || promptTicks % 40 != 0) {
            return;
        }
        for (String word : new String[] {"proceed", "without backup", "yes", "continue", "play", "load"}) {
            for (net.minecraft.client.gui.Element e : screen.children()) {
                if (e instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.active
                        && b.getMessage().getString().toLowerCase(Locale.ROOT).contains(word)) {
                    note("a screen in front of the world (" + screen.getClass().getSimpleName() + "): pressed '" + b.getMessage().getString() + "'");
                    b.onPress();
                    return;
                }
            }
        }
        note("a screen in front of the world (" + screen.getClass().getSimpleName() + ") with no button to press");
    }

    /** Asks the server inside the game for something, on its own thread; {@link #answered} says when the answer is there. */
    private static void ask(MinecraftClient c, String key, java.util.function.Function<net.minecraft.server.MinecraftServer, Object> question) {
        java.util.concurrent.CompletableFuture<Object> future = new java.util.concurrent.CompletableFuture<>();
        ASKED.put(key, future);
        net.minecraft.server.MinecraftServer server = c.getServer();
        if (server == null) {
            future.completeExceptionally(new IllegalStateException("the game has no server of its own"));
            return;
        }
        server.execute(() -> {
            try {
                future.complete(question.apply(server));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
    }

    private static boolean answered(String key) {
        java.util.concurrent.CompletableFuture<Object> f = ASKED.get(key);
        return f != null && f.isDone();
    }

    private static Object answer(String key) {
        java.util.concurrent.CompletableFuture<Object> f = ASKED.get(key);
        try {
            return f == null ? null : f.getNow(null);
        } catch (RuntimeException e) {
            note("the question '" + key + "' failed: " + e);
            return null;
        }
    }

    /** A check of something the test only reports (it does not fail the run): a result for the reader of the log. */
    private static void soft(String what, boolean ok, String detail) {
        note((ok ? "SOFT CHECK OK " : "SOFT CHECK FAILED ") + what + ": " + detail);
    }

    private static com.lewandivka.core.world.gen.Cars.Spot carSpot(String marker) {
        for (com.lewandivka.core.world.gen.Cars.Spot s : com.lewandivka.core.world.gen.Cars.spots(com.lewandivka.core.world.gen.DistrictPlan.get())) {
            if (s.marker().equals(marker)) {
                return s;
            }
        }
        return null;
    }

    private static boolean isCar(Entity e) {
        return Registries.ENTITY_TYPE.getId(e.getType()).getNamespace().equals(com.lewandivka.core.world.gen.Cars.MOD);
    }

    /**
     * The picture of a car from a vantage point {@code dx, dy, dz} blocks from its middle, looking at it; the player is in the picture's
     * place when it is taken. Checks that the car of the mod stands there and that no block of the car built of blocks is left.
     */
    private static void carView(MinecraftClient c, String marker, String name, double dx, double dy, double dz) {
        com.lewandivka.core.world.gen.Cars.Spot car = carSpot(marker);
        if (car == null) {
            add("no car " + marker, 1, () -> expect("the plan has a car at " + marker, false, "no such place"));
            return;
        }
        double cx = car.x() + 0.5;
        double cy = car.y();
        double cz = car.z() + 0.5;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy - 1.0, Math.hypot(dx, dz)));
        cmd(c, atIn("lewandivka:district", cx + dx, cy + dy, cz + dz, yaw, pitch), 20);
        settle(c, name, 2400);
        add("check the car at " + name, 1, () -> {
            expect("the player stands free at the car " + name, !c.player.isInsideWall(), "the player is at " + c.player.getBlockPos().toShortString());
            java.util.List<Entity> near = c.world.getOtherEntities(null, net.minecraft.util.math.Box.of(new Vec3d(cx, cy + 1.0, cz), 8, 6, 8), AutoTest::isCar);
            double best = Double.MAX_VALUE;
            String kind = "none";
            for (Entity e : near) {
                double d = Math.hypot(e.getX() - cx, e.getZ() - cz);
                if (d < best) {
                    best = d;
                    kind = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
                }
            }
            expect("a car of Trep's Cars stands at " + name, best <= 1.5, "the nearest is " + kind + String.format(Locale.ROOT, " %.1f blocks from %s", best, marker));
            int leftover = 0;
            int colon = marker.indexOf(':');
            Structures.Site site = Structures.site(marker.substring(0, colon));
            if (site != null) {
                com.lewandivka.core.structure.StructurePlacement p = site.placement();
                int[] fp = car.footprint();
                for (int x = fp[0]; x <= fp[2]; x++) {
                    for (int z = fp[1]; z <= fp[3]; z++) {
                        for (int y = car.y(); y < car.y() + com.lewandivka.core.world.gen.Cars.HEIGHT; y++) {
                            String key = p.blueprint().keyAt(x - p.x(), y - p.y(), z - p.z());
                            if (key != null && com.lewandivka.core.structure.Keys.isUnlessMod(key) && !c.world.getBlockState(new BlockPos(x, y, z)).isAir()) {
                                leftover++;
                            }
                        }
                    }
                }
            }
            expect("no block of the car built of blocks is left at " + name, leftover == 0, leftover + " blocks of the footprint of " + marker);
        });
        shot(c, "cars_" + name, 10);
    }

    /** Gets into the car of a place with a real click and pushes the forward key: the mod's own driving, on the server inside the game. */
    private static void drive(MinecraftClient c, String marker, String name) {
        com.lewandivka.core.world.gen.Cars.Spot car = carSpot(marker);
        if (car == null) {
            add("no car " + marker, 1, () -> expect("the plan has a car at " + marker, false, "no such place"));
            return;
        }
        // beside the car, three blocks to the side (the cars of the streets stand along x), looking at it
        cmd(c, atIn("lewandivka:district", car.x() + 0.5, car.y(), car.z() + 3.5, 180, 8), 20);
        settle(c, "beside the car to drive", 2400);
        Entity[] ref = new Entity[1];
        Vec3d[] start = new Vec3d[1];
        add("get into the car", 30, () -> {
            Entity best = null;
            for (Entity e : c.world.getOtherEntities(null, c.player.getBoundingBox().expand(6.0), AutoTest::isCar)) {
                if (best == null || e.squaredDistanceTo(c.player) < best.squaredDistanceTo(c.player)) {
                    best = e;
                }
            }
            soft("a car is within reach of the player", best != null, best == null ? "none within six blocks" : Registries.ENTITY_TYPE.getId(best.getType()).getPath());
            if (best != null) {
                ref[0] = best;
                start[0] = best.getPos();
                c.interactionManager.interactEntity(c.player, best, Hand.MAIN_HAND);
            }
        });
        until("the player sits in the car", 80, 3, () -> c.player.hasVehicle());
        add("check the seat", 1, () -> soft("a click on the car seats the player in it", c.player.hasVehicle(), "vehicle " + c.player.getVehicle()));
        shot(c, "cars_" + name + "_seated", 4);
        add("push forward", 1, () -> c.options.forwardKey.setPressed(true));
        until("the car has driven away", 160, 1, () -> ref[0] != null && ref[0].getPos().distanceTo(start[0]) > 8.0);
        shot(c, "cars_" + name + "_driving", 2);
        add("let go of the keys", 1, () -> c.options.forwardKey.setPressed(false));
        add("check the drive", 1, () -> soft("the car drives when the player pushes forward", ref[0] != null && ref[0].getPos().distanceTo(start[0]) > 5.0,
                ref[0] == null ? "no car" : String.format(Locale.ROOT, "moved %.1f blocks", ref[0].getPos().distanceTo(start[0]))));
        cmd(c, "ride @s dismount", 10);
    }

    /**
     * The second run of the client test: the game plays a world of its own (so its server is inside the game and can run the mod of the
     * cars, which a dedicated server cannot), a new world made by the plan with the mod in it. Some places get the cars built of blocks first,
     * the way a world that was made without the mod has them: the cars of the mod must replace them.
     */
    private static void buildCars(MinecraftClient c) {
        add("settle", 300, () -> { });
        add("a world of the game's own", 1, () -> expect("the game plays a world of its own (its server is inside the game)", c.getServer() != null && c.isInSingleplayer(),
                "server " + c.getServer()));
        add("the mods", 1, () -> {
            net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            String gecko = loader.getModContainer("geckolib").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("none");
            String cars = loader.getModContainer("trepscars").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("none");
            expect("Trep's Cars is loaded next to the mod", !cars.equals("none"), "trepscars " + cars + ", geckolib " + gecko);
        });
        add("allow the commands", 20, () -> {
            net.minecraft.server.MinecraftServer server = c.getServer();
            if (server != null) {
                server.execute(() -> {
                    net.minecraft.server.network.ServerPlayerEntity sp = server.getPlayerManager().getPlayer(c.player.getUuid());
                    if (sp != null) {
                        server.getPlayerManager().addToOperators(sp.getGameProfile());
                    }
                });
            }
        });
        cmd(c, "gamemode creative", 10);
        cmd(c, "effect give @s minecraft:night_vision 99999 0 true", 6);
        add("where is the player", 1, () -> {
            where(c, "the first arrival in the world of its own");
            expect("the first arrival is not inside a block", !c.player.isInsideWall(), "the player is at " + c.player.getBlockPos().toShortString());
        });
        shot(c, "cars_00_arrival", 10);

        // two places get the cars built of blocks, the way an older world has them
        java.util.List<String> old = java.util.List.of("car_n1:car", "car_s2:car");
        add("build two cars of blocks", 1, () -> ask(c, "old", server -> com.lewandivka.quest.CarPark.buildBlockCars(server, old)));
        until("the cars of blocks are built", 1800, 1, () -> answered("old"));
        add("check the cars of blocks", 1, () -> {
            Object o = answer("old");
            expect("the cars of blocks were built", o instanceof Integer n && n > 20, String.valueOf(o));
        });
        // every place of a car gets its car of the mod (the part of the district that is not generated yet is generated with the mod in the game)
        add("make the cars", 1, () -> ask(c, "cars", server -> com.lewandivka.quest.CarPark.makeAll(server)));
        until("the cars are made", 2400, 1, () -> answered("cars"));
        add("check the cars", 1, () -> {
            Object o = answer("cars");
            expect("the cars were made", o instanceof com.lewandivka.quest.CarPark.Made, String.valueOf(o));
            if (o instanceof com.lewandivka.quest.CarPark.Made m) {
                note("cars: " + m.text());
                expect("every place of a car has its car of Trep's Cars", m.available() && m.missing() == 0 && m.made() + m.already() == m.places(), m.text());
                expect("the cars are of several kinds", m.kinds().size() >= 3, m.kinds().toString());
                Object put = answer("old");
                expect("the cars of blocks were taken away", put instanceof Integer n && m.blocks() >= n, "put up " + put + ", taken away " + m.blocks());
            }
        });
        add("count again", 1, () -> ask(c, "cars2", server -> com.lewandivka.quest.CarPark.makeAll(server)));
        until("the cars are counted again", 600, 1, () -> answered("cars2"));
        add("check that no car is made twice", 1, () -> {
            Object o = answer("cars2");
            boolean ok = o instanceof com.lewandivka.quest.CarPark.Made m && m.made() == 0 && m.already() == m.places();
            expect("no car is made twice", ok, o instanceof com.lewandivka.quest.CarPark.Made m2 ? m2.text() : String.valueOf(o));
        });

        // the pictures: two places that had cars of blocks, two that were generated with the mod in the game
        carView(c, "car_n1:car", "street_replaced", -5, 5, 7);
        carView(c, "car_n1:car", "street_replaced_top", 0, 14, 1);
        carView(c, "car_s2:car", "street_replaced_2", 5, 5, -7);
        carView(c, "car_n2:car", "street_new", -5, 5, 7);
        carView(c, "car_s1:car", "street_new_2", 5, 5, -7);
        carView(c, "car_t:car", "street_new_3", -7, 5, 5);
        carView(c, "garage13:car.car", "garage13", 0, 4, -6);
        carView(c, "garages_n2:car.car", "garages_row", 0, 4, -6);
        drive(c, "car_n2:car", "street");
        add("cars known to the client", 2, () -> {
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (Entity e : c.world.getEntities()) {
                if (isCar(e)) {
                    counts.merge(Registries.ENTITY_TYPE.getId(e.getType()).getPath(), 1, Integer::sum);
                }
            }
            note("cars known to the client at the end: " + counts);
        });
        add("the end", 4, () -> { });
    }

    private static void build(MinecraftClient c) {
        if (CARS_MODE) {
            buildCars(c);
            return;
        }
        add("settle", 180, () -> { });
        cmd(c, "gamemode creative", 10);
        int[] spawn = com.lewandivka.core.world.gen.DistrictPlan.get().spawn();
        add("where is the player", 1, () -> {
            where(c, "the first arrival");
            note("  client column at the spawn " + spawn[0] + "," + spawn[2] + ": " + rle(c, spawn[0], spawn[2], 0, 140));
            chunkGrid(c, "at the first arrival");
            expect("the first arrival is not inside a block", !c.player.isInsideWall(), "the player is at " + c.player.getBlockPos().toShortString());
        });
        // what the server has in the same column in the district and (to tell stale overworld chunks from anything else) in the overworld
        cmd(c, "execute in lewandivka:district run lewandivka probe " + spawn[0] + " 0 140 " + spawn[2], 4);
        cmd(c, "execute in minecraft:overworld run lewandivka probe " + spawn[0] + " -64 140 " + spawn[2], 4);
        shot(c, "01_district_spawn", 10);
        cmd(c, "lewandivka status", 10);
        add("notebook", 14, () -> {
            screenWanted = true;
            c.setScreen(new NotebookScreen());
        });
        shot(c, "02_notebook", 4);
        add("close", 6, () -> {
            c.setScreen(null);
            screenWanted = false;
        });

        // the first night: the district fills with gopniks around the player (the service that spawns them runs for real)
        cmd(c, "lewandivka step collect_tokens", 8);
        cmd(c, "time set 18000", 8);
        add("the night population arrives", 500, () -> { });
        census(c, "first night");
        shot(c, "03_first_night", 4);

        // night vision only for the tours: the district is held at night (see TimeControl), the shots must show the buildings
        cmd(c, "effect give @s minecraft:night_vision 99999 0 true", 6);
        tour(c, "tram_stop", "tram_fight");
        tour(c, "old_shop", null);
        // the furniture of the streets: a park bench (-48..-46, -36, the sitter looks north) and a table with chairs (-43..-41, -36..-34)
        spot(c, -44.5, 66.5, -41.5, 0, 16, "bench_and_cafe");
        spot(c, -52.5, 66.5, -32.5, 237, 14, "bench_from_the_side");
        add("grid at the old shop", 1, () -> chunkGrid(c, "at the old shop"));
        tour(c, "garage13", "garage_panels");
        for (String s : List.of("block_a", "house_ne0", "kindergarten", "playground_north", "tram_depot")) {
            tour(c, s, null);
        }
        tour(c, "kiosk_foundation", "place_kiosk");
        // the insides of the buildings (behind the doors there are stairwells, flats, rooms with furniture)
        room(c, "block_a", "entrance", 180, 8, "block_a_lobby");
        room(c, "block_a", "flat_a", 0, 18, "block_a_flat_1");
        look(c, 90, 18, "block_a_flat_1_w");
        look(c, 180, 18, "block_a_flat_1_n");
        look(c, 270, 18, "block_a_flat_1_e");
        room(c, "block_a", "flat_b", 0, 18, "block_a_flat_3");
        look(c, 90, 18, "block_a_flat_3_w");
        look(c, 180, 18, "block_a_flat_3_n");
        look(c, 270, 18, "block_a_flat_3_e");
        room(c, "block_a", "landing", 180, 12, "block_a_stairs");
        room(c, "block_a", "roof", 0, 15, "block_a_roof");
        room(c, "house_ne0", "entrance", 180, 8, "house_hall");
        room(c, "house_ne0", "living", 45, 14, "house_living");
        look(c, 135, 14, "house_living_2");
        look(c, 225, 14, "house_living_3");
        look(c, 315, 14, "house_living_4");
        room(c, "house_ne0", "bedroom", 225, 14, "house_bedroom");
        room(c, "kindergarten", "entrance", 0, 8, "kinder_hall");
        room(c, "kindergarten", "playroom", 45, 14, "kinder_playroom");
        room(c, "kindergarten", "bedroom", 225, 14, "kinder_bedroom");
        // the windows of the buildings, seen from the street
        facade(c, "block_a", "entrance", 180, "block_a", 5);
        facade(c, "house_ne0", "entrance", 180, "house_ne0");
        facade(c, "kindergarten", "entrance", 0, "kindergarten");
        tour(c, "school", null);
        // the people of the district: the traders of the market (every one of them is made now) and some of the citizens
        cmd(c, "lewandivka populace", 80);
        meet(c, "stall_0", "customer", 0, "baker");
        cmd(c, "lewandivka teleport stall_0 spot customer", 20);
        settle(c, "the counter of the baker", 600);
        trade(c, "baker");
        meet(c, "stall_3", "customer", 0, "handyman");
        meet(c, "stall_5", "customer", 0, "fishmonger");
        meet(c, "district", "citizen_3", 180, "worker_street");
        meet(c, "district", "citizen_28", 180, "kid_playground");
        // the open country around the city (generated by the plan and decorated by the game's own features)
        wild(c, "forest", 0, 380);
        wild(c, "west", -450, -100);
        wild(c, "coast", 520, 300);
        // a portal of the Nether in the open country and the way back from there
        nether(c);
        // the edge of the city seen from the open country (no wall, no step: the flat ground grows into the land)
        cmd(c, atIn("lewandivka:district", 235.5, 96, 0.5, 90, 22), 20);
        settle(c, "the edge of the city", 2400);
        shot(c, "wild_city_edge", 8);
        // underground: caves found offline in the noise (they are a pure function of the position)
        underground(c, "cavern", 361.5, 50, 514.5, 90, 12);
        // ... and under the streets of the city: the cavern of the deep, with its lake of lava, 110 blocks under the pavement
        underground(c, "lava_cave", 142.5, -51, 128.5, -90, 18);
        // mobs of the district
        cmd(c, "lewandivka teleport tram_stop", 20);
        settle(c, "mobs", 2400);
        cmd(c, "tp @s ~ ~2 ~", 10);
        show(c, "gopniks", "gopnik", "seed_thrower", "senior_yard_gopnik");
        show(c, "npcs", "pan_shlahbaum", "debtor", "fare_dodger", "fare_dodger_leader");
        show(c, "cats", "chinazik", "metadonna", "mechanic_minion");
        show(c, "vehicles", "arena_tram", "sky_tram");
        show(c, "garage_king", "garage_king");
        show(c, "collar_collector", "collar_collector");
        show(c, "lady_vortex", "lady_vortex");
        show(c, "conductor", "conductor");
        show(c, "colorless_head", "colorless_head");

        // the other side
        tour(c, "base", "base_pedestals");
        cmd(c, "tp @s ~ ~ ~ 200 -32", 30);
        shot(c, "sky_chromandivka", 6);
        tour(c, "rainbow_garage", "rg_wings");
        tour(c, "shelter", "sh_levers");
        tour(c, "aquapark", "aq_pumps");
        tour(c, "sky_ascent", "sky_ascent");
        tour(c, "sky_depot", "sky_tickets");
        tour(c, "tower_approach", "tower_approach");
        tour(c, "tower", "tower_climb");
        tour(c, "isle3", null);
        bossRewards(c);
        // the final boss ends with a cutscene that carries the party to the base: the script must not race it
        until("the rescued party arrives at the base", 900, 20, () -> in(c, "lewandivka:chromandivka") && ClientState.cinematic.isEmpty()
                && c.player.squaredDistanceTo(marker("base:spawn").x() + 0.5, marker("base:spawn").y(), marker("base:spawn").z() + 0.5) < 15 * 15);
        cmd(c, "lewandivka step tower_climb", 10);
        physics(c);
        skyRide(c);
        crossing(c);
        // the block gallery (built by the CI script through rcon) far from the district, in a chunk that is forced to stay
        // loaded; the first teleport out of there used to throw (see JoinReplay), so it is played last
        cmd(c, "tp @s 1020 154 997 0 24", 20);
        settle(c, "gallery", 2400);
        shot(c, "gallery_1", 6);
        cmd(c, "tp @s 1020 154 1010 0 24", 60);
        shot(c, "gallery_2", 6);
        cmd(c, "tp @s 1020 154 1022 0 24", 60);
        shot(c, "gallery_3", 6);
        cmd(c, "lewandivka trace Tester 100", 2);
        tour(c, "base", null);
        // the postgame: free play, Garage No. 0 under the district
        cmd(c, "lewandivka step post_free", 8);
        inside(c, "garage0", null);
        census(c, "garage 0");

        // HUD pieces (client-side mock state, only for the screenshots)
        add("hud state", 10, () -> {
            ClientState.synced = true;
            ClientState.abilityMask = 7;
            ClientState.rings = 3;
            ClientState.chargeActive = true;
            ClientState.chargeHolder = c.player.getUuid();
            ClientState.chargeFraction = 0.62f;
            ClientState.COOLDOWN_UNTIL[0] = ClientState.tick + 40;
            ClientState.COOLDOWN_TOTAL[0] = 70;
        });
        shot(c, "hud", 4);
        add("monochrome", 40, () -> ClientState.monochrome = true);
        shot(c, "hud_monochrome", 4);
        add("cinematic", 30, () -> {
            ClientState.monochrome = false;
            ClientState.chargeActive = false;
            ClientState.cinematic = "transition";
            ClientState.cinematicLength = 400;
            ClientState.cinematicTicks = 200;
        });
        shot(c, "cinematic", 4);
        add("screens", 4, () -> {
            ClientState.cinematic = "";
            ClientState.cinematicLength = 0;
            screenWanted = true;
            c.setScreen(new ConfigScreen(null));
        });
        add("config shot wait", 10, () -> { });
        shot(c, "config", 4);
        add("credits", 40, () -> c.setScreen(new CreditsScreen(null)));
        shot(c, "credits", 4);
        add("end", 4, () -> {
            c.setScreen(null);
            screenWanted = false;
        });
        cmd(c, "lewandivka status", 20);
        add("dimension check", 2, () -> note("final dimension " + c.world.getRegistryKey().getValue() + " " + (c.world.getRegistryKey() == World.OVERWORLD ? "overworld" : "campaign")));
    }
}
