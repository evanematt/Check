package com.lewandivka.client.dev;

import com.lewandivka.client.ClientNet;
import com.lewandivka.client.ClientState;
import com.lewandivka.client.screen.ConfigScreen;
import com.lewandivka.client.screen.CreditsScreen;
import com.lewandivka.client.screen.NotebookScreen;
import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

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

    private AutoTest() {
    }

    public static void register() {
        if (System.getenv("LEWANDIVKA_AUTOTEST") == null) {
            return;
        }
        LewandivkaMod.LOGGER.info("AutoTest is enabled");
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void note(String line) {
        LOG.append(line).append('\n');
        LewandivkaMod.LOGGER.info("[autotest] {}", line);
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) {
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
            if (traceLeft % 4 == 0) {
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
    private static void trace(String name, int ticks) {
        add("trace " + name, 0, () -> {
            traceName = name;
            traceLeft = ticks;
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
        shot(c, "tour_" + structure, 6);
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

        // 1. the four spring pads of the sky ascent, island by island
        String[][] jumps = {{"pad_0", "isle_1", "6"}, {"pad_1", "isle_2", "5"}, {"pad_2", "isle_3", "5"}, {"pad_3", "tunnel_bottom", "6"}};
        for (String[] j : jumps) {
            Marker pad = marker("sky_ascent:" + j[0]);
            Marker target = marker("sky_ascent:" + j[1]);
            cmd(c, at(pad.x() + 0.5, pad.y() + 1.2, pad.z() + 2.5, -90, 0), 4);
            settle(c, "beside " + j[0], 600);
            trace(c, j[0], 80);
            cmd(c, at(pad.x() + 0.5, pad.y() + 0.13, pad.z() + 0.5, -90, 0), 110);
            checkPosition(c, "spring " + j[0] + " carries the climber to " + j[1], target.x(), target.y(), target.z(), Double.parseDouble(j[2]), 1.6, 4.5);
        }

        // 2. the hatch of the glass tunnel throws the climber up to the platform of the sky tram
        Marker hatch = marker("sky_ascent:tunnel_bottom");
        Marker top = marker("sky_ascent:tunnel_top");
        cmd(c, at(hatch.x() - 1.5, hatch.y(), hatch.z() + 0.5, -90, 0), 4);
        settle(c, "beside the tunnel hatch", 600);
        trace(c, "tunnel hatch", 100);
        cmd(c, at(hatch.x() + 0.5, hatch.y(), hatch.z() + 0.5, -90, 0), 140);
        checkPosition(c, "the tunnel hatch carries the climber to the platform", top.x() + 4, top.y(), top.z(), 9, 1.6, 5.0);

        // 3. the spring shaft of the tower approach ends on the glider deck
        Marker shaft = marker("tower_approach:shaft_bottom");
        Marker deck = marker("tower_approach:glide_start");
        cmd(c, at(shaft.x() + 0.5, shaft.y(), shaft.z() + 2.5, 180, 0), 4);
        settle(c, "beside the shaft hatch", 600);
        trace(c, "shaft hatch", 120);
        cmd(c, at(shaft.x() + 0.5, shaft.y(), shaft.z() + 0.5, 180, 0), 160);
        checkPosition(c, "the shaft hatch carries the climber to the glider deck", deck.x(), deck.y(), deck.z() + 1, 7, 1.6, 6.5);

        // 4. a dash opens the first door of the dash corridor
        Marker door = marker("tower_approach:dash_door_1");
        cmd(c, at(door.x() + 2.5, door.y(), door.z() + 3.6, 180, 0), 4);
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
        cmd(c, at(near.x() + 0.5, near.y(), near.z() + 0.5, 0, 0), 4);
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
        checkPosition(c, "the dash carries the player over the pit of the shelter", far.x(), far.y(), far.z() + 0.5, 9, 1.6, 3.0);

        // 6. the glider carries the player over the chasm to the far platform: run, jump, jump again in mid-air
        Marker start = marker("tower_approach:glide_start");
        Marker end = marker("tower_approach:glide_end");
        cmd(c, at(start.x() + 0.5, start.y(), start.z() + 0.5, 180, 0), 4);
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
        checkPosition(c, "the glider crosses the chasm", end.x(), end.y(), end.z(), 9, 2.5, 11);

        cmd(c, "gamemode creative", 4);
        cmd(c, "effect clear @s minecraft:resistance", 4);
    }

    private static void build(MinecraftClient c) {
        add("settle", 180, () -> { });
        cmd(c, "gamemode creative", 10);
        add("where is the player", 1, () -> where(c, "the first arrival"));
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
        tour(c, "garage13", "garage_panels");
        for (String s : List.of("block_a", "house_ne0", "kindergarten", "playground_north", "tram_depot")) {
            tour(c, s, null);
        }
        tour(c, "kiosk_foundation", "place_kiosk");
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

        // the block gallery (built by the CI script through rcon)
        cmd(c, "tp @s 1020 154 997 0 24", 20);
        settle(c, "gallery", 2400);
        shot(c, "gallery_1", 6);
        cmd(c, "tp @s 1020 154 1010 0 24", 60);
        shot(c, "gallery_2", 6);
        cmd(c, "tp @s 1020 154 1022 0 24", 60);
        shot(c, "gallery_3", 6);

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
        physics(c);
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
