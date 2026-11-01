package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.RainbowGarageFlow;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainbowGarageFlowTest {

    private static final UUID A = new UUID(0, 1);

    private static void mix(RainbowGarageFlow f, String... taps) {
        for (String t : taps) {
            f.use("tap_" + t, A);
        }
        f.use("tap_release", A);
    }

    @Test
    void theThreeGratesOpenInOrderForOrangeGreenAndPurple() {
        FakeEnv env = new FakeEnv("rainbow_garage", 2);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        mix(f, "red", "yellow");
        assertTrue(env.gateOpen("grate_1"));
        mix(f, "red", "blue");                       // purple is not green: sludge
        assertFalse(env.gateOpen("grate_2"));
        assertTrue(env.messages.contains("message.lewandivka.rg.sludge"));
        mix(f, "yellow", "blue");
        assertTrue(env.gateOpen("grate_2"));
        mix(f, "red", "blue");
        assertTrue(env.gateOpen("grate_3"));
        f.use("tap_release", A);
        assertEquals(3, env.gates.entrySet().stream().filter(e -> e.getKey().startsWith("grate_") && e.getValue()).count());
    }

    @Test
    void releasingAnEmptyMixerDoesNothingAndTapsLightWhileInTheMix() {
        FakeEnv env = new FakeEnv("rainbow_garage", 1);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        f.use("tap_release", A);
        assertTrue(env.messages.contains("message.lewandivka.rg.empty"));
        f.use("tap_red", A);
        assertEquals("true", env.station("tap_red", "lit"));
        f.use("tap_red", A);
        assertEquals("false", env.station("tap_red", "lit"));
    }

    @Test
    void batteriesAreGivenOncePerWingAndOpenTheBossDoorInTheHub() {
        FakeEnv env = new FakeEnv("rainbow_garage", 3);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        f.use("battery_wing_p", A);
        f.use("battery_wing_p", A);
        assertEquals(1, env.given.get(QuestItems.BATTERY), "a wing gives one battery");
        f.use("battery_wing_l", A);
        f.use("battery_wing_i", A);
        assertEquals(3, f.wingsDone());
        assertEquals(3, env.eventCount(Events.RG_WING));
        assertEquals(3, env.world.counter("rg.wings_done"));
        env.inventory.put(QuestItems.BATTERY, 3);
        f.useWith("socket_1", A, QuestItems.BATTERY);
        f.useWith("socket_2", A, "");                         // empty hand
        assertFalse(env.gateOpen("boss_door"));
        f.useWith("socket_2", A, QuestItems.BATTERY);
        f.useWith("socket_3", A, QuestItems.BATTERY);
        assertTrue(env.gateOpen("boss_door"));
        assertTrue(env.gateOpen("boss_door_inner"));
        assertTrue(env.bosses.contains("garage_king"));
        assertEquals(3, env.world.counter("rg.batteries_installed"));
        assertEquals(0, env.inventory.get(QuestItems.BATTERY));
    }

    @Test
    void liftsAreCalledFromBothLevelsAndIgnoreImpatientPresses() {
        FakeEnv env = new FakeEnv("rainbow_garage", 1);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        f.use("lift_1_high", A);
        assertEquals(true, env.lifts.get("lift_1"));
        env.lifts.put("lift_1", false);
        f.use("lift_1_low", A);                                // still moving
        assertEquals(false, env.lifts.get("lift_1"));
        env.advance(RainbowGarageFlow.LIFT_TICKS + 1);
        f.use("lift_1_low", A);
        assertEquals(false, env.lifts.get("lift_1"));
        assertFalse(f.liftUp(1));
        env.advance(RainbowGarageFlow.LIFT_TICKS + 1);
        f.use("lift_1_high", A);
        assertTrue(f.liftUp(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void everyPressLeavesAnOpenWindowAndWarnsBeforeSlamming(int party) {
        FakeEnv env = new FakeEnv("rainbow_garage", party);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        int[] closedTicks = new int[RainbowGarageFlow.PRESSES];
        int[] slams = new int[RainbowGarageFlow.PRESSES];
        boolean[] prev = new boolean[RainbowGarageFlow.PRESSES];
        int total = 4000;
        for (int t = 0; t < total; t++) {
            env.advance(1);
            f.tick();
            for (int i = 0; i < RainbowGarageFlow.PRESSES; i++) {
                boolean closed = !env.gateOpen("press_" + (i + 1)) && env.gates.containsKey("press_" + (i + 1));
                if (closed) {
                    closedTicks[i]++;
                    if (!prev[i]) {
                        slams[i]++;
                    }
                }
                prev[i] = closed;
            }
        }
        for (int i = 0; i < RainbowGarageFlow.PRESSES; i++) {
            assertTrue(slams[i] >= 15, "press " + i + " cycles");
            assertTrue(closedTicks[i] / (double) total < 0.35, "the press is closed only a minor part of the time: " + closedTicks[i] / (double) total);
        }
        assertTrue(env.log.stream().anyMatch(l -> l.startsWith("fx press_1 warn")), "the lamps warn first");
        // a lone player gets longer cycles than a trio
        if (party == 1) {
            assertTrue(slams[0] < 4000 / RainbowGarageFlow.PRESS_PERIOD[0]);
        }
    }

    @Test
    void theArenaLeversGoToTheGarageKing() {
        FakeEnv env = new FakeEnv("rainbow_garage", 3);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        f.use("lever_a", A);
        f.use("lever_c", A);
        assertEquals(java.util.List.of("garage_king:lever:0", "garage_king:lever:2"), env.stationCalls);
    }

    @Test
    void rebuildRestoresSolvedPartsAndAResetKeepsThem() {
        FakeEnv env = new FakeEnv("rainbow_garage", 2);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        mix(f, "red", "yellow");
        f.use("lift_2_high", A);
        f.use("battery_wing_p", A);
        env.advance(100);
        FakeEnv later = new FakeEnv("rainbow_garage", 2);
        later.record.setFlag("grate.1");
        later.record.setFlag("lift.2.up");
        later.record.setFlag("socket.3");
        later.record.setFlag("battery.p");
        later.record.setFlag("boss.door");
        new RainbowGarageFlow(later).rebuild();
        assertTrue(later.gateOpen("grate_1"));
        assertEquals(true, later.lifts.get("lift_2"));
        assertEquals(false, later.lifts.get("lift_1"));
        assertEquals("true", later.station("socket_3", "filled"));
        assertEquals("false", later.station("battery_wing_p", "filled"));
        assertTrue(later.gateOpen("boss_door"));
        f.reset();
        assertTrue(env.record.flag("grate.1"));
        mix(f, "yellow", "blue");
        assertTrue(env.gateOpen("grate_2"), "the paint puzzle continues where it was");
    }

    @Test
    void defeatingTheKingCompletesTheEncounter() {
        FakeEnv env = new FakeEnv("rainbow_garage", 1);
        RainbowGarageFlow f = new RainbowGarageFlow(env);
        assertFalse(f.complete());
        f.bossDefeated("garage_king");
        assertTrue(f.complete());
        f.playerEntered(A);
        assertEquals(1, env.eventCount(Events.RG_ENTERED));
    }
}
