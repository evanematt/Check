package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.TowerFlow;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerFlowTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void theTicketMachineCountsUpPerPlayerAndTalksOnlyTheFirstTime() {
        FakeEnv env = new FakeEnv("tower", 2);
        TowerFlow f = new TowerFlow(env);
        f.use("ticket_machine", A);
        f.use("ticket_machine", A);
        f.use("ticket_machine", B);
        assertEquals(2, env.dialogues.stream().filter("tower_machine"::equals).count(), "one speech per person");
        assertEquals(2, env.world.counter("tower.tickets"));
        assertEquals(3, env.messages.stream().filter("message.lewandivka.tower.ticket"::equals).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void theArchiveLeversWorkForEveryPartySize(int party) {
        FakeEnv env = new FakeEnv("tower", party);
        TowerFlow f = new TowerFlow(env);
        f.use("lever_1", A);
        f.use("lever_2", A);
        assertFalse(env.gateOpen("gate_f1"));
        env.advance(10);
        f.use("lever_3", A);
        assertTrue(env.gateOpen("gate_f1"));
        assertEquals("minecraft:redstone_lamp[lit=true]", env.blocks.get("lamp_f1_2"));
        assertEquals(1, env.eventCount(Events.TOWER_FLOOR));
        f.use("lever_1", A);                              // pulling again changes nothing
        assertEquals(1, env.eventCount(Events.TOWER_FLOOR));
    }

    @Test
    void office404NeedsTheCatsFirst() {
        FakeEnv env = new FakeEnv("tower", 1);
        env.world.defeatBoss("collar_collector");
        TowerFlow f = new TowerFlow(env);
        f.use("lever_404", A);
        assertFalse(env.gateOpen("gate_f2"));
        env.playersAtMarker.put("front_404", List.of(A));
        for (int i = 0; i < 700; i++) {
            env.advance(1);
            f.tick();
        }
        assertTrue(env.record.flag("hint.f404"));
        f.use("lever_404", A);
        assertTrue(env.gateOpen("gate_f2"));
        assertEquals(1, env.eventCount(Events.TOWER_FLOOR));
    }

    @Test
    void theColourDepartmentMixesOrangeGreenAndPurple() {
        FakeEnv env = new FakeEnv("tower", 3);
        TowerFlow f = new TowerFlow(env);
        for (String[] mix : new String[][] {{"red", "yellow"}, {"yellow", "blue"}, {"red", "blue"}}) {
            for (String c : mix) {
                f.use("tap_f3_" + c, A);
            }
            f.use("tap_f3_release", A);
        }
        assertTrue(env.gateOpen("grate_f3_1") && env.gateOpen("grate_f3_2") && env.gateOpen("grate_f3_3"));
        assertEquals(1, env.eventCount(Events.TOWER_FLOOR));
        assertTrue(env.messages.contains("message.lewandivka.tower.paint"));
    }

    @Test
    void aWrongMixOnlyMakesSludge() {
        FakeEnv env = new FakeEnv("tower", 1);
        TowerFlow f = new TowerFlow(env);
        f.use("tap_f3_red", A);
        f.use("tap_f3_blue", A);
        f.use("tap_f3_release", A);                        // purple is the third grate, not the first
        assertFalse(env.gateOpen("grate_f3_1"));
        assertTrue(env.messages.contains("message.lewandivka.rg.sludge"));
    }

    @Test
    void theClimbCountsFiveFloorsAndOpensTheArena() {
        FakeEnv env = new FakeEnv("tower", 2);
        env.world.defeatBoss("collar_collector");
        TowerFlow f = new TowerFlow(env);
        f.use("lever_1", A);
        f.use("lever_2", A);
        f.use("lever_3", B);
        env.record.setFlag("hint.f404");
        f.use("lever_404", A);
        env.record.setFlag("floor.3");
        env.playersAtMarker.put("f4_ledge", List.of(A));
        f.tick();
        env.playersAtMarker.clear();
        env.playersAtMarker.put("f5_end", List.of(A));
        f.tick();
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(f.floorsDone() >= 4);
        env.playersAtMarker.clear();
        env.playersAtMarker.put("arena_entry", List.of(A, B));
        f.tick();
        f.tick();
        assertEquals(List.of("colorless_head"), env.bosses);
        f.use("relay_b", A);
        assertEquals("colorless_head:relay:1", env.stationCalls.get(env.stationCalls.size() - 1));
        f.bossDefeated("colorless_head");
        assertTrue(f.complete());
    }

    @Test
    void rebuildRestoresFloorsAndTheBoss() {
        FakeEnv env = new FakeEnv("tower", 1);
        env.record.setFlag("floor.1");
        env.record.setFlag("floor.2");
        env.record.setFlag("floor.5");
        env.record.setFlag("arena");
        env.record.setFlag("grate.1");
        new TowerFlow(env).rebuild();
        assertTrue(env.gateOpen("gate_f1") && env.gateOpen("gate_f2") && env.gateOpen("boss_door") && env.gateOpen("grate_f3_1"));
        assertFalse(env.gateOpen("grate_f3_2"));
        assertTrue(env.bosses.contains("colorless_head"));
    }
}
