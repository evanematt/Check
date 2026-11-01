package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.AquaparkFlow;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AquaparkFlowTest {

    private static final UUID A = UUID.randomUUID();

    private static void enter(FakeEnv env, AquaparkFlow f) {
        env.playersAtMarker.put("lobby", List.of(A));
        f.tick();
    }

    @Test
    void pumpsStayOffUntilThePartyHasBeenInside() {
        FakeEnv env = new FakeEnv("aquapark", 1);
        AquaparkFlow f = new AquaparkFlow(env);
        f.use("pump_1", A);
        assertEquals(0, f.pumpsOn());
        enter(env, f);
        assertEquals(1, env.eventCount(Events.AQ_ENTERED));
        f.use("pump_1", A);
        assertEquals(1, f.pumpsOn());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void threePumpsFillThePoolsAndOpenTheArenaForEveryPartySize(int party) {
        FakeEnv env = new FakeEnv("aquapark", party);
        AquaparkFlow f = new AquaparkFlow(env);
        enter(env, f);
        for (int i = 1; i <= 3; i++) {
            assertFalse(env.gateOpen("boss_door"), "closed before pump " + i);
            f.use("pump_" + i, A);
            assertEquals("true", env.station("pump_" + i, "on"));
            assertEquals(Boolean.TRUE, env.floods.get("fill_" + i));
        }
        assertEquals(3, env.eventCount(Events.AQ_PUMP));
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.bosses.contains("lady_vortex"));
        assertEquals(2, env.checkpoint);
        f.use("pump_2", A);                              // pressing a running pump again changes nothing
        assertEquals(3, env.eventCount(Events.AQ_PUMP));
    }

    @Test
    void aWaterCoreIsOnlySpentWhenTheBossAcceptsIt() {
        FakeEnv env = new FakeEnv("aquapark", 3);
        AquaparkFlow f = new AquaparkFlow(env);
        f.useWith("drain_1", A, QuestItems.WATER_CORE);
        assertTrue(env.stationCalls.isEmpty(), "no core in the pocket: nothing happens");
        env.inventory.put(QuestItems.WATER_CORE, 1);
        env.bossAccepts = false;
        f.useWith("drain_1", A, QuestItems.WATER_CORE);
        assertEquals(1, env.given.get(QuestItems.WATER_CORE), "refunded");
        env.inventory.put(QuestItems.WATER_CORE, 1);
        env.given.clear();
        env.bossAccepts = true;
        f.useWith("drain_3", A, QuestItems.WATER_CORE);
        assertEquals(0, env.inventory.get(QuestItems.WATER_CORE));
        assertTrue(env.given.isEmpty());
        assertEquals("lady_vortex:drain:2", env.stationCalls.get(env.stationCalls.size() - 1));
    }

    @Test
    void rebuildRestoresPumpsAndTheOpenArena() {
        FakeEnv env = new FakeEnv("aquapark", 2);
        env.record.setFlag("entered");
        env.record.setFlag("pump.1");
        env.record.setFlag("pump.2");
        env.record.setFlag("pump.3");
        env.record.setFlag("arena.open");
        AquaparkFlow f = new AquaparkFlow(env);
        f.rebuild();
        assertEquals("true", env.station("pump_3", "on"));
        assertEquals(Boolean.TRUE, env.floods.get("fill_2"));
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.bosses.contains("lady_vortex"));
    }

    @Test
    void resetKeepsThePumpsAndRestartsTheBoss() {
        FakeEnv env = new FakeEnv("aquapark", 3);
        AquaparkFlow f = new AquaparkFlow(env);
        enter(env, f);
        for (int i = 1; i <= 3; i++) {
            f.use("pump_" + i, A);
        }
        env.bosses.clear();
        f.reset();
        assertEquals(3, f.pumpsOn());
        assertTrue(env.bosses.contains("lady_vortex"), "boss is back for another attempt");
        f.bossDefeated("lady_vortex");
        assertTrue(f.complete());
    }
}
