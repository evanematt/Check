package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.flow.mechanism.CatHints;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.UUID;

/**
 * The Dry Lake Aquapark. The cats open the hidden entrance, three pumps fill the pools with orange liquid (each one a
 * quarter of the park) and the third opens the arena of Lady Vortex. In the arena the water cores have to be routed
 * into the drains; the boss rules decide when that is possible.
 */
public final class AquaparkFlow implements Flow {

    public static final String ID = "aquapark";
    public static final String BOSS = "lady_vortex";
    public static final int PUMPS = 3;
    public static final String GUARDS = "aq.guard";

    private final FlowEnv env;
    private final CatHints cats;

    public AquaparkFlow(FlowEnv env) {
        this.env = env;
        this.cats = new CatHints(env, List.of(new CatHints.Site("aq_entry", "hint_spot", "cat_hint_a", "cat_hint_b", "gate_hidden")));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "aquapark";
    }

    public int pumpsOn() {
        int n = 0;
        for (int i = 1; i <= PUMPS; i++) {
            if (env.record().flag("pump." + i)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (marker.startsWith("pump_")) {
            return pump(marker, player);
        }
        if (marker.startsWith("drain_")) {
            return drain(marker, player, item);
        }
        return false;
    }

    private boolean pump(String marker, UUID player) {
        int n = marker.charAt(marker.length() - 1) - '0';
        if (n < 1 || n > PUMPS) {
            return false;
        }
        if (!env.record().flag("entered")) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (!env.record().setFlag("pump." + n)) {
            return true;                                    // already running
        }
        env.station(marker, "on", "true");
        env.sound(marker, "pump.start");
        env.flood("fill_" + n, true);
        env.sound("pool_" + n, "pump.fill");
        env.say(null, "message.lewandivka.pump.started", pumpsOn());
        env.event(Events.AQ_PUMP);
        if (pumpsOn() == 1) {
            env.checkpoint(1);
        }
        if (pumpsOn() >= PUMPS) {
            openArena();
        }
        return true;
    }

    private void openArena() {
        env.record().setFlag("arena.open");
        env.gate("boss_door", true);
        env.sound("boss_door", "garage.door");
        env.checkpoint(2);
        env.spawnBoss(BOSS, "boss_spawn");
    }

    /** A water core goes into a drain of the arena. The core is only spent when the boss accepts it. */
    private boolean drain(String marker, UUID player, String item) {
        int index = marker.charAt(marker.length() - 1) - '1';
        if (index < 0 || index > 3) {
            return false;
        }
        if (!QuestItems.WATER_CORE.equals(item) || !env.has(player, item)) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (env.take(player, item, 1)) {
            if (env.bossStation(BOSS, "drain", index, player)) {
                env.sound(marker, "drain.open");
            } else {
                env.give(player, item, 1);
                env.say(player, "message.lewandivka.not_yet");
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        cats.tick();
        if (!env.record().flag("entered") && !env.playersAt("lobby", 6).isEmpty() && env.record().setFlag("entered")) {
            env.checkpoint(0);
            env.event(Events.AQ_ENTERED);
        }
    }

    @Override
    public void rebuild() {
        cats.rebuild();
        for (int i = 1; i <= PUMPS; i++) {
            if (env.record().flag("pump." + i)) {
                env.station("pump_" + i, "on", "true");
                env.flood("fill_" + i, true);
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
        cats.reset();
        env.despawn(GUARDS);
        if (env.record().flag("arena.open") && !env.world().bossDefeated(BOSS)) {
            env.despawnBoss(BOSS);
            env.spawnBoss(BOSS, "boss_spawn");
        }
    }

    @Override
    public void bossDefeated(String bossId) {
        if (BOSS.equals(bossId)) {
            env.record().setFlag("done");
            env.despawn(GUARDS);
        }
    }

    @Override
    public boolean complete() {
        return env.record().flag("done");
    }
}
