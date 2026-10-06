package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.puzzle.PaintPuzzle;
import com.lewandivka.core.puzzle.PaintPuzzle.Mix;
import com.lewandivka.core.puzzle.PaintPuzzle.Paint;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.UUID;

/**
 * The Rainbow Garage (35 to 45 minutes): three wings, each ending in a lift battery, and the Garage King.
 *
 * <ul>
 *   <li><b>Paint workshop</b>: three taps mix the primaries; the release valve pours the mix into the pipe of the next
 *       grate (orange circle, green triangle, purple square). A wrong mix is grey sludge and simply drains.</li>
 *   <li><b>Lift room</b>: three lifts with call levers on both levels carry the players up a platform route.</li>
 *   <li><b>Industrial press</b>: four presses slam down in their own rhythm; the lamps and a sound warn first.</li>
 *   <li><b>Hub</b>: the three batteries go into the sockets and open the boss door.</li>
 * </ul>
 */
public final class RainbowGarageFlow implements Flow {

    public static final String ID = "rainbow_garage";
    public static final String BOSS = "garage_king";
    public static final String GREMLINS = "rg.gremlin";
    public static final int PRESSES = 4;
    public static final int[] PRESS_PERIOD = {100, 120, 90, 110};
    public static final int PRESS_CLOSED_TICKS = 30;
    public static final int PRESS_WARN_TICKS = 25;
    public static final int LIFT_TICKS = 80;

    public static final List<String> WINGS = List.of("p", "l", "i");
    public static final List<String> MARKERS = List.of(
            "tap_red", "tap_yellow", "tap_blue", "tap_release",
            "battery_wing_p", "battery_wing_l", "battery_wing_i",
            "lift_1_low", "lift_1_high", "lift_2_low", "lift_2_high", "lift_3_low", "lift_3_high",
            "socket_1", "socket_2", "socket_3", "lever_a", "lever_b", "lever_c",
            "grate_1", "grate_2", "grate_3", "press_1", "press_2", "press_3", "press_4",
            "boss_door", "boss_door_inner", "lift_1", "lift_2", "lift_3", "boss_spawn", "mob_paint_1", "mob_paint_2", "entrance");

    private final FlowEnv env;
    private final PaintPuzzle paint = new PaintPuzzle(List.of(Mix.ORANGE, Mix.GREEN, Mix.PURPLE));
    private final boolean[] pressClosed = new boolean[PRESSES];
    private final boolean[] pressWarned = new boolean[PRESSES];
    private final long[] liftBusyUntil = new long[3];

    public RainbowGarageFlow(FlowEnv env) {
        this.env = env;
        paint.restore(grates());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "rainbow_garage";
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

    public int batteriesInstalled() {
        int n = 0;
        for (int i = 1; i <= 3; i++) {
            if (env.record().flag("socket." + i)) {
                n++;
            }
        }
        return n;
    }

    public int wingsDone() {
        int n = 0;
        for (String w : WINGS) {
            if (env.record().flag("battery." + w)) {
                n++;
            }
        }
        return n;
    }

    public boolean liftUp(int lift) {
        return env.record().flag("lift." + lift + ".up");
    }

    // ------------------------------------------------------------------ stations

    @Override
    public void playerEntered(UUID player) {
        env.event(Events.RG_ENTERED);
        if (env.record().setFlag("gremlins")) {
            env.spawn("mechanic_minion", "mob_paint_1", Math.max(1, env.party().adds(2)), GREMLINS);
        }
    }

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (marker.startsWith("tap_")) {
            return tap(marker.substring(4), marker, player);
        }
        if (marker.startsWith("battery_wing_")) {
            return battery(marker.substring("battery_wing_".length()), marker, player);
        }
        if (marker.startsWith("lift_")) {
            return lift(marker, player);
        }
        if (marker.startsWith("socket_")) {
            return socket(marker, player, item);
        }
        if (marker.startsWith("lever_")) {
            int index = marker.charAt(marker.length() - 1) - 'a';
            if (!env.bossStation(BOSS, "lever", index, player)) {
                env.say(player, "message.lewandivka.not_yet");
            }
            return true;
        }
        return false;
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
                    env.gate("grate_" + n, true);
                    env.fx("grate_" + n, "reveal");
                    env.sound("grate_" + n, "garage.door");
                    env.say(null, "message.lewandivka.rg.grate", n, paint.total());
                }
                default -> { }
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
            env.station("tap_" + c, "lit", Boolean.toString(paint.mixer().contains(pc)));
        }
        env.say(player, "message.lewandivka.rg.mix", paint.current().name().toLowerCase(java.util.Locale.ROOT));
        return true;
    }

    private void resetTaps() {
        for (String c : List.of("red", "yellow", "blue")) {
            env.station("tap_" + c, "lit", "false");
        }
    }

    private boolean battery(String wing, String marker, UUID player) {
        if (!WINGS.contains(wing)) {
            return false;
        }
        if (env.record().flag("battery." + wing)) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        env.record().setFlag("battery." + wing);
        env.world().addCounter("rg.wings_done", 1);
        env.station(marker, "filled", "false");
        env.give(player, QuestItems.BATTERY, 1);
        env.sound(marker, "garage.power_off");
        env.say(null, "message.lewandivka.battery.taken", wingsDone(), WINGS.size());
        env.event(Events.RG_WING);
        return true;
    }

    private boolean lift(String marker, UUID player) {
        if (!marker.endsWith("_low") && !marker.endsWith("_high")) {
            return false;
        }
        int n = marker.charAt("lift_".length()) - '0';
        boolean up = marker.endsWith("_high");
        long now = env.now();
        if (n < 1 || n > 3) {
            return false;
        }
        if (now < liftBusyUntil[n - 1]) {
            return true;                                     // still moving
        }
        if (liftUp(n) == up) {
            env.say(player, "message.lewandivka.lift.sync");
            return true;
        }
        if (up) {
            env.record().setFlag("lift." + n + ".up");
        } else {
            env.record().clearFlag("lift." + n + ".up");
        }
        liftBusyUntil[n - 1] = now + LIFT_TICKS;
        env.moveLift("lift_" + n, up);
        env.sound("lift_" + n, "garage.lift");
        return true;
    }

    private boolean socket(String marker, UUID player, String item) {
        int n = marker.charAt(marker.length() - 1) - '0';
        if (n < 1 || n > 3) {
            return false;
        }
        if (env.record().flag("socket." + n)) {
            return true;
        }
        if (!QuestItems.BATTERY.equals(item) || !env.take(player, item, 1)) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        env.record().setFlag("socket." + n);
        env.world().addCounter("rg.batteries_installed", 1);
        env.station(marker, "filled", "true");
        env.sound(marker, "garage.power_on");
        env.say(null, "message.lewandivka.battery.socket", batteriesInstalled(), 3);
        if (batteriesInstalled() >= 3) {
            openBossDoor();
        }
        return true;
    }

    private void openBossDoor() {
        env.record().setFlag("boss.door");
        env.gate("boss_door", true);
        env.gate("boss_door_inner", true);
        env.sound("boss_door", "garage.door");
        env.checkpoint(1);
        env.spawnBoss(BOSS, "boss_spawn");
    }

    // ------------------------------------------------------------------ the presses

    @Override
    public void tick() {
        long now = env.now();
        for (int i = 0; i < PRESSES; i++) {
            int period = env.party().deadlineTicks(PRESS_PERIOD[i]);
            int phase = (int) ((now + i * 23L) % period);
            boolean closed = phase < PRESS_CLOSED_TICKS;
            if (!closed && phase >= period - PRESS_WARN_TICKS && !pressWarned[i]) {
                pressWarned[i] = true;
                env.fx("press_" + (i + 1), "warn");
                env.sound("press_" + (i + 1), "garage.press");
            }
            if (closed != pressClosed[i]) {
                pressClosed[i] = closed;
                pressWarned[i] = false;
                env.gate("press_" + (i + 1), !closed);
            }
        }
    }

    // ------------------------------------------------------------------ recovery

    @Override
    public void rebuild() {
        for (int i = 1; i <= 3; i++) {
            if (env.record().flag("grate." + i)) {
                env.gate("grate_" + i, true);
            }
            if (env.record().flag("socket." + i)) {
                env.station("socket_" + i, "filled", "true");
            }
            env.moveLift("lift_" + i, liftUp(i));
        }
        for (String w : WINGS) {
            if (env.record().flag("battery." + w)) {
                env.station("battery_wing_" + w, "filled", "false");
            }
        }
        if (env.record().flag("boss.door")) {
            env.gate("boss_door", true);
            env.gate("boss_door_inner", true);
            if (!env.world().bossDefeated(BOSS)) {
                env.spawnBoss(BOSS, "boss_spawn");            // does nothing while the boss is alive
            }
        }
    }

    @Override
    public void reset() {
        // solved puzzles stay solved; the volatile parts start over
        paint.restore(grates());
        resetTaps();
        env.despawn(GREMLINS);
        env.record().clearFlag("gremlins");
        for (int i = 0; i < PRESSES; i++) {
            pressClosed[i] = false;
            env.gate("press_" + (i + 1), true);
        }
        if (env.record().flag("boss.door") && !env.world().bossDefeated(BOSS)) {
            env.despawnBoss(BOSS);
            env.spawnBoss(BOSS, "boss_spawn");
        }
    }

    @Override
    public void bossDefeated(String bossId) {
        if (BOSS.equals(bossId)) {
            env.record().setFlag("done");
            env.despawn(GREMLINS);
            env.gate("boss_door_inner", true);
        }
    }

    @Override
    public boolean complete() {
        return env.record().flag("done");
    }
}
