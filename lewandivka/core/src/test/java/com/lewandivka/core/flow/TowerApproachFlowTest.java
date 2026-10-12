package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.TowerApproachFlow;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TowerApproachFlowTest {

    private static final UUID A = UUID.randomUUID();

    @Test
    void dashDoorsOpenOneByOneAndStayOpenAfterARestart() {
        FakeEnv env = new FakeEnv("tower_approach", 1);
        TowerApproachFlow f = new TowerApproachFlow(env);
        assertFalse(f.use("lever_1", A));
        f.use("dash_door_2", A);
        f.use("dash_door_2", A);
        assertTrue(env.gateOpen("dash_door_2"));
        assertFalse(env.gateOpen("dash_door_1"));
        assertEquals(1, f.doorsOpen());
        FakeEnv again = new FakeEnv("tower_approach", 1);
        again.record.setFlag("door.2");
        again.record.setFlag("door.3");
        new TowerApproachFlow(again).rebuild();
        assertTrue(again.gateOpen("dash_door_3"));
        assertFalse(again.gateOpen("dash_door_1"));
    }

    @Test
    void checkpointsAdvanceButNeverGoBack() {
        FakeEnv env = new FakeEnv("tower_approach", 2);
        TowerApproachFlow f = new TowerApproachFlow(env);
        f.playerEntered(A);
        assertEquals(0, env.checkpoint);
        env.playersAtMarker.put("cp_hidden", List.of(A));
        f.tick();
        assertEquals(2, env.checkpoint);
        env.playersAtMarker.clear();
        env.playersAtMarker.put("cp_dash", List.of(A));
        f.tick();
        assertEquals(2, env.checkpoint, "standing at an earlier lamp changes nothing");
        env.playersAtMarker.clear();
        env.playersAtMarker.put("cp_landing", List.of(A));
        f.tick();
        assertEquals(5, env.checkpoint);
    }

    @Test
    void theTowerGateFinishesTheApproachOnce() {
        FakeEnv env = new FakeEnv("tower_approach", 3);
        TowerApproachFlow f = new TowerApproachFlow(env);
        env.playersAtMarker.put("tower_approach_end", List.of(A));
        f.tick();
        f.tick();
        assertEquals(1, env.eventCount(Events.APPROACH_DONE));
        assertTrue(f.complete());
        assertEquals(6, env.checkpoint);
    }

    @Test
    void theCatsOpenTheNormalLookingWall() {
        FakeEnv env = new FakeEnv("tower_approach", 1);
        env.world.defeatBoss("collar_collector");
        TowerApproachFlow f = new TowerApproachFlow(env);
        env.playersAtMarker.put("hidden_front", List.of(A));
        for (int i = 0; i < 600; i++) {
            env.advance(1);
            f.tick();
        }
        assertTrue(env.gateOpen("hidden_wall"));
        assertTrue(env.messages.contains("message.lewandivka.cat.hidden_route"));
    }
}
