package com.lewandivka.core.story;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.story.CampaignDirector.Effects;
import com.lewandivka.core.story.CampaignDirector.Rule;
import com.lewandivka.core.text.DialogueBook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignDirectorTest {

    /** Records everything the story asks the game to do. */
    private static final class Rec implements Effects {
        final List<QuestStep> entered = new ArrayList<>();
        final List<String> given = new ArrayList<>();
        final List<Ability> abilities = new ArrayList<>();
        final List<String> dialogues = new ArrayList<>();
        final List<String> npcs = new ArrayList<>();
        final List<String> cinematics = new ArrayList<>();
        int rings;
        boolean portal;
        int lastCounter = -1;

        @Override
        public void entered(QuestStep step) {
            entered.add(step);
        }

        @Override
        public void counter(int value, int max) {
            lastCounter = value;
        }

        @Override
        public void giveEveryone(String item, int count) {
            given.add(item);
        }

        @Override
        public void unlock(Ability ability) {
            abilities.add(ability);
        }

        @Override
        public void ringFragment() {
            rings++;
        }

        @Override
        public void dialogue(String scriptId) {
            dialogues.add(scriptId);
        }

        @Override
        public void spawnNpc(String npc) {
            npcs.add(npc);
        }

        @Override
        public void portal(boolean active) {
            portal = active;
        }

        @Override
        public void unlockStructure(String structure) {
        }

        @Override
        public void cinematic(String id) {
            cinematics.add(id);
        }

        @Override
        public void toast(String langKey) {
        }
    }

    /** Plays the golden path: every step's own event, as often as the step needs it. */
    private static void play(CampaignDirector d, WorldProgress w, QuestStep until) {
        for (int guard = 0; guard < 500 && w.step().isBefore(until); guard++) {
            Rule r = CampaignDirector.ruleFor(w.step());
            assertNotNull(r, "no rule leaves " + w.step());
            d.event(r.event());
        }
    }

    @Test
    void theGoldenPathWalksEveryStepInOrderAndEndsInThePostgame() {
        WorldProgress w = new WorldProgress();
        Rec fx = new Rec();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.POST_FREE);
        assertEquals(QuestStep.POST_FREE, w.step());
        List<QuestStep> expected = new ArrayList<>(QuestStep.ordered());
        expected.remove(QuestStep.EXPLORE_DISTRICT);
        assertEquals(expected, fx.entered, "every step is entered exactly once, in campaign order");
        assertEquals(List.of(Ability.DASH, Ability.SPRING_INSOLES, Ability.GLIDER), fx.abilities);
        assertEquals(4, fx.rings, "one ring fragment per boss of Chromandivka");
        assertTrue(fx.portal);
        assertTrue(fx.npcs.containsAll(List.of("pan_shlahbaum", "debtor", "chinazik", "metadonna", "base_cats")));
        assertTrue(fx.cinematics.containsAll(List.of("last_tram", "transition", "ring_restored", "rescue_ring", "credits")));
    }

    @Test
    void everyStepButTheLastHasExactlyOneRuleAndTheRulesChainInOrder() {
        List<QuestStep> ordered = QuestStep.ordered();
        for (int i = 0; i < ordered.size() - 1; i++) {
            QuestStep step = ordered.get(i);
            long rules = CampaignDirector.rules().stream().filter(r -> r.from() == step).count();
            assertEquals(1, rules, "rules leaving " + step);
            Rule r = CampaignDirector.ruleFor(step);
            assertEquals(ordered.get(i + 1), r.to(), "rule " + r.event() + " must lead to the next step");
            assertTrue(!r.counted() || step.counterMax > 0, "counted rules belong to counted steps: " + step);
        }
        assertNull(CampaignDirector.ruleFor(QuestStep.POST_FREE));
    }

    @Test
    void countedStepsNeedEveryEventAndReportProgress() {
        WorldProgress w = new WorldProgress();
        Rec fx = new Rec();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.DEBTOR_CLUES);
        assertEquals(QuestStep.DEBTOR_CLUES, w.step());
        for (int i = 1; i < 5; i++) {
            assertTrue(d.event(Events.DEBTOR_CLUE));
            assertEquals(QuestStep.DEBTOR_CLUES, w.step());
            assertEquals(i, fx.lastCounter);
        }
        assertTrue(d.event(Events.DEBTOR_CLUE));
        assertEquals(QuestStep.DEBTOR_CHASE, w.step());
        assertTrue(fx.npcs.contains("debtor"));
        assertEquals(0, w.stepCounter(), "the counter starts again in the next step");
    }

    @Test
    void wrongEventsAndRepeatedEventsAreHarmless() {
        WorldProgress w = new WorldProgress();
        CampaignDirector d = new CampaignDirector(w, new Rec());
        assertFalse(d.event("no.such.event"));
        d.event(Events.BOSS_COLORLESS_HEAD);
        assertEquals(QuestStep.EXPLORE_DISTRICT, w.step(), "an event of a far later step does not advance anything");
        play(d, w, QuestStep.DEBTOR_CHASE);
        QuestStep at = w.step();
        assertFalse(d.event(Events.TOKEN_COLLECTED), "an event of an earlier step is ignored");
        assertFalse(d.event(Events.KIOSK_PLACED));
        assertEquals(at, w.step());
    }

    @Test
    void earlyCompletionIsRememberedAndCountsWhenTheStoryArrives() {
        WorldProgress w = new WorldProgress();
        Rec fx = new Rec();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.KETTLE_TEST);
        // the players walk into garage 13 and take care of the package long before the kettle test is done
        d.event(Events.G13_ENTERED);
        d.event(Events.G13_PACKAGE_TAKEN);
        assertEquals(QuestStep.KETTLE_TEST, w.step());
        assertTrue(w.flag(CampaignDirector.pendingFlag(Events.G13_ENTERED)));
        d.event(Events.KETTLE_PLACED);
        // GARAGE_FIND was completed by the pending event, GARAGE_PANELS is a counted step and waits for its power points
        assertEquals(QuestStep.GARAGE_PANELS, w.step());
        assertTrue(fx.entered.contains(QuestStep.GARAGE_FIND));
        for (int i = 0; i < 3; i++) {
            d.event(Events.G13_POWER);
        }
        // the early package event is consumed right away
        assertEquals(QuestStep.GARAGE_ESCAPE, w.step());
    }

    @Test
    void countedObjectivesDoneInAdvanceCountWhenTheStoryArrives() {
        WorldProgress w = new WorldProgress();
        Rec fx = new Rec();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.BASE_WAKE);
        // the party puts the artifacts on the pedestals before the compass has arrived; a filled pedestal cannot be filled again
        for (int i = 0; i < 4; i++) {
            d.event(Events.BASE_PEDESTAL);
        }
        assertEquals(QuestStep.BASE_WAKE, w.step());
        assertFalse(fx.portal);
        d.event(Events.BASE_COMPASS);
        assertEquals(QuestStep.RG_TRAVEL, w.step(), "the four pedestals counted the moment their step began");
        assertTrue(fx.portal);
    }

    @Test
    void bossRewardsAreGrantedByTheRightBosses() {
        WorldProgress w = new WorldProgress();
        Rec fx = new Rec();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.RG_BOSS);
        assertTrue(fx.abilities.isEmpty());
        d.event(Events.BOSS_GARAGE_KING);
        assertEquals(List.of(Ability.DASH), fx.abilities);
        assertEquals(1, fx.rings);
        play(d, w, QuestStep.AQ_BOSS);
        d.event(Events.BOSS_LADY_VORTEX);
        assertEquals(List.of(Ability.DASH, Ability.SPRING_INSOLES), fx.abilities);
        for (Ability a : Ability.values()) {
            assertEquals(a.unlockBoss.replace("_", ""), Events.boss(a.unlockBoss).substring("boss.".length()).replace("_", ""));
        }
    }

    @Test
    void everyReferencedDialogueItemAndEventExists() {
        Set<String> items = new java.util.HashSet<>();
        ModItems.ALL.forEach(i -> items.add(i.id));
        Rec fx = new Rec();
        WorldProgress w = new WorldProgress();
        CampaignDirector d = new CampaignDirector(w, fx);
        play(d, w, QuestStep.POST_FREE);
        for (String script : fx.dialogues) {
            assertNotNull(DialogueBook.get(script), "dialogue script " + script);
        }
        for (String item : fx.given) {
            assertTrue(items.contains(item), "item " + item);
        }
        Set<QuestStep> reached = EnumSet.noneOf(QuestStep.class);
        reached.addAll(fx.entered);
        reached.add(QuestStep.EXPLORE_DISTRICT);
        assertEquals(EnumSet.allOf(QuestStep.class), reached);
    }
}
