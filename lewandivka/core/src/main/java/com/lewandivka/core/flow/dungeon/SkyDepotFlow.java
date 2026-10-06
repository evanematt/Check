package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.puzzle.TramNetwork;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The tram depot above the sky.
 *
 * <ol>
 *   <li><b>Dispatcher</b>: four switches and the dispatch lever. A tram car runs along the track; the wrong arrangement
 *       ends at a buffer stop and the car rolls back (recoverable, nobody is hurt). The notes in the dispatcher room
 *       tell which switches are "B".</li>
 *   <li><b>Ticket office</b>: the window hands out tickets with different symbols (cross, circle, triangle). The party
 *       needs all three.</li>
 *   <li><b>Arena</b>: the Conductor. The three composter stands take the matching ticket (the composter from the
 *       last tram is needed again), the validators serve the called "TICKET!" mechanic.</li>
 * </ol>
 */
public final class SkyDepotFlow implements Flow {

    public static final String ID = "sky_depot";
    public static final String BOSS = "conductor";
    public static final String CAR = "depot.car";
    public static final int SWITCHES = 4;
    public static final double CAR_SPEED = 0.35;
    public static final List<String> TICKETS = List.of(QuestItems.TICKET_X, QuestItems.TICKET_CIRCLE, QuestItems.TICKET_TRIANGLE);
    public static final List<String> TICKET_FLAGS = List.of("ticket.cross", "ticket.circle", "ticket.triangle");
    private static final List<String> NODE_MARKERS = List.of("net_s0", "net_s1", "net_s2", "net_s3", "net_d0", "net_d1", "net_d2", "net_d3", "net_goal");

    private final FlowEnv env;
    private final TramNetwork network = TramNetwork.depot();
    private final boolean[] states = new boolean[SWITCHES];
    private boolean dispatching;
    private boolean winning;

    public SkyDepotFlow(FlowEnv env) {
        this.env = env;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "sky_depot";
    }

    public boolean switchesSolved() {
        return env.record().flag("switches");
    }

    public int ticketsFound() {
        int n = 0;
        for (String f : TICKET_FLAGS) {
            if (env.record().flag(f)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void playerEntered(UUID player) {
        if (env.record().setFlag("arrived")) {
            env.checkpoint(0);
        }
    }

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (marker.startsWith("switch_") && !marker.startsWith("switch_lamp")) {
            return toggle(marker, player);
        }
        if (marker.equals("dispatch")) {
            return dispatch(player);
        }
        if (marker.equals("ticket_window")) {
            return window(player);
        }
        if (marker.startsWith("composter_")) {
            return composter(marker, player, item);
        }
        if (marker.startsWith("validator_")) {
            int index = marker.charAt(marker.length() - 1) - 'a';
            if (!env.bossStation(BOSS, "validator", index, player)) {
                env.say(player, "message.lewandivka.not_yet");
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the dispatcher

    private boolean toggle(String marker, UUID player) {
        int i = marker.charAt(marker.length() - 1) - '1';
        if (i < 0 || i >= SWITCHES) {
            return false;
        }
        if (switchesSolved() || dispatching) {
            return true;
        }
        states[i] = !states[i];
        env.station(marker, "state", states[i] ? "b" : "a");
        env.block("switch_lamp_" + (i + 1), "minecraft:redstone_lamp[lit=" + states[i] + "]");
        env.sound(marker, "switch.click");
        return true;
    }

    private boolean dispatch(UUID player) {
        env.station("dispatch", "powered", "false");
        if (switchesSolved() || dispatching || env.tramBusy(CAR)) {
            return true;
        }
        TramNetwork.Trace trace = network.trace(states.clone());
        List<String> path = new ArrayList<>();
        path.add("net_start");
        for (int node : trace.nodes()) {
            path.add(NODE_MARKERS.get(node));
        }
        env.tramDrive(CAR, path, CAR_SPEED);
        env.sound("dispatch", "lever.pull");
        dispatching = true;
        winning = trace.reachedGoal();
        return true;
    }

    private void finishDispatch() {
        dispatching = false;
        if (winning) {
            if (env.record().setFlag("switches")) {
                env.say(null, "message.lewandivka.switch.ok");
                env.sound("ticket_office", "tram.arrive");
                env.checkpoint(1);
                env.event(Events.DEPOT_SWITCHES_SOLVED);
            }
        } else {
            env.say(null, "message.lewandivka.switch.wrong");
            env.sound("dispatch", "validator.fail");
            env.tramClear(CAR, null);
        }
    }

    // ------------------------------------------------------------------ tickets and the arena

    private boolean window(UUID player) {
        if (!switchesSolved()) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        for (int i = 0; i < TICKETS.size(); i++) {
            if (!env.has(player, TICKETS.get(i))) {
                env.give(player, TICKETS.get(i), 1);
                env.sound("ticket_window", "tram.validate");
                if (env.record().setFlag(TICKET_FLAGS.get(i))) {
                    env.event(Events.DEPOT_TICKET);
                    if (ticketsFound() >= TICKETS.size()) {
                        openArena();
                    }
                }
                return true;
            }
        }
        env.say(player, "message.lewandivka.ticket.have_all");
        return true;
    }

    private void openArena() {
        env.record().setFlag("arena.open");
        env.gate("boss_door", true);
        env.sound("boss_door", "garage.door");
        env.checkpoint(3);
        env.spawnBoss(BOSS, "boss_spawn");
    }

    /** The composter stand takes the ticket with its own symbol (the ticket stays in the pocket, it is only punched). */
    private boolean composter(String marker, UUID player, String item) {
        int index = marker.charAt(marker.length() - 1) - '1';
        if (index < 0 || index >= TICKETS.size()) {
            return false;
        }
        if (!env.has(player, QuestItems.COMPOSTER) || !TICKETS.get(index).equals(item)) {
            env.say(player, "message.lewandivka.ticket.invalid");
            return true;
        }
        if (!env.bossStation(BOSS, "composter", index, player)) {
            env.say(player, "message.lewandivka.not_yet");
        }
        return true;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        if (dispatching && !env.tramBusy(CAR)) {
            finishDispatch();
        }
        if (!env.record().flag("yard") && !env.playersAt("yard", 5).isEmpty() && env.record().setFlag("yard")) {
            env.checkpoint(2);
        }
    }

    @Override
    public void rebuild() {
        if (switchesSolved()) {
            for (int i = 0; i < SWITCHES; i++) {
                boolean b = TramNetwork.depotSolution()[i];
                states[i] = b;
                env.station("switch_" + (i + 1), "state", b ? "b" : "a");
                env.block("switch_lamp_" + (i + 1), "minecraft:redstone_lamp[lit=" + b + "]");
            }
        }
        if (env.record().flag("arena.open")) {
            env.gate("boss_door", true);
            if (!env.world().bossDefeated(BOSS)) {
                env.spawnBoss(BOSS, "boss_spawn");
            }
        }
    }

    @Override
    public void reset() {
        dispatching = false;
        env.tramClear(CAR, null);
        if (!switchesSolved()) {
            for (int i = 0; i < SWITCHES; i++) {
                states[i] = false;
                env.station("switch_" + (i + 1), "state", "a");
                env.block("switch_lamp_" + (i + 1), "minecraft:redstone_lamp[lit=false]");
            }
        }
        if (env.record().flag("arena.open") && !env.world().bossDefeated(BOSS)) {
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
