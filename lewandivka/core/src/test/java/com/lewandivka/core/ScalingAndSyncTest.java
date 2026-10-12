package com.lewandivka.core;

import com.lewandivka.core.boss.PhaseTracker;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.core.scale.SyncGroup;
import com.lewandivka.core.sync.ChargeRelay;
import com.lewandivka.core.sync.ChromaSync;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalingAndSyncTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    // ---------------------------------------------------------------- PartyScale

    @Test
    void partySizeIsClampedAndNeverFails() {
        assertEquals(1, PartyScale.of(0).size());
        assertEquals(1, PartyScale.of(-5).size());
        assertEquals(3, PartyScale.of(7).size());
    }

    @Test
    void bossHealthIsSubLinear() {
        double solo = PartyScale.of(1).bossHealthFactor();
        double duo = PartyScale.of(2).bossHealthFactor();
        double trio = PartyScale.of(3).bossHealthFactor();
        assertTrue(solo < duo && duo < trio);
        assertTrue(trio < 3 * solo, "three players must not simply triple boss health");
        assertTrue(duo < 2 * solo);
    }

    @Test
    void windowsGetLongerForSmallerParties() {
        int base = 60;
        int w3 = PartyScale.of(3).windowTicks(base);
        int w2 = PartyScale.of(2).windowTicks(base);
        int w1 = PartyScale.of(1).windowTicks(base);
        assertEquals(base, w3);
        assertTrue(w2 > w3 && w1 > w2);
        assertTrue(w1 >= 3 * base, "solo must be able to walk between stations");
    }

    @Test
    void addsScaleDownButNeverToZero() {
        assertEquals(10, PartyScale.of(3).adds(10));
        assertEquals(7, PartyScale.of(2).adds(10));
        assertEquals(5, PartyScale.of(1).adds(10));
        assertEquals(1, PartyScale.of(1).adds(1));
        assertEquals(0, PartyScale.of(1).adds(0));
    }

    @Test
    void soloIsHelpedWhenChasingAndVulnerableLonger() {
        assertTrue(PartyScale.of(1).fleeStumbleChance() > PartyScale.of(3).fleeStumbleChance());
        assertTrue(PartyScale.of(1).vulnerableTicks(300) > PartyScale.of(3).vulnerableTicks(300));
        assertTrue(PartyScale.of(1).deadlineTicks(1000) > PartyScale.of(3).deadlineTicks(1000));
    }

    // ---------------------------------------------------------------- SyncGroup

    /** Three levers, shelter style. Returns true if the party managed to light all three at once. */
    private static boolean tryLevers(int partySize, int[] pressTicks) {
        PartyScale scale = PartyScale.of(partySize);
        SyncGroup g = new SyncGroup(3, scale.windowTicks(40)); // 2 s for three players
        boolean done = false;
        for (int i = 0; i < 3; i++) {
            done |= g.activate(i, pressTicks[i]);
        }
        return done;
    }

    @Test
    void threePlayersPressTogether() {
        assertTrue(tryLevers(3, new int[] {100, 110, 120}));
        assertFalse(tryLevers(3, new int[] {100, 160, 220}), "too slow for the short window");
    }

    @Test
    void twoPlayersHaveAMediumWindow() {
        // one player covers two levers 50 ticks apart, the other presses in between
        assertTrue(tryLevers(2, new int[] {100, 130, 150}));
        assertFalse(tryLevers(2, new int[] {100, 230, 320}));
    }

    @Test
    void soloPlayerCanDoItSequentially() {
        // levers are at most ~8 blocks apart: about 2 s (40 ticks) of walking between presses
        assertTrue(tryLevers(1, new int[] {100, 140, 180}));
        assertFalse(tryLevers(1, new int[] {100, 300, 500}), "a window is still a window");
    }

    @Test
    void syncGroupExpiresAndResets() {
        SyncGroup g = new SyncGroup(2, 20);
        assertFalse(g.activate(0, 0));
        assertEquals(1, g.activeCount(5));
        assertEquals(0, g.activeCount(25), "lever released after the window");
        assertFalse(g.activate(1, 30), "first lever is already dark");
        assertTrue(g.activate(0, 35));
        assertTrue(g.satisfied());
        g.reset();
        assertFalse(g.satisfied());
        assertEquals(0, g.activeCount(35));
    }

    @Test
    void syncGroupIgnoresInvalidStations() {
        SyncGroup g = new SyncGroup(2, 20);
        assertFalse(g.activate(-1, 0));
        assertFalse(g.activate(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new SyncGroup(0, 10));
    }

    // ---------------------------------------------------------------- ChromaSync

    @Test
    void chromaThreePlayersAllMustConsume() {
        ChromaSync s = new ChromaSync();
        Set<UUID> online = Set.of(A, B, C);
        assertEquals(ChromaSync.Event.STARTED, s.consume(A, online, 0));
        assertTrue(s.running());
        assertEquals(2, s.missing());
        assertEquals(ChromaSync.Event.CONSUMED, s.consume(B, online, 100));
        assertEquals(ChromaSync.Event.NONE, s.consume(B, online, 110), "double consume is ignored");
        assertEquals(ChromaSync.Event.COMPLETED, s.consume(C, online, 200));
        assertEquals(ChromaSync.Phase.COMPLETE, s.phase());
    }

    @Test
    void chromaSoloCompletesImmediately() {
        ChromaSync s = new ChromaSync();
        assertEquals(ChromaSync.Event.COMPLETED, s.consume(A, Set.of(A), 0));
    }

    @Test
    void chromaTwoPlayers() {
        ChromaSync s = new ChromaSync();
        assertEquals(ChromaSync.Event.STARTED, s.consume(A, Set.of(A, B), 0));
        assertEquals(ChromaSync.Event.COMPLETED, s.consume(B, Set.of(A, B), 50));
    }

    @Test
    void chromaExpiresAfterFiveMinutesAndCanBeRetried() {
        ChromaSync s = new ChromaSync();
        Set<UUID> online = Set.of(A, B);
        s.consume(A, online, 0);
        assertEquals(ChromaSync.Event.NONE, s.tick(online, 5 * 60 * 20 - 1));
        assertEquals(ChromaSync.Event.EXPIRED, s.tick(online, 5 * 60 * 20));
        assertEquals(ChromaSync.Phase.IDLE, s.phase());
        assertTrue(s.consumed().isEmpty(), "failed attempt consumes nothing");
        // retry works
        assertEquals(ChromaSync.Event.STARTED, s.consume(A, online, 7000));
        assertEquals(ChromaSync.Event.COMPLETED, s.consume(B, online, 7100));
    }

    @Test
    void chromaDisconnectedPlayerCannotBlockThePartyAndLateJoinerIsNotRequired() {
        ChromaSync s = new ChromaSync();
        s.consume(A, Set.of(A, B, C), 0);
        s.consume(B, Set.of(A, B, C), 10);
        // C leaves the game: A and B already consumed -> completes
        assertEquals(ChromaSync.Event.COMPLETED, s.tick(Set.of(A, B), 20));

        ChromaSync s2 = new ChromaSync();
        s2.consume(A, Set.of(A), 0 + 0); // solo completes right away; use two to keep it running
        ChromaSync s3 = new ChromaSync();
        assertEquals(ChromaSync.Event.STARTED, s3.consume(A, Set.of(A, B), 0));
        // late joiner C consumes while running; not required
        assertEquals(ChromaSync.Event.CONSUMED, s3.consume(C, Set.of(A, B, C), 5));
        assertEquals(ChromaSync.Event.COMPLETED, s3.consume(B, Set.of(A, B, C), 6));
        assertTrue(s3.participants().containsAll(List.of(A, B, C)));
    }

    @Test
    void chromaAbortsWhenEveryoneLeaves() {
        ChromaSync s = new ChromaSync();
        s.consume(A, Set.of(A, B), 0);
        assertEquals(ChromaSync.Event.ABORTED, s.tick(Set.of(), 5));
        assertEquals(ChromaSync.Phase.IDLE, s.phase());
    }

    // ---------------------------------------------------------------- ChargeRelay

    @Test
    void chargeOverloadsAfterTwelveSecondsAndPassResetsTheTimer() {
        ChargeRelay r = new ChargeRelay();
        List<UUID> all = List.of(A, B, C);
        r.grant(A, 0);
        assertTrue(r.canDamageBoss(A));
        assertFalse(r.canDamageBoss(B));
        assertTrue(r.tick(239, all).isEmpty());
        // pass just before overload
        assertEquals(ChargeRelay.Event.PASSED, r.pass(A, B, 239).event());
        assertTrue(r.isHolder(B));
        assertTrue(r.tick(300, all).isEmpty(), "timer was reset by the pass");
        List<ChargeRelay.Result> out = r.tick(239 + 240, all);
        assertEquals(1, out.size());
        assertEquals(ChargeRelay.Event.OVERLOAD, out.get(0).event());
        assertEquals(B, out.get(0).player());
        assertEquals(1, r.overloadCount());
    }

    @Test
    void passValidation() {
        ChargeRelay r = new ChargeRelay();
        r.grant(A, 0);
        assertEquals(ChargeRelay.Event.NONE, r.pass(B, C, 1).event(), "only the holder can pass");
        assertEquals(ChargeRelay.Event.NONE, r.pass(A, A, 1).event(), "cannot pass to yourself");
        assertEquals(ChargeRelay.Event.NONE, r.pass(A, null, 1).event());
    }

    @Test
    void soloRelayReturnsWithAFreshTimer() {
        ChargeRelay r = new ChargeRelay();
        List<UUID> solo = List.of(A);
        r.grant(A, 0);
        assertEquals(ChargeRelay.Event.RELAYED, r.throwToRelay(A, 0, 200).event());
        assertTrue(r.inRelay());
        assertFalse(r.canDamageBoss(A), "nobody holds it while it sits in the relay");
        assertTrue(r.tick(200 + ChargeRelay.DEFAULT_RELAY_DELAY_TICKS - 1, solo).isEmpty());
        List<ChargeRelay.Result> back = r.tick(200 + ChargeRelay.DEFAULT_RELAY_DELAY_TICKS, solo);
        assertEquals(ChargeRelay.Event.RETURNED, back.get(0).event());
        assertEquals(A, back.get(0).player());
        long t = 200 + ChargeRelay.DEFAULT_RELAY_DELAY_TICKS;
        assertEquals(ChargeRelay.DEFAULT_OVERLOAD_TICKS, r.remaining(t));
    }

    @Test
    void chargeIsNeverLostWhenTheHolderLeavesOrDies() {
        ChargeRelay r = new ChargeRelay();
        r.grant(A, 0);
        List<ChargeRelay.Result> out = r.tick(10, List.of(B, C));
        assertEquals(ChargeRelay.Event.REASSIGNED, out.get(0).event());
        assertEquals(B, r.holder());
        // everybody gone: dormant, then re-granted as soon as somebody is eligible again
        assertTrue(r.tick(11, List.of()).isEmpty());
        assertNull(r.holder());
        List<ChargeRelay.Result> again = r.tick(12, List.of(C));
        assertEquals(ChargeRelay.Event.GRANTED, again.get(0).event());
        assertEquals(C, r.holder());
    }

    @Test
    void freezeStopsTheOverloadTimer() {
        ChargeRelay r = new ChargeRelay();
        List<UUID> all = List.of(A);
        r.grant(A, 0);
        r.freeze(100, 600);
        for (long t = 100; t < 700; t++) {
            assertTrue(r.tick(t, all).isEmpty(), "no overload while frozen at tick " + t);
        }
        assertTrue(r.remaining(700) > 0);
    }

    // ---------------------------------------------------------------- PhaseTracker

    @Test
    void phasesAdvanceMonotonicallyAndProcessEveryThreshold() {
        PhaseTracker p = new PhaseTracker(0.70, 0.35, 0.05);
        assertEquals(4, p.phaseCount());
        assertEquals(-1, p.advanceIfNeeded(0.9));
        assertEquals(1, p.advanceIfNeeded(0.69));
        assertEquals(-1, p.advanceIfNeeded(0.69));
        // a single huge hit drops to 2 %: both remaining thresholds must be processed one by one
        assertEquals(2, p.advanceIfNeeded(0.02));
        assertEquals(3, p.advanceIfNeeded(0.02));
        assertEquals(-1, p.advanceIfNeeded(0.02));
        assertTrue(p.isLastPhase());
        // healing never reverts
        assertEquals(-1, p.advanceIfNeeded(1.0));
        assertEquals(3, p.phase());
        p.reset();
        assertEquals(0, p.phase());
    }

    @Test
    void phaseForDoesNotMutate() {
        PhaseTracker p = new PhaseTracker(0.7, 0.35);
        assertEquals(2, p.phaseFor(0.1));
        assertEquals(0, p.phase());
        assertThrows(IllegalArgumentException.class, () -> new PhaseTracker(0.3, 0.5));
    }
}
