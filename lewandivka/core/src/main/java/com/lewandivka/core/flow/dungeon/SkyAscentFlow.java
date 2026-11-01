package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.story.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The climb to the sky stop and the tram ride to the depot. The climb itself is physics (spring pads, floating islands,
 * the updraft tube); this flow only saves the checkpoints, calls the tram and carries everybody who is on the platform
 * through two minutes of panorama with one short attack on the way. Nobody is teleported: the tram really drives.
 */
public final class SkyAscentFlow implements Flow {

    public static final String ID = "sky_ascent";
    public static final String TRAM = "sky.tram";
    public static final String ATTACKERS = "sky.attacker";
    public static final int ROUTE_POINTS = 10;
    /** Blocks per tick: the 250 block route takes about two and a half minutes. */
    public static final double SPEED = 0.085;
    public static final int BOARDING_TICKS = 400;

    private enum Phase {
        IDLE, ARRIVING, BOARDING, RIDING
    }

    private final FlowEnv env;
    private Phase phase = Phase.IDLE;
    private long phaseSince;
    private long attackAt = -1;
    private long rideSeconds;

    public SkyAscentFlow(FlowEnv env) {
        this.env = env;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "sky_ascent";
    }

    public boolean riding() {
        return phase == Phase.RIDING;
    }

    @Override
    public boolean use(String marker, UUID player) {
        return false;
    }

    private static List<String> route(int from, int to) {
        List<String> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.add("chromandivka:sky_ride_" + i);
        }
        return out;
    }

    @Override
    public void tick() {
        if (!env.record().flag("isle3") && !env.playersAt("pad_3", 3).isEmpty() && env.record().setFlag("isle3")) {
            env.checkpoint(1);
        }
        boolean onPlatform = !env.playersAt("stop_platform", 1.5).isEmpty();
        switch (phase) {
            case IDLE -> {
                if (onPlatform) {
                    if (env.record().setFlag("reached")) {
                        env.checkpoint(2);
                        env.event(Events.SKY_STOP_REACHED);
                    }
                    callTram();
                }
            }
            case ARRIVING -> {
                if (!env.tramBusy(TRAM)) {
                    phase = Phase.BOARDING;
                    phaseSince = env.now();
                    env.say(null, "message.lewandivka.sky.boarding");
                }
            }
            case BOARDING -> {
                List<UUID> on = env.playersAt("stop_platform", 3);
                boolean everyone = !on.isEmpty() && on.size() >= env.players().size();
                if (on.isEmpty()) {
                    // everybody walked off again: the tram leaves empty and comes back when somebody returns
                    env.tramClear(TRAM, null);
                    phase = Phase.IDLE;
                } else if (everyone || env.now() - phaseSince >= BOARDING_TICKS) {
                    depart(on);
                }
            }
            case RIDING -> ride();
            default -> {
            }
        }
    }

    private void callTram() {
        phase = Phase.ARRIVING;
        phaseSince = env.now();
        env.sound("stop_platform", "tram.arrive");
        env.tramDrive(TRAM, route(0, 1), 0.25);
    }

    private void depart(List<UUID> riders) {
        phase = Phase.RIDING;
        phaseSince = env.now();
        env.tramBoard(TRAM, riders);
        env.tramDrive(TRAM, route(1, ROUTE_POINTS - 1), SPEED);
        env.sound("stop_platform", "tram.horn");
        attackAt = env.now() + 900;
        rideSeconds = 0;
    }

    private void ride() {
        long now = env.now();
        if (attackAt >= 0 && now >= attackAt) {
            attackAt = -1;
            env.say(null, "message.lewandivka.sky.attack");
            env.sound("stop_platform", "boss.bell");
            env.tramBoardMobs(TRAM, "seed_thrower", Math.max(1, env.party().adds(3)), ATTACKERS);
        }
        if (!env.tramBusy(TRAM)) {
            env.despawn(ATTACKERS);
            env.tramClear(TRAM, "sky_depot:tram_arrive");
            phase = Phase.IDLE;
            env.record().setFlag("arrived");
            env.event(Events.SKY_ARRIVED);
        }
    }

    @Override
    public void rebuild() {
        phase = Phase.IDLE;
    }

    @Override
    public void reset() {
        env.despawn(ATTACKERS);
        env.tramClear(TRAM, null);
        phase = Phase.IDLE;
        attackAt = -1;
    }

    @Override
    public boolean complete() {
        return env.record().flag("arrived");
    }
}
