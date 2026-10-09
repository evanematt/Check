package com.lewandivka.core;

import com.lewandivka.core.puzzle.ChasePace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChasePaceTest {

    private static final double DEBTOR = 0.36;

    @Test
    void multiplierAndPaceAreInverse() {
        for (double pace : new double[] {2.0, 3.5, ChasePace.SOLO, ChasePace.PARTY, 6.0}) {
            double m = ChasePace.multiplierFor(DEBTOR, pace);
            assertEquals(pace, ChasePace.blocksPerSecond(DEBTOR, m), 1e-9);
        }
    }

    @Test
    void theSameMultiplierRunsFasterForAFasterCreature() {
        // the pace grows with the square of the attribute (the speed is also the forward input)
        double slow = ChasePace.blocksPerSecond(0.2, 1.0);
        double fast = ChasePace.blocksPerSecond(0.4, 1.0);
        assertEquals(4.0, fast / slow, 1e-9);
    }

    @Test
    void theChaseIsFairAgainstAPlayer() {
        // a lone player who sprints gains on the Debtor, one who only strolls does not lose by much
        assertTrue(ChasePace.SOLO < ChasePace.PLAYER_SPRINT - 0.8, "a sprinting player must gain on the Debtor");
        assertTrue(ChasePace.SOLO > ChasePace.PLAYER_WALK - 0.5, "a lone Debtor is not slower than a walk by much");
        assertTrue(ChasePace.PARTY > ChasePace.SOLO && ChasePace.PARTY < ChasePace.PLAYER_SPRINT, "a party faces a faster but catchable Debtor");
        double m = ChasePace.multiplierFor(DEBTOR, ChasePace.PARTY);
        assertTrue(m > 0.5 && m < 1.0, "the navigation multiplier stays a plain walking one: " + m);
    }

    @Test
    void aTiredDebtorCanAlwaysBeCaught() {
        assertEquals(1.0, ChasePace.freshness(0), 1e-12);
        assertEquals(1.0, ChasePace.freshness(ChasePace.TIRED_AFTER_TICKS), 1e-12);
        assertEquals(ChasePace.TIRED_FLOOR, ChasePace.freshness(ChasePace.TIRED_FULLY_TICKS), 1e-12);
        assertEquals(ChasePace.TIRED_FLOOR, ChasePace.freshness(10 * ChasePace.TIRED_FULLY_TICKS), 1e-12);
        double previous = 1.0;
        for (long t = 0; t <= ChasePace.TIRED_FULLY_TICKS; t += 100) {
            double f = ChasePace.freshness(t);
            assertTrue(f <= previous + 1e-12, "tiring never reverses");
            previous = f;
        }
        // fully tired, even the Debtor chased by a party is slower than a walking player
        double tiredParty = ChasePace.blocksPerSecond(DEBTOR, ChasePace.multiplierFor(DEBTOR, ChasePace.PARTY) * ChasePace.freshness(ChasePace.TIRED_FULLY_TICKS));
        assertTrue(tiredParty < ChasePace.PLAYER_WALK, "a tired Debtor is slower than a walking player: " + tiredParty);
    }
}
