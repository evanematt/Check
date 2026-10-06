package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.flow.mechanism.SyncPresses;
import com.lewandivka.core.puzzle.CatRoute;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;

import java.util.List;
import java.util.UUID;

/**
 * The Shelter of Lost Names.
 *
 * <ol>
 *   <li>Past the Dash pit three levers must be held together (the window scales with the party).</li>
 *   <li>Chinazik ("???") appears. He is not a follower: four fish are hidden in the rubbish, and putting one on a food
 *       point sends him to the next point; crowding him sends him back to the previous safe point. His last point is
 *       the old rug, where his name returns.</li>
 *   <li>The box warehouse hides Metadonna in the one unusually small box: purring, a moving box and scratch marks help
 *       instead of a click marathon.</li>
 *   <li>Both cats look at the Collar Collector's door, which opens.</li>
 * </ol>
 */
public final class ShelterFlow implements Flow {

    public static final String ID = "shelter";
    public static final String BOSS = "collar_collector";
    public static final List<String> LEVERS = List.of("lever_1", "lever_2", "lever_3");
    public static final int FOOD_POINTS = 4;
    public static final int EAT_TICKS = 80;

    public static final List<String> MARKERS = List.of("lever_1", "lever_2", "lever_3", "lamp_1", "lamp_2", "lamp_3",
            "gate_levers", "hall_entrance", "cat_hall", "cat_spawn", "cat_start", "cat_food_1", "cat_food_2", "cat_food_3",
            "cat_food_4", "cat_rug", "gate_warehouse", "boss_door", "box_small", "boxes", "box_warehouse",
            "stand_1", "stand_2", "stand_3", "stand_4", "stand_5", "stand_6", "stand_7", "stand_8", "boss_spawn",
            "stash_fish_1", "stash_fish_2", "stash_fish_3", "stash_fish_4");

    private final FlowEnv env;
    private final SyncPresses levers;
    private final CatRoute route = new CatRoute(FOOD_POINTS + 1);
    private long eatUntil = -1;
    private long scriptAt = -1;
    private int script;
    private long nextClue;

    public ShelterFlow(FlowEnv env) {
        this.env = env;
        this.levers = new SyncPresses(env, LEVERS, "powered", 100);
        route.restore(reached());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "shelter";
    }

    public CatRoute route() {
        return route;
    }

    private int reached() {
        int best = 0;
        for (int i = 1; i <= FOOD_POINTS + 1; i++) {
            if (env.record().flag("cat.reached." + i)) {
                best = i;
            }
        }
        return best;
    }

    private static String point(int p) {
        return p <= 0 ? "cat_start" : p > FOOD_POINTS ? "cat_rug" : "cat_food_" + p;
    }

    // ------------------------------------------------------------------ stations

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (marker.startsWith("lever_")) {
            return lever(marker, player);
        }
        if (marker.startsWith("cat_food_")) {
            return food(marker, player, item);
        }
        if (marker.equals("box_small")) {
            return foundMetadonna(player);
        }
        if (marker.equals("box_other")) {
            env.sound("box_warehouse", "box.rustle");
            env.say(player, "message.lewandivka.cat.rug");
            return true;
        }
        if (marker.startsWith("stand_")) {
            int index = marker.charAt(marker.length() - 1) - '1';
            if (!env.bossStation(BOSS, "stand", index, player)) {
                env.say(player, "message.lewandivka.not_yet");
            }
            return true;
        }
        return false;
    }

    private boolean lever(String marker, UUID player) {
        if (env.record().flag("levers")) {
            return true;
        }
        if (levers.use(marker)) {
            env.record().setFlag("levers");
            env.gate("gate_levers", true);
            for (int i = 1; i <= LEVERS.size(); i++) {
                env.block("lamp_" + i, "minecraft:redstone_lamp[lit=true]");
            }
            env.sound("gate_levers", "garage.door");
            env.checkpoint(1);
            env.event(Events.SH_LEVERS_DONE);
            scriptAt = env.now() + 40;
            script = 0;
        }
        env.stepCounter(levers.solved() ? LEVERS.size() : 0);
        return true;
    }

    private boolean food(String marker, UUID player, String item) {
        int p = marker.charAt(marker.length() - 1) - '0';
        if (!env.record().flag("cat.active") || route.state() == CatRoute.State.DONE) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (!QuestItems.FISH.equals(item)) {
            env.say(player, "message.lewandivka.cat.food");
            return true;
        }
        if (!route.placeFood(p)) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (!env.take(player, QuestItems.FISH, 1)) {
            route.scare();
            env.say(player, "message.lewandivka.cat.food");
            return true;
        }
        env.fx(marker, "food");
        env.sound(marker, "cat.sniff");
        env.npcMove("chinazik", marker);
        return true;
    }

    private boolean foundMetadonna(UUID player) {
        if (!env.record().flag("chinazik.done")) {
            env.sound("box_small", "box.rustle");
            return true;
        }
        if (!env.record().setFlag("metadonna.found")) {
            return true;
        }
        env.station("box_small", "opened", "true");
        env.sound("box_small", "box.open");
        env.fx("box_small", "reveal");
        env.world().setNpcState("metadonna", "named");
        env.event(Events.SH_METADONNA_FOUND);
        env.gate("boss_door", true);
        env.sound("boss_door", "garage.door");
        env.spawnBoss(BOSS, "boss_spawn");
        env.checkpoint(3);
        scriptAt = env.now() + 80;
        script = 10;
        return true;
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public void tick() {
        long now = env.now();
        levers.tick();
        if (!env.record().flag("hall") && !env.playersAt("hall_entrance", 3.0).isEmpty()) {
            env.record().setFlag("hall");
            env.event(Events.SH_HALL_ENTERED);
        }
        runScript(now);
        if (env.record().flag("cat.active") && route.state() != CatRoute.State.DONE) {
            tickCat(now);
        }
        if (env.record().flag("chinazik.done") && !env.record().flag("metadonna.found") && now >= nextClue) {
            nextClue = now + 50;
            boxClues();
        }
    }

    /** The two small scenes: Chinazik wandering off in the wrong direction, and both cats looking at the boss door. */
    private void runScript(long now) {
        if (scriptAt < 0 || now < scriptAt) {
            return;
        }
        switch (script) {
            case 0 -> {
                env.npcMove("chinazik", "cat_hall");
                script = 1;
                scriptAt = now + 120;
            }
            case 1 -> {
                env.npcMove("chinazik", "cat_start");
                env.npcAnim("chinazik", "look");
                env.record().setFlag("cat.active");
                env.say(null, "message.lewandivka.cat.name_hidden");
                scriptAt = -1;
            }
            case 10 -> {
                env.npcMove("metadonna", "box_warehouse");
                env.npcAnim("metadonna", "point");
                env.npcAnim("chinazik", "point");
                env.dialogue("cats_doors");
                scriptAt = -1;
            }
            default -> scriptAt = -1;
        }
    }

    private void tickCat(long now) {
        int at = route.at();
        switch (route.state()) {
            case MOVING -> {
                String target = point(route.target());
                if (env.playersNear("chinazik", 1.5) > 0) {
                    retreat();
                } else if (env.npcAt("chinazik", target, 1.6)) {
                    route.arrive();
                    env.record().setFlag("cat.reached." + route.at());
                    env.npcAnim("chinazik", "eat");
                    env.sound(target, "cat.eat");
                    eatUntil = now + EAT_TICKS;
                    if (route.state() == CatRoute.State.DONE) {
                        finishRoute();
                    } else {
                        env.stepCounter(route.at());
                    }
                }
            }
            case EATING -> {
                if (env.playersNear("chinazik", 1.5) > 0) {
                    retreat();
                } else if (now >= eatUntil) {
                    route.finishEating();
                    env.npcAnim("chinazik", "look");
                    if (route.at() == FOOD_POINTS && route.placeFood(FOOD_POINTS + 1)) {
                        // fully fed, he walks to the old rug on his own
                        env.npcMove("chinazik", "cat_rug");
                    }
                }
            }
            default -> {
                if (at < 0) {
                    route.reset();
                }
            }
        }
    }

    private void retreat() {
        int safe = route.scare();
        if (safe >= 0) {
            env.npcMove("chinazik", point(safe));
            env.npcAnim("chinazik", "alert");
            env.sound(point(safe), "cat.hiss");
            env.say(null, "message.lewandivka.cat.retreat");
        }
    }

    private void finishRoute() {
        env.record().setFlag("chinazik.done");
        env.world().setNpcState("chinazik", "named");
        env.npcName("chinazik", "");
        env.npcAnim("chinazik", "point");
        env.gate("gate_warehouse", true);
        env.sound("gate_warehouse", "garage.door");
        env.checkpoint(2);
        env.stepCounter(FOOD_POINTS);
        env.event(Events.SH_CHINAZIK_DONE);
    }

    /** Purring grows louder near the small box; now and then it shakes. Nothing needs a click marathon. */
    private void boxClues() {
        for (UUID p : env.playersAt("boxes", 2.0)) {
            double d = env.distance(p, "box_small");
            float volume = (float) Math.max(0.06, Math.min(1.0, 1.0 - d / 18.0));
            env.soundAt(p, "cat.purr", volume);
            if (d < 7) {
                env.fx("box_small", "rustle");
            }
        }
    }

    // ------------------------------------------------------------------ recovery

    @Override
    public void rebuild() {
        if (env.record().flag("levers")) {
            env.gate("gate_levers", true);
            levers.restoreSolved();
            for (int i = 1; i <= LEVERS.size(); i++) {
                env.block("lamp_" + i, "minecraft:redstone_lamp[lit=true]");
            }
        }
        if (env.record().flag("chinazik.done")) {
            env.gate("gate_warehouse", true);
        }
        if (env.record().flag("metadonna.found")) {
            env.station("box_small", "opened", "true");
            env.gate("boss_door", true);
            if (!env.world().bossDefeated(BOSS)) {
                env.spawnBoss(BOSS, "boss_spawn");            // does nothing while the boss is alive
            }
        }
    }

    @Override
    public void reset() {
        eatUntil = -1;
        if (!env.record().flag("chinazik.done")) {
            route.restore(reached());
            if (env.record().flag("cat.active") && route.state() != CatRoute.State.DONE) {
                env.npcMove("chinazik", point(route.at()));
            }
        }
        if (env.record().flag("metadonna.found") && !env.world().bossDefeated(BOSS)) {
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
