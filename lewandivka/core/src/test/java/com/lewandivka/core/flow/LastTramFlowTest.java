package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.dungeon.LastTramFlow;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastTramFlowTest {

    private static final UUID A = new UUID(0, 1);

    private static FakeEnv env(int party) {
        FakeEnv e = new FakeEnv("tram_stop", party);
        e.world.forceStep(QuestStep.TRAM_WAIT);
        for (int i = 0; i < party; i++) {
            e.inside.add(new UUID(0, i + 1));
        }
        return e;
    }

    private static void run(FakeEnv env, LastTramFlow flow, int ticks) {
        for (int i = 0; i < ticks; i++) {
            env.advance(1);
            flow.tick();
        }
    }

    private static void killAll(FakeEnv env, String tag) {
        while (env.kill(tag)) {
            // one by one
        }
    }

    @Test
    void nothingStartsBeforeTheStoryReachesTheTram() {
        FakeEnv env = new FakeEnv("tram_stop", 3);
        env.world.forceStep(QuestStep.GARAGE_ESCAPE);
        env.inside.add(A);
        LastTramFlow f = new LastTramFlow(env);
        run(env, f, 500);
        assertEquals(0, env.eventCount(Events.TRAM_STARTED));
        assertFalse(env.forcedNight);
        env.world.forceStep(QuestStep.TRAM_WAIT);
        run(env, f, 5);
        assertEquals(1, env.eventCount(Events.TRAM_STARTED));
        assertTrue(env.forcedNight, "cinematic night");
    }

    @ParameterizedTest
    @CsvSource({"1,3,5,2", "2,5,7,3", "3,6,9,4"})
    void waveSizesScaleWithTheParty(int party, int w1, int w2, int w3) {
        FakeEnv env = env(party);
        LastTramFlow f = new LastTramFlow(env);
        run(env, f, 400);
        assertEquals(w1, env.alive(LastTramFlow.W1));
        killAll(env, LastTramFlow.W1);
        run(env, f, 400);
        assertEquals(w2, env.alive(LastTramFlow.W2));
        killAll(env, LastTramFlow.W2);
        run(env, f, 400);
        assertEquals(w3, env.alive(LastTramFlow.W3));
        assertTrue(env.bosses.contains("fare_dodger_leader"));
        assertEquals(LastTramFlow.SHIELDED, env.resistance.get("fare_dodger_leader"));
        assertEquals(2, env.eventCount(Events.TRAM_WAVE));
    }

    @Test
    void anOverrunTramRestartsOnlyThatWave() {
        FakeEnv env = env(3);
        LastTramFlow f = new LastTramFlow(env);
        run(env, f, 400);
        killAll(env, LastTramFlow.W1);
        run(env, f, 400);
        assertEquals("WAVE2", f.phaseName());
        env.nearCounts.put(LastTramFlow.W2, 9);          // nine enemies hang on the tram
        run(env, f, 20 * 4);
        assertTrue(f.integrity() < LastTramFlow.TRAM_INTEGRITY);
        run(env, f, 20 * 4);
        assertTrue(env.messages.contains("message.lewandivka.encounter_reset"), "the wave starts over");
        assertTrue(env.record.flag("wave.1"), "wave 1 stays finished");
        assertFalse(env.record.flag("wave.2"));
        env.nearCounts.put(LastTramFlow.W2, 0);
        run(env, f, 300);
        assertEquals("WAVE2", f.phaseName());
        assertEquals(9, env.alive(LastTramFlow.W2));
    }

    private static void bringToLeader(FakeEnv env, LastTramFlow f) {
        run(env, f, 400);
        killAll(env, LastTramFlow.W1);
        run(env, f, 400);
        killAll(env, LastTramFlow.W2);
        run(env, f, 400);
        assertEquals("WAVE3", f.phaseName());
    }

    @Test
    void validatorsNeedATokenAndTheWindowScalesWithTheParty() {
        // three players: a short window, so a slow walk between the validators fails
        FakeEnv trio = env(3);
        LastTramFlow ft = new LastTramFlow(trio);
        bringToLeader(trio, ft);
        ft.use("validator_1", A);
        assertTrue(trio.messages.contains("message.lewandivka.ticket.invalid"), "a bare hand is not a ticket");
        ft.useWith("validator_1", A, QuestItems.TOKEN);
        trio.advance(200);
        ft.useWith("validator_2", A, QuestItems.TOKEN);
        trio.advance(200);
        ft.useWith("validator_3", A, QuestItems.TOKEN);
        assertEquals(LastTramFlow.SHIELDED, trio.resistance.get("fare_dodger_leader"), "the first press went dark already");
        trio.advance(10);
        ft.useWith("validator_1", A, QuestItems.TOKEN);
        ft.useWith("validator_2", A, QuestItems.TOKEN);
        ft.useWith("validator_3", A, QuestItems.TOKEN);
        assertEquals(1.0, trio.resistance.get("fare_dodger_leader"), "all three together drop the shield");

        // one player: the window is long enough to walk from validator to validator
        FakeEnv solo = env(1);
        LastTramFlow fs = new LastTramFlow(solo);
        bringToLeader(solo, fs);
        fs.useWith("validator_1", A, QuestItems.TOKEN);
        solo.advance(300);
        fs.useWith("validator_2", A, QuestItems.TOKEN);
        solo.advance(300);
        fs.useWith("validator_3", A, QuestItems.TOKEN);
        assertEquals(1.0, solo.resistance.get("fare_dodger_leader"));
        // the shield comes back after a while and the validators go dark
        run(solo, fs, 600);
        assertEquals(LastTramFlow.SHIELDED, solo.resistance.get("fare_dodger_leader"));
        assertEquals("false", solo.station("validator_1", "active"));
    }

    @Test
    void defeatingTheLeaderFinishesTheEventAndRestoresTheDay() {
        FakeEnv env = env(2);
        LastTramFlow f = new LastTramFlow(env);
        bringToLeader(env, f);
        f.bossDefeated("fare_dodger_leader");
        assertTrue(f.complete());
        assertEquals(3, env.eventCount(Events.TRAM_WAVE));
        assertFalse(env.forcedNight);
        assertEquals(0, env.alive(LastTramFlow.W3));
    }

    @Test
    void aRestartContinuesAfterTheLastFinishedWave() {
        FakeEnv env = env(3);
        LastTramFlow f = new LastTramFlow(env);
        run(env, f, 400);
        killAll(env, LastTramFlow.W1);
        run(env, f, 150);
        assertTrue(env.record.flag("wave.1"));
        LastTramFlow reloaded = new LastTramFlow(env);
        reloaded.rebuild();
        assertTrue(env.forcedNight);
        run(env, reloaded, 100);
        assertEquals("WAVE2", reloaded.phaseName());
    }

    @Test
    void aWipeResetsTheRunningWaveCleanly() {
        FakeEnv env = env(3);
        LastTramFlow f = new LastTramFlow(env);
        bringToLeader(env, f);
        f.reset();
        assertEquals(0, env.alive(LastTramFlow.W3));
        assertFalse(env.bosses.contains("fare_dodger_leader"));
        assertTrue(env.record.flag("wave.2"));
        run(env, f, 300);
        assertEquals("WAVE3", f.phaseName());
        assertTrue(env.bosses.contains("fare_dodger_leader"));
    }
}
