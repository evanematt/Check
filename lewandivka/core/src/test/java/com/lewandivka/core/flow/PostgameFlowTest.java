package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.dungeon.Garage0Flow;
import com.lewandivka.core.flow.dungeon.PostgameFlow;
import com.lewandivka.core.quest.QuestItems;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgameFlowTest {

    private static final UUID A = UUID.randomUUID();

    private static FakeEnv free(int party) {
        FakeEnv env = new FakeEnv("district", party);
        env.world.forceStep(QuestStep.POST_FREE);
        env.inside.add(A);
        return env;
    }

    @Test
    void nothingWorksBeforeTheCredits() {
        FakeEnv env = new FakeEnv("district", 1);
        PostgameFlow f = new PostgameFlow(env);
        assertFalse(f.use("cat_1", A));
        f.tick();
        assertTrue(env.spawned.isEmpty());
    }

    @Test
    void twelveCatsSitAtTheirSpotsAndTheLastOneGivesTheSquareTicket() {
        FakeEnv env = free(2);
        PostgameFlow f = new PostgameFlow(env);
        f.tick();
        for (int i = 1; i <= 12; i++) {
            assertEquals(1, env.alive("post.cat." + i), "cat " + i);
        }
        for (int i = 1; i <= 12; i++) {
            assertTrue(f.use("cat_" + i, A));
        }
        assertEquals(12, f.catsFound());
        assertEquals(1, env.given.get(QuestItems.TICKET_SQUARE));
        assertTrue(env.awards.contains("cats12"));
        env.advance(500);
        f.tick();
        assertEquals(0, env.alive("post.cat.1"), "found cats do not come back");
        f.use("cat_5", A);                                   // a second pat changes nothing
        assertEquals(1, env.given.get(QuestItems.TICKET_SQUARE));
    }

    @Test
    void theSeedsVanishAtNightAndThreeWitnessedBowlsSolveTheMystery() {
        FakeEnv env = free(1);
        PostgameFlow f = new PostgameFlow(env);
        env.inventory.put(QuestItems.SEEDS, 20);
        f.useWith("bowl_1", A, "");
        assertEquals(20, env.inventory.get(QuestItems.SEEDS), "no seeds in the hand: nothing is taken");
        for (int n = 1; n <= 3; n++) {
            f.useWith("bowl_" + n, A, QuestItems.SEEDS);
            assertEquals("1", env.station("seed_bowl_" + n, "stage"));
        }
        assertEquals(14, env.inventory.get(QuestItems.SEEDS));
        for (int n = 1; n <= 3; n++) {
            env.playersAtMarker.put("seed_bowl_" + n, List.of(A));
        }
        for (int t = 0; t < 1400; t++) {
            env.advance(1);
            f.tick();
        }
        assertEquals(3, f.bowlsWitnessed());
        assertEquals("2", env.station("seed_bowl_2", "stage"));
        assertEquals(2, env.given.get(QuestItems.TOKEN));
        assertEquals(0, env.alive("post.thief.1"));
    }

    @Test
    void aBowlIsNotEmptiedInTheDaytime() {
        FakeEnv env = free(1);
        env.night = false;
        PostgameFlow f = new PostgameFlow(env);
        env.inventory.put(QuestItems.SEEDS, 4);
        f.useWith("bowl_1", A, QuestItems.SEEDS);
        for (int t = 0; t < 2000; t++) {
            env.advance(1);
            f.tick();
        }
        assertEquals("1", env.station("seed_bowl_1", "stage"));
        assertEquals(0, f.bowlsWitnessed());
    }

    @Test
    void theWrongTramNeedsTheSquareTicketAndEndsInGarageZero() {
        FakeEnv env = free(3);
        PostgameFlow f = new PostgameFlow(env);
        f.useWith("validator", A, QuestItems.TICKET_SQUARE);
        assertTrue(env.tramRoutes.isEmpty(), "the cats come first");
        env.record.setFlag("cats.done");
        f.useWith("validator", A, QuestItems.TICKET_SQUARE);
        assertTrue(env.tramRoutes.isEmpty(), "no ticket in the pocket");
        env.inventory.put(QuestItems.TICKET_SQUARE, 1);
        env.playersAtMarker.put("wrong_tram_stop", List.of(A));
        f.useWith("validator", A, QuestItems.TICKET_SQUARE);
        assertEquals(List.of("district:wrong_tram_stop", "district:tram_fog_start"), env.tramRoutes.get("post.tram"));
        assertEquals(List.of(A), env.tramRiders.get("post.tram"));
        env.advance(200);
        f.tick();
        assertEquals("post.tram->garage0:entry", env.tramCleared.get(0));
        assertTrue(env.awards.contains("wrong_tram"));
        assertTrue(env.record.flag("tram.done"));
    }

    @Test
    void mrShlahbaumGivesTheNextHintAndFinallyTheSock() {
        FakeEnv env = free(1);
        PostgameFlow f = new PostgameFlow(env);
        f.use("shlahbaum", A);
        env.record.setFlag("cats.done");
        f.use("shlahbaum", A);
        env.record.setFlag("seeds.done");
        f.use("shlahbaum", A);
        env.record.setFlag("tram.done");
        f.use("shlahbaum", A);
        assertEquals(List.of("post_cats12", "post_seeds", "post_tram", "post_garage0"), env.dialogues);
        f.use("shlahbaum", A);
        assertNotNull(env.asks.get("post_package"));
        env.asks.get("post_package").accept(A, "no");
        assertFalse(env.record.flag("package.revealed"));
        env.asks.get("post_package").accept(A, "yes");
        assertTrue(env.record.flag("package.revealed"));
        assertEquals(1, env.given.get(QuestItems.SOCK));
        assertTrue(env.awards.contains("better_not_ask"));
        assertTrue(f.complete());
    }

    @Test
    void garageZeroSpawnsThreeWavesAndThenShowsTheReward() {
        FakeEnv env = new FakeEnv("garage0", 3);
        env.inside.add(A);
        Garage0Flow f = new Garage0Flow(env);
        f.playerEntered(A);
        assertEquals(0, env.checkpoint);
        f.tick();
        assertEquals(0, f.wave(), "nothing happens until somebody stands in the hall");
        env.playersAtMarker.put("arena", List.of(A));
        f.tick();
        assertEquals(1, f.wave());
        assertTrue(env.alive("g0.enemy") >= 1);
        for (int wave = 1; wave <= 3; wave++) {
            while (env.kill("g0.enemy")) {
                // the party wins the wave
            }
            for (int t = 0; t < 130 && !f.complete(); t++) {
                env.advance(1);
                f.tick();
            }
        }
        assertTrue(f.complete());
        assertEquals("lewandivka:supply_stash", env.blocks.get("reward"));
        assertTrue(env.awards.contains("garage0"));
    }

    @Test
    void garageZeroResetsWithoutLosingAFinishedReward() {
        FakeEnv env = new FakeEnv("garage0", 1);
        env.record.setFlag("done");
        Garage0Flow f = new Garage0Flow(env);
        f.rebuild();
        assertEquals("lewandivka:supply_stash", env.blocks.get("reward"));
        f.reset();
        assertTrue(f.complete());
    }
}
