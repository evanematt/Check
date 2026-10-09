package com.lewandivka.core.story;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.quest.QuestItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The story table of the whole campaign. Systems report events ({@link Events}); the director decides which step they
 * complete, advances the campaign and asks the game for the side effects (items, abilities, dialogue ...).
 *
 * <ul>
 *   <li>A rule only fires in its own step. Events that arrive early are remembered ("pending") and fire the moment the
 *       campaign reaches the step, so doing things in an unexpected order never blocks the story.</li>
 *   <li>Counted rules (clues, waves, pedestals) need {@code counterMax} events of the step; the ones that arrive early
 *       are remembered one by one and counted when the step begins (the pedestals may be filled before the compass
 *       arrives, and a filled pedestal cannot be filled again).</li>
 *   <li>Everything is plain data on top of {@link WorldProgress}; the table is also the source of docs/QUEST_FLOW.md.</li>
 * </ul>
 */
public final class CampaignDirector {

    /** What the game has to do for the story. All methods are called on the server thread. */
    public interface Effects {
        /** The campaign entered a step: toast, notebook, compass and HUD update. */
        void entered(QuestStep step);

        /** A counted objective advanced (clues 3 of 5). */
        void counter(int value, int max);

        /** Gives an item to every participating player. */
        void giveEveryone(String item, int count);

        /** Unlocks an ability for every participating player (and every player who joins later). */
        void unlock(Ability ability);

        void ringFragment();

        void dialogue(String scriptId);

        /** Makes a story NPC appear (shlahbaum, debtor, cats at the base ...). */
        void spawnNpc(String npc);

        void portal(boolean active);

        void unlockStructure(String structure);

        void cinematic(String id);

        void toast(String langKey);
    }

    public record Rule(String event, QuestStep from, QuestStep to, boolean counted, Consumer<Effects> effect) {
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final Map<String, List<Rule>> BY_EVENT = new LinkedHashMap<>();

    private static void rule(String event, QuestStep from, QuestStep to, Consumer<Effects> effect) {
        add(new Rule(event, from, to, false, effect));
    }

    private static void rule(String event, QuestStep from, QuestStep to) {
        rule(event, from, to, fx -> { });
    }

    private static void counted(String event, QuestStep from, QuestStep to, Consumer<Effects> effect) {
        add(new Rule(event, from, to, true, effect));
    }

    private static void add(Rule r) {
        RULES.add(r);
        BY_EVENT.computeIfAbsent(r.event(), k -> new ArrayList<>()).add(r);
    }

    static {
        // ---- prologue and the first evening
        counted(Events.POI_VISITED, QuestStep.EXPLORE_DISTRICT, QuestStep.COLLECT_TOKENS, fx -> { });
        counted(Events.TOKEN_COLLECTED, QuestStep.COLLECT_TOKENS, QuestStep.CRAFT_KIOSK, fx -> fx.toast("message.lewandivka.kiosk.unlocked"));
        rule(Events.KIOSK_CRAFTED, QuestStep.CRAFT_KIOSK, QuestStep.PLACE_KIOSK);
        rule(Events.KIOSK_PLACED, QuestStep.PLACE_KIOSK, QuestStep.TALK_SHLAHBAUM, fx -> fx.spawnNpc("pan_shlahbaum"));
        rule(Events.SHLAHBAUM_MET, QuestStep.TALK_SHLAHBAUM, QuestStep.DEBTOR_NOTE, fx -> fx.giveEveryone(QuestItems.DEBTOR_NOTE, 1));
        // ---- the debtor
        rule(Events.DEBTOR_NOTE_USED, QuestStep.DEBTOR_NOTE, QuestStep.DEBTOR_CLUES);
        counted(Events.DEBTOR_CLUE, QuestStep.DEBTOR_CLUES, QuestStep.DEBTOR_CHASE, fx -> fx.spawnNpc("debtor"));
        rule(Events.DEBTOR_CAUGHT, QuestStep.DEBTOR_CHASE, QuestStep.DEBTOR_RESOLVE);
        rule(Events.DEBTOR_RESOLVED, QuestStep.DEBTOR_RESOLVE, QuestStep.KETTLE_TEST, fx -> fx.giveEveryone(QuestItems.KETTLE, 1));
        rule(Events.KETTLE_PLACED, QuestStep.KETTLE_TEST, QuestStep.GARAGE_FIND, fx -> {
            fx.dialogue("shlahbaum_kettle");
            fx.giveEveryone(QuestItems.GARAGE_NOTE, 1);
        });
        // ---- garage no. 13
        rule(Events.G13_ENTERED, QuestStep.GARAGE_FIND, QuestStep.GARAGE_PANELS);
        counted(Events.G13_POWER, QuestStep.GARAGE_PANELS, QuestStep.GARAGE_PACKAGE, fx -> fx.toast("message.lewandivka.garage.open"));
        rule(Events.G13_PACKAGE_TAKEN, QuestStep.GARAGE_PACKAGE, QuestStep.GARAGE_ESCAPE);
        rule(Events.G13_ESCAPED, QuestStep.GARAGE_ESCAPE, QuestStep.GARAGE_DELIVER);
        rule(Events.PACKAGE_DELIVERED, QuestStep.GARAGE_DELIVER, QuestStep.TRAM_WAIT, fx -> {
            fx.dialogue("shlahbaum_package");
            fx.giveEveryone(QuestItems.TRAM_NOTE, 1);
        });
        // ---- the last tram
        rule(Events.TRAM_STARTED, QuestStep.TRAM_WAIT, QuestStep.TRAM_FIGHT, fx -> fx.cinematic("last_tram"));
        counted(Events.TRAM_WAVE, QuestStep.TRAM_FIGHT, QuestStep.TRAM_REPORT, fx -> {
            fx.giveEveryone(QuestItems.COMPOSTER, 1);
            fx.toast("message.lewandivka.tram.reward");
        });
        rule(Events.TRAM_REPORTED, QuestStep.TRAM_REPORT, QuestStep.CHROMA_TAKE, fx -> {
            fx.dialogue("shlahbaum_chroma");
            fx.giveEveryone(QuestItems.TABLET, 1);
        });
        rule(Events.CHROMA_SYNCED, QuestStep.CHROMA_TAKE, QuestStep.TRANSITION, fx -> fx.cinematic("transition"));
        rule(Events.TRANSITION_DONE, QuestStep.TRANSITION, QuestStep.BASE_WAKE, fx -> fx.spawnNpc("base_empty"));
        // ---- the base
        rule(Events.BASE_COMPASS, QuestStep.BASE_WAKE, QuestStep.BASE_PEDESTALS, fx -> fx.giveEveryone(QuestItems.COMPASS, 1));
        counted(Events.BASE_PEDESTAL, QuestStep.BASE_PEDESTALS, QuestStep.RG_TRAVEL, fx -> {
            fx.portal(true);
            fx.toast("message.lewandivka.portal.open");
        });
        // ---- rainbow garage
        rule(Events.RG_ENTERED, QuestStep.RG_TRAVEL, QuestStep.RG_WINGS);
        counted(Events.RG_WING, QuestStep.RG_WINGS, QuestStep.RG_BOSS, fx -> { });
        rule(Events.BOSS_GARAGE_KING, QuestStep.RG_BOSS, QuestStep.SH_DASH, fx -> {
            fx.unlock(Ability.DASH);
            fx.ringFragment();
        });
        // ---- shelter of lost names
        rule(Events.SH_HALL_ENTERED, QuestStep.SH_DASH, QuestStep.SH_LEVERS);
        rule(Events.SH_LEVERS_DONE, QuestStep.SH_LEVERS, QuestStep.SH_CHINAZIK, fx -> {
            fx.spawnNpc("chinazik");
            fx.dialogue("chinazik_first");
        });
        rule(Events.SH_CHINAZIK_DONE, QuestStep.SH_CHINAZIK, QuestStep.SH_METADONNA, fx -> fx.dialogue("chinazik_named"));
        rule(Events.SH_METADONNA_FOUND, QuestStep.SH_METADONNA, QuestStep.SH_BOSS, fx -> {
            fx.spawnNpc("metadonna");
            fx.dialogue("metadonna_named");
        });
        rule(Events.BOSS_COLLAR_COLLECTOR, QuestStep.SH_BOSS, QuestStep.AQ_FIND, fx -> {
            fx.ringFragment();
            fx.spawnNpc("base_cats");
        });
        // ---- dry lake aquapark
        rule(Events.AQ_ENTERED, QuestStep.AQ_FIND, QuestStep.AQ_PUMPS);
        counted(Events.AQ_PUMP, QuestStep.AQ_PUMPS, QuestStep.AQ_BOSS, fx -> { });
        rule(Events.BOSS_LADY_VORTEX, QuestStep.AQ_BOSS, QuestStep.SKY_ASCENT, fx -> {
            fx.unlock(Ability.SPRING_INSOLES);
            fx.ringFragment();
        });
        // ---- the sky
        rule(Events.SKY_STOP_REACHED, QuestStep.SKY_ASCENT, QuestStep.SKY_RIDE);
        rule(Events.SKY_ARRIVED, QuestStep.SKY_RIDE, QuestStep.SKY_SWITCHES);
        rule(Events.DEPOT_SWITCHES_SOLVED, QuestStep.SKY_SWITCHES, QuestStep.SKY_TICKETS);
        counted(Events.DEPOT_TICKET, QuestStep.SKY_TICKETS, QuestStep.SKY_BOSS, fx -> fx.toast("message.lewandivka.ticket.got"));
        rule(Events.BOSS_CONDUCTOR, QuestStep.SKY_BOSS, QuestStep.TOWER_RING, fx -> {
            fx.unlock(Ability.GLIDER);
            fx.ringFragment();
            fx.cinematic("ring_restored");
        });
        // ---- the tower
        rule(Events.RING_RESTORED, QuestStep.TOWER_RING, QuestStep.TOWER_APPROACH, fx -> fx.dialogue("head_message"));
        rule(Events.APPROACH_DONE, QuestStep.TOWER_APPROACH, QuestStep.TOWER_CLIMB);
        counted(Events.TOWER_FLOOR, QuestStep.TOWER_CLIMB, QuestStep.FINAL_FIGHT, fx -> { });
        rule(Events.BOSS_COLORLESS_HEAD, QuestStep.FINAL_FIGHT, QuestStep.EPI_RETURN, fx -> fx.cinematic("rescue_ring"));
        // ---- epilogue and postgame
        rule(Events.EPILOGUE_BASE, QuestStep.EPI_RETURN, QuestStep.EPI_PORTAL, fx -> fx.spawnNpc("base_epilogue"));
        rule(Events.EPILOGUE_PORTAL, QuestStep.EPI_PORTAL, QuestStep.EPI_MORNING, fx -> fx.dialogue("epilogue_morning"));
        rule(Events.EPILOGUE_DONE, QuestStep.EPI_MORNING, QuestStep.POST_FREE, fx -> {
            fx.cinematic("credits");
            fx.toast("message.lewandivka.postgame.unlocked");
        });
    }

    /** Every rule, in campaign order (used by the docs generator and the tests). */
    public static List<Rule> rules() {
        return Collections.unmodifiableList(RULES);
    }

    public static Rule ruleFor(QuestStep from) {
        for (Rule r : RULES) {
            if (r.from() == from) {
                return r;
            }
        }
        return null;
    }

    private final WorldProgress world;
    private final Effects fx;

    public CampaignDirector(WorldProgress world, Effects fx) {
        this.world = world;
        this.fx = fx;
    }

    /**
     * Reports an event.
     *
     * @return true when the campaign state changed (a counter moved or a step was completed)
     */
    public boolean event(String id) {
        List<Rule> rules = BY_EVENT.get(id);
        if (rules == null) {
            return false;
        }
        boolean changed = false;
        for (Rule r : rules) {
            if (world.step() == r.from()) {
                changed |= fire(r);
            } else if (world.step().isBefore(r.from())) {
                // too early: remember it, it counts as soon as the story gets there
                changed |= r.counted() ? rememberOccurrence(r) : world.setFlag(pendingFlag(id));
            }
        }
        return changed;
    }

    /** One flag per occurrence of a counted event done in advance, up to what the step asks for. */
    private boolean rememberOccurrence(Rule r) {
        int max = Math.max(1, r.from().counterMax);
        for (int i = 1; i <= max; i++) {
            if (world.setFlag(pendingFlag(r.event()) + "#" + i)) {
                return true;
            }
        }
        return false;
    }

    private boolean fire(Rule r) {
        if (r.counted()) {
            int max = Math.max(1, r.from().counterMax);
            int n = world.addStepCounter(1);
            if (n < max) {
                fx.counter(n, max);
                return true;
            }
        }
        if (!world.advanceTo(r.to())) {
            return false;
        }
        world.clearFlag(pendingFlag(r.event()));
        fx.entered(r.to());
        r.effect().accept(fx);
        settle();
        return true;
    }

    /** After a step change, events that were already reported for the new step complete it right away. */
    private void settle() {
        for (int guard = 0; guard < 60; guard++) {
            Rule next = null;
            for (Rule r : RULES) {
                if (r.from() == world.step() && !r.counted() && world.flag(pendingFlag(r.event()))) {
                    next = r;
                    break;
                }
            }
            if (next == null) {
                if (recallOccurrences()) {
                    continue;
                }
                return;
            }
            world.clearFlag(pendingFlag(next.event()));
            if (!world.advanceTo(next.to())) {
                return;
            }
            fx.entered(next.to());
            next.effect().accept(fx);
        }
    }

    /**
     * Counts what was done in advance for the current step.
     *
     * @return true when that completed the step (the loop in {@link #settle()} then looks at the next one)
     */
    private boolean recallOccurrences() {
        for (Rule r : RULES) {
            if (r.from() != world.step() || !r.counted()) {
                continue;
            }
            int max = Math.max(1, r.from().counterMax);
            int n = 0;
            for (int i = 1; i <= max; i++) {
                if (world.clearFlag(pendingFlag(r.event()) + "#" + i)) {
                    n++;
                }
            }
            if (n == 0) {
                continue;
            }
            int total = world.addStepCounter(n);
            if (total < max) {
                fx.counter(total, max);
                return false;
            }
            if (!world.advanceTo(r.to())) {
                return false;
            }
            fx.entered(r.to());
            r.effect().accept(fx);
            return true;
        }
        return false;
    }

    public static String pendingFlag(String event) {
        return "pending." + event;
    }
}
