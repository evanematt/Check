package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.SkyAscentFlow;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyAscentFlowTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static void tick(FakeEnv env, SkyAscentFlow f, int times, int step) {
        for (int i = 0; i < times; i++) {
            env.advance(step);
            f.tick();
        }
    }

    @Test
    void reachingTheStopSavesProgressAndCallsTheTramOnce() {
        FakeEnv env = new FakeEnv("sky_ascent", 1);
        env.inside.add(A);
        SkyAscentFlow f = new SkyAscentFlow(env);
        env.playersAtMarker.put("pad_3", List.of(A));
        f.tick();
        assertEquals(1, env.checkpoint);
        env.playersAtMarker.put("stop_platform", List.of(A));
        f.tick();
        assertEquals(2, env.checkpoint);
        assertEquals(1, env.eventCount(Events.SKY_STOP_REACHED));
        assertEquals(List.of("chromandivka:sky_ride_0", "chromandivka:sky_ride_1"), env.tramRoutes.get("sky.tram"));
        tick(env, f, 3, 50);
        assertEquals(1, env.eventCount(Events.SKY_STOP_REACHED));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void theRideCarriesEverybodyOnThePlatformAndEndsAtTheDepot(int party) {
        FakeEnv env = new FakeEnv("sky_ascent", party);
        env.inside.add(A);
        if (party > 1) {
            env.inside.add(B);
        }
        env.tramTicks = 2400;
        SkyAscentFlow f = new SkyAscentFlow(env);
        env.playersAtMarker.put("stop_platform", party > 1 ? List.of(A, B) : List.of(A));
        f.tick();                                       // tram is called
        env.tramTicks = 2400;
        env.advance(2400);
        f.tick();                                       // arrived: boarding
        f.tick();                                       // everybody is on the platform: leave
        assertTrue(f.riding());
        assertEquals(party > 1 ? List.of(A, B) : List.of(A), env.tramRiders.get("sky.tram"));
        assertEquals(10 - 1, env.tramRoutes.get("sky.tram").size());
        assertEquals(SkyAscentFlow.SPEED, env.tramSpeed);
        env.advance(950);
        f.tick();
        assertFalse(env.tramMobs.isEmpty(), "one short attack on the way");
        assertTrue(env.messages.contains("message.lewandivka.sky.attack"));
        env.advance(2400);
        f.tick();
        assertEquals(1, env.eventCount(Events.SKY_ARRIVED));
        assertEquals("sky.tram->sky_depot:tram_arrive", env.tramCleared.get(env.tramCleared.size() - 1));
        assertTrue(f.complete());
        assertFalse(f.riding());
    }

    @Test
    void theTramWaitsForStragglersButNotForever() {
        FakeEnv env = new FakeEnv("sky_ascent", 2);
        env.inside.add(A);
        env.inside.add(B);
        SkyAscentFlow f = new SkyAscentFlow(env);
        env.playersAtMarker.put("stop_platform", List.of(A));
        f.tick();
        env.advance(200);
        f.tick();                                       // boarding window starts
        f.tick();
        assertFalse(f.riding(), "B is still on the way");
        env.advance(SkyAscentFlow.BOARDING_TICKS + 1);
        f.tick();
        assertTrue(f.riding());
        assertEquals(List.of(A), env.tramRiders.get("sky.tram"));
    }

    @Test
    void anEmptyPlatformSendsTheTramAway() {
        FakeEnv env = new FakeEnv("sky_ascent", 1);
        env.inside.add(A);
        SkyAscentFlow f = new SkyAscentFlow(env);
        env.playersAtMarker.put("stop_platform", List.of(A));
        f.tick();
        env.advance(200);
        f.tick();
        env.playersAtMarker.put("stop_platform", List.of());
        f.tick();
        assertEquals("sky.tram->null", env.tramCleared.get(env.tramCleared.size() - 1));
        env.playersAtMarker.put("stop_platform", List.of(A));
        f.tick();
        assertTrue(env.tramBusy("sky.tram"), "somebody came back: the tram is called again");
    }
}
