package com.lewandivka.core.flow.dungeon;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.quest.QuestItems;

import java.util.Random;
import java.util.UUID;

/**
 * Free play after the credits. The whole district is one "structure" here ({@code district}); the activities:
 * <ul>
 *   <li><b>Twelve cats of Lewandivka</b>: twelve named cats sit at hidden spots; each one found is counted, the twelfth
 *       gives the square ticket</li>
 *   <li><b>Where do the seeds go</b>: three bowls get seeds; at night somebody eats them. Watch it happen three times</li>
 *   <li><b>The wrong tram</b>: the square ticket in the validator at the tram stop calls a tram that is not in the
 *       timetable; it ends in Garage No. 0</li>
 *   <li><b>Garage No. 0</b>: see {@link Garage0Flow}</li>
 *   <li><b>What was in the package</b>: Mr. Shlahbaum tells, once everything else is done. It was one sock.</li>
 * </ul>
 */
public final class PostgameFlow implements Flow {

    public static final String ID = "postgame";
    public static final int CATS = 12;
    public static final int BOWLS = 3;
    public static final String CAT_TAG = "post.cat.";
    public static final String THIEF_TAG = "post.thief.";
    public static final String TRAM = "post.tram";
    public static final int SEEDS_PER_BOWL = 2;
    private static final int EAT_TICKS = 360;

    private final FlowEnv env;
    private final Random rng = new Random(0x0C47L);
    private final long[] eatAt = new long[BOWLS + 1];
    private final long[] eatUntil = new long[BOWLS + 1];
    private final boolean[] witnessed = new boolean[BOWLS + 1];
    private boolean riding;
    private long checkAt;

    public PostgameFlow(FlowEnv env) {
        this.env = env;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String structure() {
        return "district";
    }

    private boolean active() {
        return env.world().step() == QuestStep.POST_FREE;
    }

    public int catsFound() {
        int n = 0;
        for (int i = 1; i <= CATS; i++) {
            if (env.record().flag("cat." + i)) {
                n++;
            }
        }
        return n;
    }

    public int bowlsWitnessed() {
        int n = 0;
        for (int i = 1; i <= BOWLS; i++) {
            if (env.record().flag("seen." + i)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ stations

    @Override
    public boolean use(String marker, UUID player) {
        return useWith(marker, player, "");
    }

    @Override
    public boolean useWith(String marker, UUID player, String item) {
        if (!active()) {
            return false;
        }
        if (marker.startsWith("cat_")) {
            return cat(Integer.parseInt(marker.substring(4)), player);
        }
        if (marker.startsWith("bowl_")) {
            return bowl(Integer.parseInt(marker.substring(5)), player, item);
        }
        if (marker.equals("validator")) {
            return validator(player, item);
        }
        if (marker.equals("shlahbaum")) {
            return shlahbaum(player);
        }
        return false;
    }

    private boolean cat(int n, UUID player) {
        if (n < 1 || n > CATS || !env.record().setFlag("cat." + n)) {
            return n >= 1 && n <= CATS;
        }
        env.despawn(CAT_TAG + n);
        env.sound("cat_spot_" + n, "cat.meow_high");
        env.fx("cat_spot_" + n, "food");
        env.say(null, "message.lewandivka.postgame.cat", "cat.lewandivka.name." + n, catsFound());
        if (catsFound() >= CATS && env.record().setFlag("cats.done")) {
            env.give(null, QuestItems.TICKET_SQUARE, 1);
            env.award("cats12");
        }
        return true;
    }

    private boolean bowl(int n, UUID player, String item) {
        if (n < 1 || n > BOWLS) {
            return false;
        }
        if (env.record().flag("seeds.done") || env.record().flag("bowl." + n)) {
            env.say(player, "message.lewandivka.stash.empty");
            return true;
        }
        if (!QuestItems.SEEDS.equals(item) || !env.take(player, QuestItems.SEEDS, SEEDS_PER_BOWL)) {
            env.say(player, "message.lewandivka.debtor.need_seeds");
            return true;
        }
        env.record().setFlag("bowl." + n);
        env.station("seed_bowl_" + n, "stage", "1");
        env.sound("seed_bowl_" + n, "seed.crunch");
        eatAt[n] = -1;
        return true;
    }

    private boolean validator(UUID player, String item) {
        if (!env.record().flag("cats.done")) {
            env.say(player, "message.lewandivka.not_yet");
            return true;
        }
        if (riding || env.tramBusy(TRAM)) {
            return true;
        }
        if (!QuestItems.TICKET_SQUARE.equals(item) || !env.take(player, QuestItems.TICKET_SQUARE, 1)) {
            env.say(player, "message.lewandivka.ticket.invalid");
            return true;
        }
        env.sound("wrong_tram_stop", "tram.validate");
        env.sound("wrong_tram_stop", "tram.bell");
        env.tramDrive(TRAM, java.util.List.of("district:wrong_tram_stop", "district:tram_fog_start"), 0.5);
        env.tramBoard(TRAM, env.playersAt("wrong_tram_stop", 7));
        riding = true;
        env.say(null, "message.lewandivka.postgame.tram");
        return true;
    }

    /** Mr. Shlahbaum says what is left to do, and once everything is done, what was in the package. */
    private boolean shlahbaum(UUID player) {
        if (!env.record().flag("cats.done")) {
            env.dialogue("post_cats12");
        } else if (!env.record().flag("seeds.done")) {
            env.dialogue("post_seeds");
        } else if (!env.record().flag("tram.done")) {
            env.dialogue("post_tram");
        } else if (!(env.world().hasEncounter("garage0") && env.world().encounter("garage0").flag("done")) && !env.record().flag("garage0.seen")) {
            env.record().setFlag("garage0.seen");
            env.dialogue("post_garage0");
        } else if (!env.record().flag("package.revealed")) {
            env.ask("post_package", (who, choice) -> {
                if ("yes".equals(choice) && env.record().setFlag("package.revealed")) {
                    env.dialogue("post_package_reveal");
                    env.give(who, QuestItems.SOCK, 1);
                    env.award("better_not_ask");
                }
            });
        } else {
            env.dialogue("post_package_reveal");
        }
        return true;
    }

    // ------------------------------------------------------------------ the district at free play

    @Override
    public void tick() {
        if (!active()) {
            return;
        }
        long now = env.now();
        if (now >= checkAt) {
            checkAt = now + 100;
            ensureCats();
        }
        seeds(now);
        if (riding && !env.tramBusy(TRAM)) {
            riding = false;
            env.tramClear(TRAM, "garage0:entry");
            if (env.record().setFlag("tram.done")) {
                env.award("wrong_tram");
            }
            env.dialogue("post_garage0");
        }
    }

    /** Distance within which somebody has the cat's chunk loaded: only there can a missing cat be told from an unloaded one. */
    public static final double CAT_RANGE = 70;
    private final int[] missing = new int[CATS + 1];

    /**
     * Cats that were not found yet sit at their spots. A cat is only brought back where somebody stands near and after two
     * empty looks in a row: an entity of a chunk that is not loaded cannot be counted, and guessing would add a cat at
     * every look (or every server restart).
     */
    private void ensureCats() {
        for (int i = 1; i <= CATS; i++) {
            if (env.record().flag("cat." + i)) {
                continue;
            }
            if (env.playersAt("cat_spot_" + i, CAT_RANGE).isEmpty() || env.alive(CAT_TAG + i) > 0) {
                missing[i] = 0;
                continue;
            }
            if (++missing[i] >= 2) {
                missing[i] = 0;
                env.spawn(i % 2 == 0 ? "chinazik" : "metadonna", "cat_spot_" + i, 1, CAT_TAG + i);
            }
        }
    }

    private void seeds(long now) {
        if (env.record().flag("seeds.done")) {
            return;
        }
        for (int n = 1; n <= BOWLS; n++) {
            if (!env.record().flag("bowl." + n)) {
                continue;
            }
            if (eatUntil[n] == 0 && env.night()) {
                if (eatAt[n] <= 0) {
                    eatAt[n] = now + 100 + rng.nextInt(300);
                } else if (now >= eatAt[n]) {
                    env.spawn("metadonna", "seed_bowl_" + n, 1, THIEF_TAG + n);
                    env.sound("seed_bowl_" + n, "cat.eat");
                    eatUntil[n] = now + EAT_TICKS;
                    witnessed[n] = false;
                }
            }
            if (eatUntil[n] > 0) {
                if (!env.playersAt("seed_bowl_" + n, 11).isEmpty()) {
                    witnessed[n] = true;
                }
                if (now >= eatUntil[n]) {
                    env.despawn(THIEF_TAG + n);
                    env.station("seed_bowl_" + n, "stage", "2");
                    env.record().clearFlag("bowl." + n);
                    eatUntil[n] = 0;
                    eatAt[n] = 0;
                    if (witnessed[n] && env.record().setFlag("seen." + n)) {
                        env.say(null, "message.lewandivka.postgame.seeds");
                        if (bowlsWitnessed() >= BOWLS && env.record().setFlag("seeds.done")) {
                            env.give(null, QuestItems.TOKEN, 2);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void rebuild() {
        for (int n = 1; n <= BOWLS; n++) {
            env.station("seed_bowl_" + n, "stage", env.record().flag("bowl." + n) ? "1" : "0");
        }
    }

    @Override
    public void reset() {
        for (int n = 1; n <= BOWLS; n++) {
            env.despawn(THIEF_TAG + n);
            eatUntil[n] = 0;
            eatAt[n] = 0;
        }
        if (riding || env.tramBusy(TRAM)) {
            env.tramClear(TRAM, null);
            riding = false;
        }
    }

    @Override
    public boolean complete() {
        return env.record().flag("package.revealed");
    }
}
