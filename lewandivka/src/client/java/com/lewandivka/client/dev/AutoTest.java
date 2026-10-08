package com.lewandivka.client.dev;

import com.lewandivka.client.ClientState;
import com.lewandivka.client.screen.ConfigScreen;
import com.lewandivka.client.screen.CreditsScreen;
import com.lewandivka.client.screen.NotebookScreen;
import com.lewandivka.LewandivkaMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (wait > 0) {
            wait--;
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
        note(ok ? "AUTOTEST-RESULT OK shots=" + shots : "AUTOTEST-RESULT FAILED");
        try {
            Files.writeString(Path.of(c.runDirectory.toURI()).resolve("autotest-result.txt"), LOG.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LewandivkaMod.LOGGER.error("could not write the autotest result", e);
        }
        c.scheduleStop();
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

    private static void tour(MinecraftClient c, String structure, String view) {
        cmd(c, "lewandivka teleport " + structure, 150);
        cmd(c, view, 70);
        shot(c, "tour_" + structure, 6);
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

    private static void build(MinecraftClient c) {
        add("settle", 180, () -> { });
        cmd(c, "gamemode creative", 10);
        shot(c, "01_district_spawn", 10);
        cmd(c, "lewandivka status", 10);
        add("notebook", 14, () -> c.setScreen(new NotebookScreen()));
        shot(c, "02_notebook", 4);
        add("close", 6, () -> c.setScreen(null));

        String high = "tp @s ~ ~14 ~18 180 30";
        for (String s : List.of("tram_stop", "old_shop", "garage13", "block_a", "house_ne0", "kindergarten", "playground_north", "tram_depot", "kiosk_foundation")) {
            tour(c, s, high);
        }
        // mobs of the district
        cmd(c, "lewandivka teleport tram_stop", 120);
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
        cmd(c, "tp @s 1020 154 997 0 24", 140);
        shot(c, "gallery_1", 6);
        cmd(c, "tp @s 1020 154 1010 0 24", 60);
        shot(c, "gallery_2", 6);
        cmd(c, "tp @s 1020 154 1022 0 24", 60);
        shot(c, "gallery_3", 6);

        // the other side
        String sky = "tp @s ~ ~ ~ 200 -32";
        tour(c, "base", high);
        cmd(c, sky, 20);
        shot(c, "sky_chromandivka", 6);
        for (String s : List.of("rainbow_garage", "shelter", "aquapark", "sky_ascent", "sky_depot", "tower_approach", "tower", "isle3")) {
            tour(c, s, high);
        }

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
            c.setScreen(new ConfigScreen(null));
        });
        add("config shot wait", 10, () -> { });
        shot(c, "config", 4);
        add("credits", 40, () -> c.setScreen(new CreditsScreen(null)));
        shot(c, "credits", 4);
        add("end", 4, () -> c.setScreen(null));
        cmd(c, "lewandivka status", 20);
        add("dimension check", 2, () -> note("final dimension " + c.world.getRegistryKey().getValue() + " " + (c.world.getRegistryKey() == World.OVERWORLD ? "overworld" : "campaign")));
    }
}
