package com.lewandivka.core;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.dialogue.DialogueChoice;
import com.lewandivka.core.dialogue.DialogueLine;
import com.lewandivka.core.dialogue.DialogueRunner;
import com.lewandivka.core.dialogue.DialogueScript;
import com.lewandivka.core.puzzle.CatRoute;
import com.lewandivka.core.puzzle.FleePlanner;
import com.lewandivka.core.puzzle.PaintPuzzle;
import com.lewandivka.core.puzzle.PlatformDecay;
import com.lewandivka.core.puzzle.TramNetwork;
import com.lewandivka.core.puzzle.ZonePlanner;
import com.lewandivka.core.quest.QuestItemLedger;
import com.lewandivka.core.quest.QuestItems;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestLogicTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    // ---------------------------------------------------------------- dialogue

    @Test
    void dialogueLinesAppearOnTimeWithoutBlocking() {
        DialogueScript s = DialogueScript.builder("epilogue")
                .say("shlahbaum", 0)
                .say("shlahbaum", 60)
                .say("shlahbaum", 60)
                .build();
        DialogueRunner r = new DialogueRunner(s, 1000);
        assertEquals(1, r.due(1000).size());
        assertTrue(r.due(1059).isEmpty(), "pause between lines");
        List<DialogueLine> second = r.due(1060);
        assertEquals(1, second.size());
        assertEquals("dialogue.lewandivka.epilogue.2", second.get(0).textKey());
        assertFalse(r.linesFinished());
        assertEquals(1, r.due(1120).size());
        assertTrue(r.linesFinished());
        assertTrue(r.finished());
    }

    @Test
    void dialogueChoicesAreValidatedAgainstTheScript() {
        DialogueScript s = DialogueScript.builder("seeds").say("gopnik", 0)
                .choice("give").choice("leave").choice("attack").build();
        DialogueRunner r = new DialogueRunner(s, 0);
        assertNull(r.choose("give"), "nothing may be chosen before the question is shown");
        r.due(0);
        assertTrue(r.shouldOfferChoices());
        assertFalse(r.shouldOfferChoices(), "offered exactly once");
        assertNull(r.choose("rob"), "unknown choice id is rejected");
        DialogueChoice c = r.choose("give");
        assertNotNull(c);
        assertEquals("dialogue.lewandivka.seeds.choice.give", c.textKey());
        assertNull(r.choose("leave"), "only one answer is accepted");
        assertTrue(r.finished());
    }

    @Test
    void emptyScriptIsFinishedImmediately() {
        DialogueRunner r = new DialogueRunner(DialogueScript.builder("empty").build(), 0);
        assertTrue(r.due(0).isEmpty());
        assertTrue(r.finished());
    }

    // ---------------------------------------------------------------- quest item ledger

    private static Map<UUID, Map<String, Integer>> inv(Object... pairs) {
        Map<UUID, Map<String, Integer>> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> items = (Map<String, Integer>) pairs[i + 1];
            m.put((UUID) pairs[i], items);
        }
        return m;
    }

    @Test
    void notebookIsAlwaysRestored() {
        WorldProgress w = new WorldProgress();
        List<QuestItemLedger.Grant> g = QuestItemLedger.missing(QuestItemLedger.needs(w), inv(A, Map.of(), B, Map.of(QuestItems.NOTEBOOK, 1)));
        assertEquals(1, g.size());
        assertEquals(A, g.get(0).player());
        assertEquals(QuestItems.NOTEBOOK, g.get(0).item());
    }

    @Test
    void lostPackageIsRestoredButNotWhilePlacedInTheWorld() {
        WorldProgress w = new WorldProgress();
        w.forceStep(QuestStep.GARAGE_ESCAPE);
        Map<UUID, Map<String, Integer>> inventories = inv(A, Map.of(QuestItems.NOTEBOOK, 1, QuestItems.KETTLE, 1), B, Map.of(QuestItems.NOTEBOOK, 1));
        List<QuestItemLedger.Grant> g = QuestItemLedger.missing(QuestItemLedger.needs(w), inventories);
        assertEquals(1, g.size());
        assertEquals(QuestItems.PACKAGE, g.get(0).item());
        assertEquals(A, g.get(0).player(), "party-level items go to the first participant");

        w.setFlag(QuestItemLedger.FLAG_PACKAGE_PLACED);
        assertTrue(QuestItemLedger.missing(QuestItemLedger.needs(w), inventories).isEmpty(), "package stands on the ground");
    }

    @Test
    void partyLevelItemOwnedByAnyoneIsNotDuplicated() {
        WorldProgress w = new WorldProgress();
        w.forceStep(QuestStep.GARAGE_ESCAPE);
        Map<UUID, Map<String, Integer>> inventories = inv(
                A, Map.of(QuestItems.NOTEBOOK, 1, QuestItems.KETTLE, 1),
                B, Map.of(QuestItems.NOTEBOOK, 1, QuestItems.PACKAGE, 1));
        assertTrue(QuestItemLedger.missing(QuestItemLedger.needs(w), inventories).isEmpty());
    }

    @Test
    void compassComposterAndTabletArePerPlayer() {
        WorldProgress w = new WorldProgress();
        w.forceStep(QuestStep.CHROMA_TAKE);
        List<QuestItemLedger.Need> needs = QuestItemLedger.needs(w);
        assertTrue(needs.stream().anyMatch(n -> n.item().equals(QuestItems.TABLET) && n.perPlayer()));
        assertTrue(needs.stream().anyMatch(n -> n.item().equals(QuestItems.COMPOSTER) && n.perPlayer()));
        assertTrue(needs.stream().noneMatch(n -> n.item().equals(QuestItems.COMPASS)));
        w.forceStep(QuestStep.RG_TRAVEL);
        assertTrue(QuestItemLedger.needs(w).stream().anyMatch(n -> n.item().equals(QuestItems.COMPASS) && n.perPlayer()));
        w.forceStep(QuestStep.POST_FREE);
        assertTrue(QuestItemLedger.needs(w).stream().noneMatch(n -> n.item().equals(QuestItems.COMPASS)),
                "compass is no longer protected after the campaign");
    }

    @Test
    void batteriesAreOwedByWingsMinusInstalled() {
        WorldProgress w = new WorldProgress();
        w.forceStep(QuestStep.RG_WINGS);
        w.setCounter(QuestItemLedger.COUNTER_WINGS_DONE, 2);
        w.setCounter(QuestItemLedger.COUNTER_BATTERIES_INSTALLED, 1);
        QuestItemLedger.Need need = QuestItemLedger.needs(w).stream()
                .filter(n -> n.item().equals(QuestItems.BATTERY)).findFirst().orElseThrow();
        assertEquals(1, need.count());
        w.setCounter(QuestItemLedger.COUNTER_BATTERIES_INSTALLED, 2);
        assertTrue(QuestItemLedger.needs(w).stream().noneMatch(n -> n.item().equals(QuestItems.BATTERY)));
    }

    @Test
    void everyQuestStepProducesAConsistentLedger() {
        for (QuestStep step : QuestStep.values()) {
            WorldProgress w = new WorldProgress();
            w.forceStep(step);
            for (QuestItemLedger.Need n : QuestItemLedger.needs(w)) {
                assertTrue(n.count() > 0, step + " " + n.item());
            }
        }
    }

    // ---------------------------------------------------------------- flee planner

    private static FleePlanner ring() {
        // square loop 0-1-2-3 with a dead end 4 hanging off node 0
        List<FleePlanner.Node> nodes = List.of(
                new FleePlanner.Node(0, 0, 0, false), new FleePlanner.Node(1, 40, 0, false),
                new FleePlanner.Node(2, 40, 40, false), new FleePlanner.Node(3, 0, 40, false),
                new FleePlanner.Node(4, -30, 0, true));
        return new FleePlanner(nodes, List.of(new int[] {0, 1}, new int[] {1, 2}, new int[] {2, 3}, new int[] {3, 0}, new int[] {0, 4}));
    }

    @Test
    void runnerFleesAwayFromThePlayerAndAvoidsDeadEnds() {
        FleePlanner p = ring();
        Random rng = new Random(1);
        // player stands near node 1; runner at 0 must not run towards node 1, and prefers the loop over the dead end
        for (int i = 0; i < 50; i++) {
            assertEquals(3, p.choose(0, -1, List.<double[]>of(new double[] {38, 2}), rng));
        }
    }

    @Test
    void runnerCanBeCutOffByTwoPlayers() {
        FleePlanner p = ring();
        Random rng = new Random(2);
        List<double[]> two = List.of(new double[] {38, 2}, new double[] {2, 38}); // near nodes 1 and 3
        int choice = p.choose(0, -1, two, rng);
        assertEquals(4, choice, "both loop exits are covered, only the dead end is left");
    }

    @Test
    void nearestNodeAndNeighbours() {
        FleePlanner p = ring();
        assertEquals(2, p.nearest(39, 41));
        assertEquals(3, p.neighbours(0).size());
        assertTrue(FleePlanner.stumbles(1.0, new Random(0)));
        assertFalse(FleePlanner.stumbles(0.0, new Random(0)));
    }

    // ---------------------------------------------------------------- cat route

    @Test
    void chinazikWalksFoodPointToFoodPointAndEndsOnTheRug() {
        CatRoute r = new CatRoute(4);
        assertEquals(1, r.nextFoodPoint());
        assertFalse(r.placeFood(2), "food must be placed in order");
        assertTrue(r.placeFood(1));
        assertFalse(r.placeFood(2), "he is still walking");
        r.arrive();
        assertEquals(CatRoute.State.EATING, r.state());
        r.finishEating();
        assertTrue(r.placeFood(2));
        r.arrive();
        r.finishEating();
        assertTrue(r.placeFood(3));
        r.arrive();
        r.finishEating();
        assertTrue(r.placeFood(4));
        r.arrive();
        assertEquals(CatRoute.State.DONE, r.state());
        assertEquals(-1, r.nextFoodPoint());
    }

    @Test
    void crowdingTheCatMakesHimRetreatToThePreviousSafePoint() {
        CatRoute r = new CatRoute(4);
        r.placeFood(1);
        r.arrive();
        r.finishEating();
        r.placeFood(2);
        assertEquals(1, r.scare(), "walking: turn back to the point he came from");
        assertEquals(CatRoute.State.WAITING, r.state());
        assertEquals(1, r.at());
        assertEquals(1, r.retreats());
        assertTrue(r.placeFood(2));
        r.arrive();
        assertEquals(1, r.scare(), "eating at point 2: one point further back");
        assertEquals(1, r.at());
        // he never retreats past the start
        CatRoute s = new CatRoute(2);
        s.placeFood(1);
        s.arrive();
        assertEquals(0, s.scare());
    }

    // ---------------------------------------------------------------- paint puzzle

    @Test
    void paintMixingFollowsPrimaryColourRules() {
        assertEquals(PaintPuzzle.Mix.ORANGE, PaintPuzzle.mix(EnumSet.of(PaintPuzzle.Paint.RED, PaintPuzzle.Paint.YELLOW)));
        assertEquals(PaintPuzzle.Mix.GREEN, PaintPuzzle.mix(EnumSet.of(PaintPuzzle.Paint.YELLOW, PaintPuzzle.Paint.BLUE)));
        assertEquals(PaintPuzzle.Mix.PURPLE, PaintPuzzle.mix(EnumSet.of(PaintPuzzle.Paint.RED, PaintPuzzle.Paint.BLUE)));
        assertEquals(PaintPuzzle.Mix.SLUDGE, PaintPuzzle.mix(EnumSet.allOf(PaintPuzzle.Paint.class)));
        assertEquals(PaintPuzzle.Mix.NONE, PaintPuzzle.mix(EnumSet.noneOf(PaintPuzzle.Paint.class)));
    }

    @Test
    void paintPuzzleRecoversFromMistakes() {
        PaintPuzzle p = new PaintPuzzle(List.of(PaintPuzzle.Mix.ORANGE, PaintPuzzle.Mix.GREEN, PaintPuzzle.Mix.PURPLE));
        assertEquals(PaintPuzzle.Result.EMPTY, p.release());
        p.toggle(PaintPuzzle.Paint.BLUE);
        p.toggle(PaintPuzzle.Paint.YELLOW);
        assertEquals(PaintPuzzle.Result.WRONG, p.release(), "green was poured but orange was needed");
        assertEquals(1, p.mistakes());
        assertEquals(0, p.solved());
        p.toggle(PaintPuzzle.Paint.RED);
        p.toggle(PaintPuzzle.Paint.YELLOW);
        assertEquals(PaintPuzzle.Result.CORRECT, p.release());
        p.toggle(PaintPuzzle.Paint.YELLOW);
        p.toggle(PaintPuzzle.Paint.BLUE);
        assertEquals(PaintPuzzle.Result.CORRECT, p.release());
        p.toggle(PaintPuzzle.Paint.RED);
        p.toggle(PaintPuzzle.Paint.BLUE);
        assertEquals(PaintPuzzle.Result.CORRECT, p.release());
        assertTrue(p.done());
        assertEquals(PaintPuzzle.Result.ALREADY_DONE, p.release());
    }

    // ---------------------------------------------------------------- tram network

    @Test
    void depotPuzzleHasExactlyOneSolutionAndFailuresAreDeadEnds() {
        TramNetwork t = TramNetwork.depot();
        assertEquals(4, t.switchCount());
        List<boolean[]> sols = t.solutions();
        assertEquals(1, sols.size());
        assertTrue(java.util.Arrays.equals(TramNetwork.depotSolution(), sols.get(0)));
        // every wrong arrangement ends in a buffer stop (recoverable), never loops or crashes
        for (int mask = 0; mask < 16; mask++) {
            boolean[] s = new boolean[4];
            for (int i = 0; i < 4; i++) {
                s[i] = (mask & (1 << i)) != 0;
            }
            TramNetwork.Trace tr = t.trace(s);
            assertTrue(tr.nodes().size() <= 6);
            assertEquals(tr.reachedGoal(), java.util.Arrays.equals(s, TramNetwork.depotSolution()));
        }
    }

    // ---------------------------------------------------------------- zones & platforms

    @Test
    void floodedZonesNeverCoverTheWholeArenaAndNeverRepeat() {
        ZonePlanner z = new ZonePlanner(42);
        List<ZonePlanner.Zone> prev = List.of();
        for (int i = 0; i < 200; i++) {
            int count = 1 + (i % 2);
            List<ZonePlanner.Zone> pick = z.next(count);
            assertEquals(count, pick.size());
            assertEquals(count, new HashSet<>(pick).size(), "no duplicates");
            assertTrue(pick.size() <= 2);
            assertFalse(pick.size() == prev.size() && pick.containsAll(prev) && !prev.isEmpty(), "same selection twice in a row");
            prev = pick;
        }
        // each zone has colour AND symbol
        for (ZonePlanner.Zone zone : ZonePlanner.Zone.values()) {
            assertFalse(zone.color.isEmpty());
            assertFalse(zone.symbol.isEmpty());
        }
        Set<String> symbols = new HashSet<>();
        for (ZonePlanner.Zone zone : ZonePlanner.Zone.values()) {
            assertTrue(symbols.add(zone.symbol), "symbols must be unique");
        }
    }

    @Test
    void platformDecayNeverDestroysEverySafePlatform() {
        int n = 14;
        boolean[] anchors = new boolean[n];
        anchors[0] = true;
        anchors[1] = true;
        PlatformDecay d = new PlatformDecay(n, anchors, 6);
        Random rng = new Random(7);
        int safety = 5000;
        while (safety-- > 0) {
            int moved = d.advance(rng);
            assertTrue(d.standing() >= 6, "standing platforms must stay >= minimum");
            assertEquals(PlatformDecay.COLOURED, d.stage(0), "anchors never decay");
            assertEquals(PlatformDecay.COLOURED, d.stage(1));
            if (moved < 0) {
                break;
            }
        }
        assertTrue(safety > 0, "decay reaches a fixed point");
        assertTrue(d.standing() >= 6);
        int regrown = d.regrowOne();
        assertTrue(regrown >= 0);
        assertEquals(PlatformDecay.GREY, d.stage(regrown));
    }

    @Test
    void questStepsHaveCompassTargetsOnlyInTheSecondAct() {
        List<String> targets = new ArrayList<>();
        for (QuestStep s : QuestStep.values()) {
            if (s.compassMarker != null) {
                assertTrue(s.stage.id >= QuestStep.RG_TRAVEL.stage.id, s + " has a compass target before Act 2");
                assertTrue(s.compassMarker.contains(":"), "marker ids are structure:name");
                targets.add(s.compassMarker);
            }
        }
        assertFalse(targets.isEmpty());
    }
}
