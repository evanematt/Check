package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.dungeon.ShelterFlow;
import com.lewandivka.core.puzzle.CatRoute;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterFlowTest {

    private static final UUID A = new UUID(0, 1);

    private static void run(FakeEnv env, ShelterFlow f, int ticks) {
        for (int i = 0; i < ticks; i++) {
            env.advance(1);
            f.tick();
        }
    }

    private static void solveLevers(FakeEnv env, ShelterFlow f, int gap) {
        f.use("lever_1", A);
        env.advance(gap);
        f.use("lever_2", A);
        env.advance(gap);
        f.use("lever_3", A);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void theLeversNeedAWindowThatFitsThePartySize(int party) {
        FakeEnv env = new FakeEnv("shelter", party);
        ShelterFlow f = new ShelterFlow(env);
        int window = env.party.windowTicks(100);
        // pressing slower than the window never works
        solveLevers(env, f, window);
        assertFalse(env.gateOpen("gate_levers"));
        env.advance(window + 5);
        f.tick();
        // pressing at a walking pace inside the window works (a lone player gets the long window)
        solveLevers(env, f, window / 3);
        assertTrue(env.gateOpen("gate_levers"), "party " + party);
        assertEquals(1, env.eventCount(Events.SH_LEVERS_DONE));
        assertEquals("lewandivka:redstone_lamp", "lewandivka:redstone_lamp");
        assertEquals("minecraft:redstone_lamp[lit=true]", env.blocks.get("lamp_2"));
    }

    @Test
    void theHallEntranceReportsOnce() {
        FakeEnv env = new FakeEnv("shelter", 1);
        ShelterFlow f = new ShelterFlow(env);
        env.playersAtMarker.put("hall_entrance", List.of(A));
        f.tick();
        f.tick();
        assertEquals(1, env.eventCount(Events.SH_HALL_ENTERED));
    }

    private static ShelterFlow withCat(FakeEnv env) {
        ShelterFlow f = new ShelterFlow(env);
        solveLevers(env, f, 20);
        run(env, f, 400);
        assertTrue(env.record.flag("cat.active"), "the cat scene ends with Chinazik waiting at the start");
        assertEquals("cat_start", env.npcPlace.get("chinazik"));
        return f;
    }

    @Test
    void chinazikWalksTheRouteOnlyWhenFedInOrder() {
        FakeEnv env = new FakeEnv("shelter", 3);
        ShelterFlow f = withCat(env);
        env.inventory.put(QuestItems.FISH, 6);
        f.useWith("cat_food_2", A, QuestItems.FISH);
        assertEquals(6, env.inventory.get(QuestItems.FISH), "the second food point is not next");
        f.useWith("cat_food_1", A, "");
        assertTrue(env.messages.contains("message.lewandivka.cat.food"), "an empty hand is no food");
        f.useWith("cat_food_1", A, QuestItems.FISH);
        assertEquals("cat_food_1", env.npcPlace.get("chinazik"));
        run(env, f, 5);
        assertEquals(1, f.route().at());
        run(env, f, ShelterFlow.EAT_TICKS + 5);
        for (int p = 2; p <= 4; p++) {
            f.useWith("cat_food_" + p, A, QuestItems.FISH);
            run(env, f, 5);
            run(env, f, ShelterFlow.EAT_TICKS + 5);
        }
        // fully fed: he walks to the rug on his own
        assertEquals("cat_rug", env.npcPlace.get("chinazik"));
        run(env, f, 5);
        assertEquals(CatRoute.State.DONE, f.route().state());
        assertTrue(env.record.flag("chinazik.done"));
        assertTrue(env.gateOpen("gate_warehouse"));
        assertEquals("", env.npcNames.get("chinazik"), "his name is back");
        assertEquals("named", env.world.npcState("chinazik", "hidden"));
        assertEquals(1, env.eventCount(Events.SH_CHINAZIK_DONE));
        assertEquals(2, env.inventory.get(QuestItems.FISH), "four fish were used");
    }

    @Test
    void crowdingChinazikSendsHimBackToThePreviousSafePoint() {
        FakeEnv env = new FakeEnv("shelter", 2);
        ShelterFlow f = withCat(env);
        env.inventory.put(QuestItems.FISH, 4);
        f.useWith("cat_food_1", A, QuestItems.FISH);
        run(env, f, 5);
        run(env, f, ShelterFlow.EAT_TICKS + 5);
        f.useWith("cat_food_2", A, QuestItems.FISH);
        assertEquals(CatRoute.State.MOVING, f.route().state());
        env.npcCrowd.put("chinazik", 1);                       // somebody rushes him
        run(env, f, 2);
        assertEquals(CatRoute.State.WAITING, f.route().state());
        assertEquals("cat_food_1", env.npcPlace.get("chinazik"), "back to the previous safe point");
        assertTrue(env.messages.contains("message.lewandivka.cat.retreat"));
        env.npcCrowd.put("chinazik", 0);
        f.useWith("cat_food_2", A, QuestItems.FISH);
        run(env, f, 3);
        assertEquals(2, f.route().at());
    }

    @Test
    void metadonnaIsFoundByTheSmallBoxAndOnlyAfterChinazik() {
        FakeEnv env = new FakeEnv("shelter", 1);
        ShelterFlow f = withCat(env);
        f.use("box_small", A);
        assertFalse(env.record.flag("metadonna.found"), "the cats come in order");
        env.record.setFlag("chinazik.done");
        // clues while searching: purring gets louder when the player is close
        env.playersAtMarker.put("boxes", List.of(A));
        env.distances.put("box_small", 16.0);
        run(env, f, 60);
        env.distances.put("box_small", 2.0);
        run(env, f, 60);
        assertTrue(env.volumes.stream().anyMatch(v -> Float.parseFloat(v.substring(v.indexOf('@') + 1)) > 0.8f), "close to the box it purrs loudly: " + env.volumes);
        assertTrue(env.volumes.stream().anyMatch(v -> Float.parseFloat(v.substring(v.indexOf('@') + 1)) < 0.2f), "far away only quietly: " + env.volumes);
        f.use("box_other", A);
        assertFalse(env.record.flag("metadonna.found"));
        f.use("box_small", A);
        assertTrue(env.record.flag("metadonna.found"));
        assertEquals(1, env.eventCount(Events.SH_METADONNA_FOUND));
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.bosses.contains("collar_collector"));
        run(env, f, 100);
        assertTrue(env.dialogues.contains("cats_doors"));
        assertEquals("named", env.world.npcState("metadonna", "hidden"));
    }

    @Test
    void collarStandsGoToTheBossAndRebuildKeepsEverything() {
        FakeEnv env = new FakeEnv("shelter", 3);
        ShelterFlow f = new ShelterFlow(env);
        f.use("stand_3", A);
        f.use("stand_8", A);
        assertEquals(List.of("collar_collector:stand:2", "collar_collector:stand:7"), env.stationCalls);
        FakeEnv later = new FakeEnv("shelter", 3);
        later.record.setFlag("levers");
        later.record.setFlag("chinazik.done");
        later.record.setFlag("metadonna.found");
        new ShelterFlow(later).rebuild();
        assertTrue(later.gateOpen("gate_levers"));
        assertTrue(later.gateOpen("gate_warehouse"));
        assertTrue(later.gateOpen("boss_door"));
        assertEquals(List.of("collar_collector"), later.bosses);
        assertNotNull(later.station("box_small", "opened"));
    }
}
