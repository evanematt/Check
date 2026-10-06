package com.lewandivka.core.flow;

import com.lewandivka.core.flow.dungeon.Garage13Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Garage13FlowTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    private static void solveBreakers(Garage13Flow f) {
        for (int n : new int[] {3, 1, 4, 2}) {
            f.use("breaker_" + n, A);
        }
    }

    private static void solveLevers(Garage13Flow f) {
        f.use("lever_1", A);
        f.use("lever_3", A);
        f.use("confirm_c", A);
    }

    private static void solveSwitchboxes(FakeEnv env, Garage13Flow f) {
        f.use("sync_1", A);
        env.advance(60);
        f.use("sync_2", B);
    }

    @Test
    void wrongBreakerOrderResetsWithoutLosingAnything() {
        FakeEnv env = new FakeEnv("garage13", 3);
        Garage13Flow f = new Garage13Flow(env);
        f.use("breaker_3", A);
        f.use("breaker_2", A);                         // wrong: 1 is next
        assertEquals("false", env.station("breaker_3", "lit"));
        assertTrue(env.messages.contains("message.lewandivka.garage.wrong"));
        solveBreakers(f);
        assertTrue(env.record.flag("power.a"));
        assertEquals("true", env.station("breaker_2", "lit"));
        assertEquals(1, env.progress);
    }

    @Test
    void wrongLeverPatternDropsAllLevers() {
        FakeEnv env = new FakeEnv("garage13", 1);
        Garage13Flow f = new Garage13Flow(env);
        f.use("lever_1", A);
        f.use("confirm_c", A);                         // pattern needs 1 and 3 up
        assertEquals("false", env.station("lever_1", "powered"));
        assertFalse(env.record.flag("power.c"));
        solveLevers(f);
        assertTrue(env.record.flag("power.c"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void switchboxWindowScalesWithThePartyButNeverFails(int party) {
        FakeEnv env = new FakeEnv("garage13", party);
        Garage13Flow f = new Garage13Flow(env);
        f.use("sync_1", A);
        env.advance(60);                                // 3 s later: the second box
        f.use("sync_2", A);
        // base window 80 ticks: trio 80 (< 60? no: lit), duo 144, solo 288
        assertTrue(env.record.flag("power.b"), "party " + party);
    }

    @Test
    void trioWindowIsShortAndExpires() {
        FakeEnv env = new FakeEnv("garage13", 3);
        Garage13Flow f = new Garage13Flow(env);
        f.use("sync_1", A);
        env.advance(100);                               // longer than the 80 tick trio window
        f.tick();
        assertEquals("false", env.station("sync_1", "lit"));
        f.use("sync_2", A);
        assertFalse(env.record.flag("power.b"));
        f.use("sync_1", B);                             // now both within the window
        assertTrue(env.record.flag("power.b"));
    }

    @Test
    void fullRunSoloRevealsTheGarageAndEscapes() {
        FakeEnv env = new FakeEnv("garage13", 1);
        Garage13Flow f = new Garage13Flow(env);
        solveBreakers(f);
        solveSwitchboxes(env, f);
        assertFalse(env.gateOpen("gate_garage13"));
        solveLevers(f);
        assertEquals(3, f.powerPoints());
        assertTrue(env.gateOpen("gate_garage13"), "the wall between 12 and 14 opens");
        assertEquals("lewandivka:garage_plate[plate=13,facing=south]", env.blocks.get("plate_13"));
        assertEquals(1, env.spawned.get("g13.package:item:package"));
        assertEquals(2, env.checkpoint);
        assertEquals(3, env.progress, "three power points counted on the quest step");

        f.packageTaken(A);
        assertFalse(env.gateOpen("gate_main"), "the main gate closes");
        assertTrue(env.gateOpen("hatch"));
        assertEquals(3, env.spawned.get("g13.guard:gopnik") - 1, "solo gets about half of the guards (3) plus a tunnel guard");
        // the lever only works for a limited, party-scaled time
        f.use("escape_lever", B);
        assertTrue(env.gateOpen("tunnel_gate"));
        env.advance(600);                               // trio would have been closed by now, solo has 1080 ticks
        f.tick();
        assertTrue(env.gateOpen("tunnel_gate"));
        env.advance(500);
        f.tick();
        assertFalse(env.gateOpen("tunnel_gate"));
        f.enteredTunnels();
        assertEquals(3, env.checkpoint);
        f.carrierReachedExit(A);
        assertTrue(f.complete());
        assertEquals(4, env.progress);
    }

    @Test
    void operatorLeverNeedsThePackageToBeTakenFirst() {
        FakeEnv env = new FakeEnv("garage13", 3);
        Garage13Flow f = new Garage13Flow(env);
        f.use("escape_lever", A);
        assertFalse(env.gateOpen("tunnel_gate"));
    }

    @Test
    void growlsAttractGuardsAndAreQuieterOnTheGround() {
        FakeEnv env = new FakeEnv("garage13", 3);
        Garage13Flow f = new Garage13Flow(env);
        solveBreakers(f);
        solveSwitchboxes(env, f);
        solveLevers(f);
        f.packageTaken(A);
        int growls = 0;
        for (int t = 0; t < 2400; t++) {
            env.advance(1);
            f.tick();
        }
        for (String s : env.sounds) {
            if (s.equals("package.growl")) {
                growls++;
            }
        }
        assertTrue(growls >= 4 && growls <= 8, "carried: a growl every 15-25 s, got " + growls);
        env.sounds.clear();
        f.packagePlaced();
        for (int t = 0; t < 2400; t++) {
            env.advance(1);
            f.tick();
        }
        long quiet = env.sounds.stream().filter(s -> s.equals("package.growl")).count();
        assertTrue(quiet < growls, "placed package growls less often: " + quiet);
    }

    @Test
    void resetKeepsSolvedPowerPointsAndRestartsTheEscape() {
        FakeEnv env = new FakeEnv("garage13", 2);
        Garage13Flow f = new Garage13Flow(env);
        solveBreakers(f);
        solveSwitchboxes(env, f);
        solveLevers(f);
        f.packageTaken(A);
        f.reset();
        assertTrue(env.gateOpen("gate_main"));
        assertFalse(env.gateOpen("hatch"));
        assertTrue(env.record.flag("revealed"));
        assertFalse(env.record.flag("taken"));
        assertTrue(env.spawned.keySet().stream().noneMatch(k -> k.startsWith("g13.guard")));
        assertEquals(1, env.spawned.get("g13.package:item:package"), "the package is restored at its spawn");
    }

    @Test
    void rebuildReappliesPersistedProgress() {
        FakeEnv env = new FakeEnv("garage13", 3);
        env.record.setFlag("power.a");
        env.record.setFlag("power.c");
        env.record.setFlag("revealed");
        Garage13Flow f = new Garage13Flow(env);
        f.rebuild();
        assertEquals("minecraft:redstone_lamp[lit=true]", env.blocks.get("lamp_a"));
        assertEquals("true", env.station("confirm_c", "lit"));
        assertTrue(env.gateOpen("gate_garage13"));
        assertEquals(2, f.powerPoints());
    }
}
