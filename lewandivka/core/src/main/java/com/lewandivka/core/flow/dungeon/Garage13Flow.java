package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.flow.mechanism.DeadlineSwitch;
import com.lewandivka.core.flow.mechanism.LeverPattern;
import com.lewandivka.core.flow.mechanism.OrderedSequence;
import com.lewandivka.core.flow.mechanism.SyncPresses;
import com.lewandivka.core.story.Events;
import com.lewandivka.core.world.gen.GarageComplex;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Garage No. 13 (Act 1 dungeon).
 *
 * <ol>
 *   <li>Three power points: A = four breakers in garage 9 in the order given by four notes; B = two switch boxes held
 *       together (window scaled by the party size); C = three levers up/down/up confirmed on a console.</li>
 *   <li>When all three are on, the wall between garage 12 and 14 opens and the plate "13" appears.</li>
 *   <li>Taking the package closes the main gate, opens the floor hatch, calls the guards, and the package growls every
 *       15-25 seconds (quieter when it lies on the ground).</li>
 *   <li>The escape runs through the maintenance tunnels; the operator's lever opens the tunnel gate for a limited time.</li>
 * </ol>
 * No role is hard-coded: whoever carries, operates or fights is up to the players.
 */
public final class Garage13Flow implements Flow {

    public static final String ID = "garage13";
    public static final String GUARDS = "g13.guard";

    private final FlowEnv env;
    private final OrderedSequence breakers;
    private final SyncPresses switchboxes;
    private final LeverPattern levers;
    private final DeadlineSwitch operator;
    private final Random rng = new Random(0x1313L);
    private long nextGrowl = -1;
    private boolean carried;
    private UUID carrier;

    public Garage13Flow(FlowEnv env) {
        this.env = env;
        this.breakers = new OrderedSequence(env, List.of("breaker_1", "breaker_2", "breaker_3", "breaker_4"), GarageComplex.BREAKER_ORDER, "lit");
        this.switchboxes = new SyncPresses(env, List.of("sync_1", "sync_2"), "lit", 80);
        this.levers = new LeverPattern(env, List.of("lever_1", "lever_2", "lever_3"), GarageComplex.LEVER_PATTERN, "confirm_c");
        this.operator = new DeadlineSwitch(env, "escape_lever", "tunnel_gate", 600);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "garage13";
    }

    public int powerPoints() {
        int n = 0;
        for (String f : List.of("power.a", "power.b", "power.c")) {
            if (env.record().flag(f)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void playerEntered(UUID player) {
        env.event(Events.G13_ENTERED);
    }

    // ------------------------------------------------------------------ stations
    @Override
    public boolean use(String marker, UUID player) {
        if (marker.startsWith("breaker_")) {
            OrderedSequence.Result r = breakers.use(marker);
            if (r == OrderedSequence.Result.WRONG) {
                env.say(player, "message.lewandivka.garage.wrong");
            } else if (r == OrderedSequence.Result.SOLVED) {
                powerOn("a");
            }
            return r != OrderedSequence.Result.IGNORED;
        }
        if (marker.startsWith("sync_")) {
            if (env.record().flag("power.b")) {
                return true;
            }
            if (switchboxes.use(marker)) {
                powerOn("b");
            }
            return true;
        }
        if (marker.startsWith("lever_") || marker.equals("confirm_c")) {
            LeverPattern.Result r = levers.use(marker);
            if (r == LeverPattern.Result.WRONG) {
                env.say(player, "message.lewandivka.garage.wrong");
            } else if (r == LeverPattern.Result.SOLVED) {
                powerOn("c");
            }
            return r != LeverPattern.Result.IGNORED;
        }
        if (marker.equals("escape_lever")) {
            if (!env.record().flag("taken")) {
                env.say(player, "message.lewandivka.not_yet");
                return true;
            }
            return operator.use(marker);
        }
        return false;
    }

    private void powerOn(String point) {
        if (!env.record().setFlag("power." + point)) {
            return;
        }
        env.block("lamp_" + point, "minecraft:redstone_lamp[lit=true]");
        env.sound("lamp_" + point, "garage.power_on");
        env.event(Events.G13_POWER);
        env.checkpoint(1);
        int n = powerPoints();
        env.say(null, "message.lewandivka.garage.power", n);
        if (n >= 3) {
            reveal();
        }
    }

    private void reveal() {
        if (!env.record().setFlag("revealed")) {
            return;
        }
        env.gate("gate_garage13", true);
        env.block("plate_13", "lewandivka:garage_plate[plate=13,facing=south]");
        env.sound("plate_13", "garage.door");
        env.fx("plate_13", "reveal");
        env.checkpoint(2);
        env.say(null, "message.lewandivka.garage.open");
        env.dropItem("package_spawn", "package", 1, "g13.package");
    }

    // ------------------------------------------------------------------ the package
    /** A player picked the package up for the first time: the main gate closes and the guards come. */
    public void packageTaken(UUID by) {
        carried = true;
        carrier = by;
        if (env.record().setFlag("taken")) {
            env.gate("gate_main", false);
            env.gate("hatch", true);
            env.say(null, "message.lewandivka.gate.closed");
            env.sound("gate_main", "garage.door");
            int guards = env.party().adds(6);
            for (int i = 1; i <= Math.min(6, guards); i++) {
                env.spawn("gopnik", "guard_spawn_" + i, 1, GUARDS);
            }
            env.spawn("gopnik", "tunnel_guard", Math.max(1, env.party().adds(2)), GUARDS);
            env.checkpoint(2);
            env.event(Events.G13_PACKAGE_TAKEN);
        }
        scheduleGrowl();
    }

    /** The package was put on the ground: it is quiet again (growls become rare). */
    public void packagePlaced() {
        carried = false;
        carrier = null;
        scheduleGrowl();
        env.say(null, "message.lewandivka.package.placed");
    }

    public void packageLifted(UUID by) {
        carried = true;
        carrier = by;
        env.say(by, "message.lewandivka.package.carry");
        scheduleGrowl();
    }

    private void scheduleGrowl() {
        if (!env.record().flag("taken") || env.record().flag("escaped")) {
            nextGrowl = -1;
            return;
        }
        int base = 300 + rng.nextInt(201);                       // 15..25 s
        nextGrowl = env.now() + (carried ? base : base * 5 / 2);
    }

    /** The party entered the maintenance tunnels. */
    public void enteredTunnels() {
        if (env.record().flag("taken")) {
            env.checkpoint(3);
        }
    }

    /** The package carrier reached the manhole. */
    public void carrierReachedExit(UUID player) {
        if (!env.record().flag("taken") || !env.record().setFlag("escaped")) {
            return;
        }
        operator.reset();
        env.despawn(GUARDS);
        env.sound("exit", "ui.checkpoint");
        env.event(Events.G13_ESCAPED);
        nextGrowl = -1;
    }

    public boolean carried() {
        return carried;
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    public void tick() {
        switchboxes.tick();
        operator.tick();
        if (nextGrowl >= 0 && env.now() >= nextGrowl) {
            if (carrier != null) {
                env.soundAt(carrier, "package.growl");
                env.say(null, "message.lewandivka.package.growl");
                env.attract(GUARDS, carrier);
                if (carried) {
                    env.spawn("gopnik", "guard_spawn_" + (1 + rng.nextInt(6)), Math.max(1, env.party().adds(2) / 2), GUARDS);
                }
            }
            scheduleGrowl();
        }
    }

    @Override
    public void rebuild() {
        if (env.record().flag("power.a")) {
            breakers.restoreSolved();
            env.block("lamp_a", "minecraft:redstone_lamp[lit=true]");
        }
        if (env.record().flag("power.b")) {
            switchboxes.restoreSolved();
            env.block("lamp_b", "minecraft:redstone_lamp[lit=true]");
        }
        if (env.record().flag("power.c")) {
            levers.restoreSolved();
            env.block("lamp_c", "minecraft:redstone_lamp[lit=true]");
        }
        if (env.record().flag("revealed")) {
            env.gate("gate_garage13", true);
            env.block("plate_13", "lewandivka:garage_plate[plate=13,facing=south]");
        }
        if (env.record().flag("taken") && !env.record().flag("escaped")) {
            env.gate("gate_main", false);
            env.gate("hatch", true);
        }
    }

    @Override
    public void reset() {
        // completed puzzles stay solved; the escape starts over
        operator.reset();
        env.despawn(GUARDS);
        carried = false;
        carrier = null;
        nextGrowl = -1;
        if (!env.record().flag("escaped")) {
            env.record().clearFlag("taken");
            env.gate("gate_main", true);
            env.gate("hatch", false);
            if (env.record().flag("revealed")) {
                env.despawn("g13.package");
                env.dropItem("package_spawn", "package", 1, "g13.package");
            }
        }
        if (!env.record().flag("power.a")) {
            breakers.reset();
        }
        if (!env.record().flag("power.b")) {
            switchboxes.reset();
        }
        if (!env.record().flag("power.c")) {
            levers.reset();
        }
    }

    @Override
    public boolean complete() {
        return env.record().flag("escaped");
    }
}
