package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.SkyDepotFlow;
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

class SkyDepotFlowTest {

    private static final UUID A = UUID.randomUUID();

    private static void solve(FakeEnv env, SkyDepotFlow f) {
        f.use("switch_1", A);
        f.use("switch_3", A);
        f.use("dispatch", A);
        env.advance(150);
        f.tick();
    }

    @Test
    void aWrongArrangementRollsTheCarBackWithoutHarm() {
        FakeEnv env = new FakeEnv("sky_depot", 3);
        SkyDepotFlow f = new SkyDepotFlow(env);
        f.use("switch_1", A);                           // B A A A: the third switch sends the car to a buffer stop
        f.use("dispatch", A);
        List<String> route = env.tramRoutes.get("depot.car");
        assertEquals("net_start", route.get(0));
        assertEquals("net_d2", route.get(route.size() - 1));
        assertTrue(env.tramBusy("depot.car"));
        f.use("dispatch", A);                           // pulling again while it moves does nothing
        env.advance(150);
        f.tick();
        assertTrue(env.messages.contains("message.lewandivka.switch.wrong"));
        assertEquals("depot.car->null", env.tramCleared.get(0));
        assertFalse(f.switchesSolved());
        assertEquals(0, env.eventCount(Events.DEPOT_SWITCHES_SOLVED));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void theClueArrangementDeliversTheCarForAnyPartySize(int party) {
        FakeEnv env = new FakeEnv("sky_depot", party);
        SkyDepotFlow f = new SkyDepotFlow(env);
        solve(env, f);
        List<String> route = env.tramRoutes.get("depot.car");
        assertEquals("net_goal", route.get(route.size() - 1));
        assertTrue(f.switchesSolved());
        assertEquals(1, env.eventCount(Events.DEPOT_SWITCHES_SOLVED));
        assertEquals("b", env.station("switch_1", "state"));
        assertEquals("b", env.station("switch_3", "state"));
        assertEquals(null, env.station("switch_2", "state"));
        assertEquals("minecraft:redstone_lamp[lit=true]", env.blocks.get("switch_lamp_3"));
    }

    @Test
    void theTicketWindowIsDeadUntilTheTramRoutesAreRight() {
        FakeEnv env = new FakeEnv("sky_depot", 1);
        SkyDepotFlow f = new SkyDepotFlow(env);
        f.use("ticket_window", A);
        assertTrue(env.given.isEmpty());
        assertEquals(0, env.eventCount(Events.DEPOT_TICKET));
    }

    @Test
    void threeDifferentTicketsOpenTheArenaAndTheBoss() {
        FakeEnv env = new FakeEnv("sky_depot", 2);
        SkyDepotFlow f = new SkyDepotFlow(env);
        solve(env, f);
        for (String ticket : SkyDepotFlow.TICKETS) {
            f.use("ticket_window", A);
            assertEquals(1, env.given.get(ticket), "next missing symbol: " + ticket);
            env.inventory.put(ticket, 1);
        }
        assertEquals(3, env.eventCount(Events.DEPOT_TICKET));
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.bosses.contains("conductor"));
        assertEquals(3, env.checkpoint);
        f.use("ticket_window", A);                       // nothing more to hand out
        assertTrue(env.messages.contains("message.lewandivka.ticket.have_all"));
        assertEquals(3, env.eventCount(Events.DEPOT_TICKET));
    }

    @Test
    void aCompostersStandNeedsTheComposterAndItsOwnSymbol() {
        FakeEnv env = new FakeEnv("sky_depot", 3);
        SkyDepotFlow f = new SkyDepotFlow(env);
        f.useWith("composter_1", A, QuestItems.TICKET_X);
        assertTrue(env.stationCalls.isEmpty(), "no composter in the pocket");
        env.inventory.put(QuestItems.COMPOSTER, 1);
        f.useWith("composter_2", A, QuestItems.TICKET_X);
        assertTrue(env.stationCalls.isEmpty(), "wrong symbol");
        f.useWith("composter_2", A, QuestItems.TICKET_CIRCLE);
        f.useWith("composter_3", A, QuestItems.TICKET_TRIANGLE);
        f.use("validator_c", A);
        assertEquals(List.of("conductor:composter:1", "conductor:composter:2", "conductor:validator:2"), env.stationCalls);
    }

    @Test
    void rebuildRestoresTheSolvedSwitchesAndTheOpenArena() {
        FakeEnv env = new FakeEnv("sky_depot", 1);
        env.record.setFlag("switches");
        env.record.setFlag("arena.open");
        SkyDepotFlow f = new SkyDepotFlow(env);
        f.rebuild();
        assertEquals("b", env.station("switch_1", "state"));
        assertEquals("a", env.station("switch_2", "state"));
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.bosses.contains("conductor"));
        f.bossDefeated("conductor");
        assertTrue(f.complete());
    }
}
