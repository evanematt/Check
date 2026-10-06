package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.flow.mechanism.CatHints;
import com.lewandivka.core.flow.mechanism.CheckpointSensors;
import com.lewandivka.core.flow.mechanism.SyncPresses;
import com.lewandivka.core.puzzle.PaintPuzzle;
import com.lewandivka.core.puzzle.PaintPuzzle.Mix;
import com.lewandivka.core.puzzle.PaintPuzzle.Paint;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Head of District Tower. Every floor reuses a mechanic the players already know:
 * <ul>
 *   <li>F0 lobby: the ticket machine hands out #847 (the display says 003); the urgent side entrance is found with the
 *       help of the cats instead of waiting for hours</li>
 *   <li>F1 archive: three levers within a window (as in the shelter, scaled by the party)</li>
 *   <li>F2 office 404: the wall that the cats open, then the lever</li>
 *   <li>F3 department of colour violations: the paint taps and three grates</li>
 *   <li>F4 service shaft: the spring hatch and the updraft (physics; reaching the ledge counts)</li>
 *   <li>F5 collapsed floor: dash across the platforms</li>
 *   <li>Arena: the Colorless Head</li>
 * </ul>
 */
public final class TowerFlow implements Flow {

    public static final String ID = "tower";
    public static final String BOSS = "colorless_head";
    public static final int FLOORS = 5;
    public static final int FIRST_NUMBER = 847;

    private final FlowEnv env;
    private final CatHints cats;
    private final CheckpointSensors sensors;
    private final SyncPresses levers;
    private final PaintPuzzle paint = new PaintPuzzle(List.of(Mix.ORANGE, Mix.GREEN, Mix.PURPLE));

    public TowerFlow(FlowEnv env) {
        this.env = env;
        this.cats = new CatHints(env, List.of(
                new CatHints.Site("urgent", "ticket_machine", "urgent_front", "cat_spot_urgent", "hidden_urgent"),
                new CatHints.Site("f404", "front_404", "cat_spot_404", "front_404", "hidden_404")));
        this.sensors = new CheckpointSensors(env, "tower", 3.5);
        this.levers = new SyncPresses(env, List.of("lever_1", "lever_2", "lever_3"), "powered", 120);
        paint.restore(grates());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "tower";
    }

    public int floorsDone() {
        int n = 0;
        for (int i = 1; i <= FLOORS; i++) {
            if (env.record().flag("floor." + i)) {
                n++;
            }
        }
        return n;
    }

    private int grates() {
        int n = 0;
        for (int i = 1; i <= 3; i++) {
            if (env.record().flag("grate." + i)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void playerEntered(UUID player) {
        if (env.record().setFlag("started")) {
            env.checkpoint(0);
        }
    }

    // ------------------------------------------------------------------ stations

    @Override
    public boolean use(String marker, UUID player) {
        if (marker.equals("ticket_machine")) {
            return ticket(player);
        }
        if (marker.equals("lever_1") || marker.equals("lever_2") || marker.equals("lever_3")) {
            return archiveLever(marker, player);
        }
        if (marker.equals("lever_404")) {
            if (!env.record().flag("hint.f404")) {
                env.say(player, "message.lewandivka.not_yet");
                return true;
            }
            env.gate("gate_f2", true);
            env.sound("gate_f2", "garage.door");
            floorDone(2);
            return true;
        }
        if (marker.startsWith("tap_f3_")) {
            return tap(marker.substring("tap_f3_".length()), marker, player);
        }
        if (marker.equals("relay_a") || marker.equals("relay_b")) {
            int index = marker.equals("relay_a") ? 0 : 1;
            if (!env.bossStation(BOSS, "relay", index, player)) {
                env.say(player, "message.lewandivka.not_yet");
            }
            return true;
        }
        return false;
    }

    private boolean ticket(UUID player) {
        int number = FIRST_NUMBER + env.world().counter("tower.tickets");
        if (!env.record().flag("ticket." + player)) {
            env.record().setFlag("ticket." + player);
            env.world().addCounter("tower.tickets", 1);
            env.dialogue("tower_machine");
            number = FIRST_NUMBER + env.world().counter("tower.tickets") - 1;
        }
        env.sound("ticket_machine", "tower.ticket");
        env.say(player, "message.lewandivka.tower.ticket", number);
        return true;
    }

    private boolean archiveLever(String marker, UUID player) {
        if (env.record().flag("floor.1")) {
            return true;
        }
        if (levers.use(marker)) {
            for (int i = 1; i <= 3; i++) {
                env.block("lamp_f1_" + i, "minecraft:redstone_lamp[lit=true]");
            }
            env.gate("gate_f1", true);
            env.sound("gate_f1", "garage.door");
            floorDone(1);
        } else {
            env.say(player, "message.lewandivka.tower.lever", 1, 3);
        }
        return true;
    }

    private boolean tap(String color, String marker, UUID player) {
        if (paint.done()) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        env.sound(marker, "lever.pull");
        if (color.equals("release")) {
            PaintPuzzle.Result r = paint.release();
            resetTaps();
            switch (r) {
                case EMPTY -> env.say(player, "message.lewandivka.rg.empty");
                case WRONG -> {
                    env.say(player, "message.lewandivka.rg.sludge");
                    env.fx(marker, "sludge");
                }
                case CORRECT -> {
                    int n = paint.solved();
                    env.record().setFlag("grate." + n);
                    env.gate("grate_f3_" + n, true);
                    env.fx("grate_f3_" + n, "reveal");
                    env.sound("grate_f3_" + n, "garage.door");
                    env.say(null, "message.lewandivka.rg.grate", n, paint.total());
                    if (paint.done()) {
                        env.say(null, "message.lewandivka.tower.paint");
                        floorDone(3);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        Paint p = switch (color) {
            case "red" -> Paint.RED;
            case "yellow" -> Paint.YELLOW;
            case "blue" -> Paint.BLUE;
            default -> null;
        };
        if (p == null) {
            return false;
        }
        paint.toggle(p);
        for (String c : List.of("red", "yellow", "blue")) {
            Paint pc = c.equals("red") ? Paint.RED : c.equals("yellow") ? Paint.YELLOW : Paint.BLUE;
            env.station("tap_f3_" + c, "lit", Boolean.toString(paint.mixer().contains(pc)));
        }
        env.say(player, "message.lewandivka.rg.mix", paint.current().name().toLowerCase(Locale.ROOT));
        return true;
    }

    private void resetTaps() {
        for (String c : List.of("red", "yellow", "blue")) {
            env.station("tap_f3_" + c, "lit", "false");
        }
    }

    private void floorDone(int floor) {
        if (env.record().setFlag("floor." + floor)) {
            env.event(Events.TOWER_FLOOR);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        cats.tick();
        sensors.tick();
        levers.tick();
        if (cats.found("urgent") && env.record().setFlag("urgent.said")) {
            env.dialogue("tower_urgent");
            env.say(null, "message.lewandivka.tower.urgent");
        }
        if (!env.record().flag("floor.4") && !env.playersAt("f4_ledge", 2.5).isEmpty()) {
            floorDone(4);
        }
        if (!env.record().flag("floor.5") && !env.playersAt("f5_end", 2.5).isEmpty()) {
            env.gate("boss_door", true);
            env.sound("boss_door", "garage.door");
            floorDone(5);
        }
        if (!env.record().flag("arena") && !env.playersAt("arena_entry", 3).isEmpty() && env.record().setFlag("arena")) {
            env.spawnBoss(BOSS, "boss_spawn");
        }
    }

    @Override
    public void rebuild() {
        cats.rebuild();
        if (env.record().flag("floor.1")) {
            levers.restoreSolved();
            for (int i = 1; i <= 3; i++) {
                env.block("lamp_f1_" + i, "minecraft:redstone_lamp[lit=true]");
            }
            env.gate("gate_f1", true);
        }
        if (env.record().flag("floor.2")) {
            env.gate("gate_f2", true);
        }
        for (int i = 1; i <= grates(); i++) {
            env.gate("grate_f3_" + i, true);
        }
        if (env.record().flag("floor.5")) {
            env.gate("boss_door", true);
        }
        if (env.record().flag("arena") && !env.world().bossDefeated(BOSS)) {
            env.spawnBoss(BOSS, "boss_spawn");
        }
    }

    @Override
    public void reset() {
        cats.reset();
        if (!env.record().flag("floor.1")) {
            levers.reset();
        }
        paint.restore(grates());
        resetTaps();
        if (env.record().flag("arena") && !env.world().bossDefeated(BOSS)) {
            env.despawnBoss(BOSS);
            env.spawnBoss(BOSS, "boss_spawn");
        }
    }

    @Override
    public void bossDefeated(String bossId) {
        if (BOSS.equals(bossId)) {
            env.record().setFlag("done");
        }
    }

    @Override
    public boolean complete() {
        return env.record().flag("done");
    }
}
