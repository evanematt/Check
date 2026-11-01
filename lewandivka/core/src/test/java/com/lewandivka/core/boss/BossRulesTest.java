package com.lewandivka.core.boss;

import com.lewandivka.core.boss.BossEvent.Type;
import com.lewandivka.core.scale.PartyScale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure rules of the five fights: every mechanic must work for one, two and three players. */
class BossRulesTest {

    private static boolean has(List<BossEvent> events, Type type) {
        return events.stream().anyMatch(e -> e.type() == type);
    }

    private static long count(List<BossEvent> events, Type type) {
        return events.stream().filter(e -> e.type() == type).count();
    }

    /** Runs the ticks and collects everything the rules emitted. */
    private static List<BossEvent> run(BossRules rules, long from, long to, double health) {
        List<BossEvent> all = new ArrayList<>();
        for (long t = from; t < to; t++) {
            all.addAll(rules.tick(t, health));
        }
        return all;
    }

    // ------------------------------------------------------------------ Garage King

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void garageKingShieldDropsOnlyWhenAllLeversAreHeldTogether(int players) {
        GarageKingRules king = new GarageKingRules(7);
        king.start(PartyScale.of(players), 0);
        assertEquals(0.0, king.damageFactor(1));
        int window = king.syncWindowTicks();
        assertEquals(PartyScale.of(players).windowTicks(100), window);

        // two levers, then wait too long: the first one went dark again
        king.useLever(0, 10);
        king.useLever(1, 11);
        assertTrue(king.shielded(window + 12));
        List<BossEvent> late = king.useLever(2, window + 12);
        assertFalse(has(late, Type.CORE_OPEN), "the first lever was already dark");

        // all three inside the window (walking speed of a lone player is covered by the long window)
        long t = 1000;
        king.useLever(0, t);
        king.useLever(1, t + window / 3);
        List<BossEvent> done = king.useLever(2, t + window / 3 * 2);
        assertTrue(has(done, Type.CORE_OPEN));
        assertTrue(has(done, Type.SHIELD_OFF));
        assertEquals(1.0, king.damageFactor(t + window / 3 * 2 + 1));
        assertEquals(1, king.coresOpened());
    }

    @Test
    void garageKingCoreClosesAfterFifteenSecondsAndShieldReturns() {
        GarageKingRules king = new GarageKingRules(1);
        king.start(PartyScale.of(3), 0);
        king.useLever(0, 100);
        king.useLever(1, 101);
        king.useLever(2, 102);
        assertEquals(1.0, king.damageFactor(110));
        List<BossEvent> events = run(king, 103, 102 + GarageKingRules.CORE_OPEN_TICKS + 5, 1.0);
        assertTrue(has(events, Type.CORE_CLOSE));
        assertTrue(has(events, Type.SHIELD_ON));
        assertEquals(0.0, king.damageFactor(102 + GarageKingRules.CORE_OPEN_TICKS + 5));
        // the next cycle needs a fresh sync
        king.useLever(0, 1000);
        assertTrue(king.shielded(1001));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void garageKingPhaseOneAddsMechanicsAndMalfunctionsNeedARepair(int players) {
        GarageKingRules king = new GarageKingRules(11);
        king.start(PartyScale.of(players), 0);
        List<BossEvent> early = run(king, 0, 400, 0.9);
        assertFalse(has(early, Type.WHEEL), "no wheels before half health");
        List<BossEvent> hard = run(king, 400, 4000, 0.4);
        assertTrue(has(hard, Type.PHASE));
        assertEquals(1, king.phase());
        assertTrue(has(hard, Type.WHEEL));
        assertTrue(has(hard, Type.ADDS));
        assertTrue(has(hard, Type.CAR));
        assertTrue(has(hard, Type.MALFUNCTION));
        int lever = hard.stream().filter(e -> e.type() == Type.MALFUNCTION).findFirst().orElseThrow().a();
        // find a moment where a malfunction is active and use the lever: that repairs, it is no sync press
        long t = 4000;
        while (!king.malfunctioning(lever, t) && t < 9000) {
            king.tick(t, 0.4);
            t++;
        }
        if (king.malfunctioning(lever, t)) {
            List<BossEvent> fix = king.useLever(lever, t);
            assertTrue(has(fix, Type.REPAIRED));
            assertFalse(king.malfunctioning(lever, t));
            assertFalse(king.leverLit(lever, t), "a repair is not a sync press");
        }
    }

    @Test
    void bossStartsFromScratchAfterReset() {
        GarageKingRules king = new GarageKingRules(5);
        king.start(PartyScale.of(2), 0);
        run(king, 0, 100, 0.3);
        assertEquals(1, king.phase());
        king.reset();
        assertFalse(king.started());
        king.start(PartyScale.of(1), 500);
        assertEquals(0, king.phase());
        assertEquals(0.0, king.damageFactor(501));
    }

    @Test
    void sameSeedGivesTheSameFight() {
        List<BossEvent> a = run(startedKing(42, 3), 0, 3000, 0.3);
        List<BossEvent> b = run(startedKing(42, 3), 0, 3000, 0.3);
        assertEquals(a, b);
        List<BossEvent> c = run(startedKing(43, 3), 0, 3000, 0.3);
        assertFalse(a.equals(c), "a different seed should change the pattern");
    }

    private static GarageKingRules startedKing(long seed, int players) {
        GarageKingRules k = new GarageKingRules(seed);
        k.start(PartyScale.of(players), 0);
        return k;
    }

    // ------------------------------------------------------------------ Collar Collector

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void collectorNeedsBothRealCollars(int players) {
        CollarCollectorRules boss = new CollarCollectorRules(3);
        List<BossEvent> start = boss.start(PartyScale.of(players), 0);
        assertTrue(has(start, Type.NAMES_HIDDEN));
        assertTrue(has(start, Type.COLLARS_SHUFFLED));
        assertEquals(0.0, boss.damageFactor(1));

        int a = -1;
        int b = -1;
        for (int i = 0; i < CollarCollectorRules.STANDS; i++) {
            if (boss.isRealStand(i)) {
                if (a < 0) {
                    a = i;
                } else {
                    b = i;
                }
            }
        }
        assertTrue(a >= 0 && b >= 0 && a != b);
        int decoy = 0;
        while (boss.isRealStand(decoy)) {
            decoy++;
        }
        List<BossEvent> wrong = boss.useStand(decoy, 10);
        assertEquals(0, wrong.stream().filter(e -> e.type() == Type.COLLAR_REACTION).findFirst().orElseThrow().b());
        assertEquals(0.0, boss.damageFactor(11));
        List<BossEvent> first = boss.useStand(a, 12);
        assertEquals(1, first.stream().filter(e -> e.type() == Type.COLLAR_REACTION).findFirst().orElseThrow().b());
        assertEquals(0.0, boss.damageFactor(13));
        List<BossEvent> second = boss.useStand(b, 14);
        assertTrue(has(second, Type.SHIELD_OFF));
        assertEquals(1.0, boss.damageFactor(15));
        // a stand can only be used once
        assertTrue(boss.useStand(b, 15).isEmpty());
        // after twenty seconds the shield is back and the collars are shuffled again
        List<BossEvent> later = run(boss, 15, 14 + CollarCollectorRules.VULNERABLE_TICKS + 2, 0.9);
        assertTrue(has(later, Type.SHIELD_ON));
        assertTrue(has(later, Type.COLLARS_SHUFFLED));
        assertEquals(0.0, boss.damageFactor(14 + CollarCollectorRules.VULNERABLE_TICKS + 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void collectorLeashAlwaysEndsEvenWithoutHelp(int players) {
        CollarCollectorRules boss = new CollarCollectorRules(9);
        boss.start(PartyScale.of(players), 0);
        List<BossEvent> events = run(boss, 0, 4000, 0.6);
        assertEquals(1, boss.phase());
        assertTrue(has(events, Type.LEASH));
        assertTrue(has(events, Type.LEASH_BROKEN_BY_CAT), "Chinazik bites the leash when nobody breaks the anchor");
        assertFalse(boss.leashed() && count(events, Type.LEASH) == count(events, Type.LEASH_END) + 1 && false);
        assertEquals(1.0, boss.damageFactor(5000), "phase 1 is a normal fight");
        List<BossEvent> clones = run(boss, 4000, 9000, 0.2);
        assertTrue(has(clones, Type.CLONES));
        assertTrue(has(clones, Type.REAL_BOSS_HINT), "the cats mark the real collector");
        assertTrue(has(clones, Type.CLONES_GONE));
    }

    @Test
    void anchorDestroyedEndsTheLeash() {
        CollarCollectorRules boss = new CollarCollectorRules(9);
        boss.start(PartyScale.of(3), 0);
        long t = 0;
        while (!boss.leashed() && t < 3000) {
            boss.tick(t++, 0.6);
        }
        assertTrue(boss.leashed());
        List<BossEvent> freed = boss.anchorDestroyed(t);
        assertTrue(has(freed, Type.LEASH_END));
        assertFalse(boss.leashed());
    }

    // ------------------------------------------------------------------ Lady Vortex

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void vortexWarnsBeforeFloodingAndNeverFloodsEverything(int players) {
        LadyVortexRules boss = new LadyVortexRules(21);
        boss.start(PartyScale.of(players), 0);
        List<BossEvent> events = run(boss, 0, 3000, 1.0);
        List<BossEvent> warns = events.stream().filter(e -> e.type() == Type.ZONE_WARN).toList();
        List<BossEvent> floods = events.stream().filter(e -> e.type() == Type.ZONE_FLOOD).toList();
        assertFalse(warns.isEmpty());
        assertFalse(floods.isEmpty());
        assertTrue(warns.stream().allMatch(e -> e.b() >= 70), "warning lasts at least 3.5 seconds");
        // every flooded zone was announced first
        long firstWarn = events.indexOf(warns.get(0));
        long firstFlood = events.indexOf(floods.get(0));
        assertTrue(firstWarn < firstFlood);
        assertTrue(boss.floodedZones().size() <= 2);
        assertEquals(1.0, boss.damageFactor(10), "phase 0 is a normal fight");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void vortexPhaseOneNeedsTheWaterCoresInTheDrains(int players) {
        LadyVortexRules boss = new LadyVortexRules(5);
        boss.start(PartyScale.of(players), 0);
        run(boss, 0, 10, 0.5);
        assertEquals(1, boss.phase());
        assertEquals(0.0, boss.damageFactor(11), "shielded until the drains are used");
        long t = 10;
        boolean started = false;
        while (t < 4000 && !started) {
            started = has(boss.tick(t++, 0.5), Type.DRAIN_PHASE_START);
        }
        assertTrue(started);
        int required = boss.drainsRequired();
        assertEquals(players >= 2 ? 3 : 2, required);
        for (int i = 0; i < required - 1; i++) {
            assertTrue(boss.useDrain(i, t).isEmpty() || !boss.useDrain(i, t).isEmpty());
        }
        // use the remaining drain: shield falls
        List<BossEvent> done = boss.useDrain(required - 1, t + 1);
        assertTrue(has(done, Type.VULNERABLE_START));
        assertEquals(1.0, boss.damageFactor(t + 2));
        List<BossEvent> later = run(boss, t + 2, t + 2 + LadyVortexRules.VULNERABLE_TICKS + 2, 0.5);
        assertTrue(has(later, Type.SHIELD_ON));
    }

    @Test
    void vortexFailedDrainPhaseRecovers() {
        LadyVortexRules boss = new LadyVortexRules(5);
        boss.start(PartyScale.of(3), 0);
        List<BossEvent> events = run(boss, 0, 6000, 0.5);
        assertTrue(has(events, Type.DRAIN_PHASE_FAILED), "an unused drain phase times out instead of locking the fight");
        assertTrue(count(events, Type.DRAIN_PHASE_START) >= 2, "and it starts again");
    }

    // ------------------------------------------------------------------ Conductor

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void conductorTramsAreAnnouncedAndTicketsCanBeAnswered(int players) {
        ConductorRules boss = new ConductorRules(77);
        boss.start(PartyScale.of(players), 0);
        List<BossEvent> events = run(boss, 0, 3000, 1.0);
        assertTrue(has(events, Type.TRAM_WARN));
        assertTrue(has(events, Type.TRAM_RUN));
        assertTrue(events.stream().filter(e -> e.type() == Type.TRAM_WARN).allMatch(e -> e.b() >= 80));
        assertTrue(has(events, Type.TICKET_CALL));
        // answer a call
        long t = 3000;
        int target;
        while (true) {
            List<BossEvent> ev = boss.tick(t++, 1.0);
            var call = ev.stream().filter(e -> e.type() == Type.TICKET_CALL).findFirst();
            if (call.isPresent()) {
                target = call.get().a();
                break;
            }
            assertTrue(t < 9000, "a ticket call must come");
        }
        assertEquals(target, boss.calledTarget());
        boss.ticketValidated(target, t);
        assertEquals(-1, boss.calledTarget());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void conductorComposterSyncDropsTheShield(int players) {
        ConductorRules boss = new ConductorRules(3);
        boss.start(PartyScale.of(players), 0);
        assertEquals(0.0, boss.damageFactor(5));
        int w = boss.syncWindowTicks();
        boss.useComposter(0, 100);
        boss.useComposter(1, 100 + w / 2);
        List<BossEvent> done = boss.useComposter(2, 100 + w - 2);
        assertTrue(has(done, Type.VULNERABLE_START));
        assertEquals(1.0, boss.damageFactor(100 + w));
        List<BossEvent> later = run(boss, 100 + w, 100 + w + ConductorRules.VULNERABLE_TICKS + 5, 1.0);
        assertTrue(has(later, Type.SHIELD_ON));
        assertEquals(0.0, boss.damageFactor(100 + w + ConductorRules.VULNERABLE_TICKS + 5));
    }

    @Test
    void conductorMissedTicketIsNotLethalJustAnEvent() {
        ConductorRules boss = new ConductorRules(1);
        boss.start(PartyScale.of(1), 0);
        List<BossEvent> events = run(boss, 0, 6000, 1.0);
        assertTrue(has(events, Type.TICKET_FAIL));
        assertTrue(count(events, Type.TICKET_CALL) >= 2, "the next call arrives after a failed one");
    }
}
