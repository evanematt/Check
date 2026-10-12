package com.lewandivka.core;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.core.scale.Population;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationTest {

    @Test
    void nightIsTheSecondHalfOfTheDayAndWrapsAround() {
        assertFalse(Population.night(6000));
        assertTrue(Population.night(14000));
        assertTrue(Population.night(24000 * 5 + 18000));
        assertFalse(Population.night(24000 * 5 + 1000));
    }

    @Test
    void hostilesStartWithTheFirstNightAndGrowWithTheParty() {
        assertEquals(0, Population.hostileCap(QuestStep.EXPLORE_DISTRICT, PartyScale.of(3)));
        assertEquals(3, Population.hostileCap(QuestStep.COLLECT_TOKENS, PartyScale.of(1)));
        assertEquals(5, Population.hostileCap(QuestStep.COLLECT_TOKENS, PartyScale.of(2)));
        assertEquals(7, Population.hostileCap(QuestStep.COLLECT_TOKENS, PartyScale.of(3)));
        assertEquals(2, Population.hostileCap(QuestStep.DEBTOR_CHASE, PartyScale.of(1)));
        assertEquals(0, Population.hostileCap(QuestStep.TRAM_FIGHT, PartyScale.of(3)), "the last tram has its own enemies");
        assertEquals(0, Population.hostileCap(QuestStep.BASE_WAKE, PartyScale.of(3)));
    }

    @Test
    void neutralGroupsStandAtTheCornersBeforeTheTram() {
        assertFalse(Population.neutralGroups(QuestStep.EXPLORE_DISTRICT));
        assertTrue(Population.neutralGroups(QuestStep.COLLECT_TOKENS));
        assertTrue(Population.neutralGroups(QuestStep.GARAGE_PANELS));
        assertFalse(Population.neutralGroups(QuestStep.TRAM_FIGHT));
        assertEquals(2, Population.neutralGroupSize(PartyScale.of(1)));
        assertEquals(3, Population.neutralGroupSize(PartyScale.of(3)));
    }

    @Test
    void everySixthHostileIsASeniorWhoAlwaysDropsAToken() {
        Set<String> kinds = new HashSet<>();
        int seniors = 0;
        for (int n = 0; n < 12; n++) {
            String k = Population.hostileKind(n);
            kinds.add(k);
            if (k.equals("senior_yard_gopnik")) {
                seniors++;
            }
        }
        assertEquals(2, seniors);
        assertTrue(kinds.containsAll(Set.of("gopnik", "seed_thrower", "senior_yard_gopnik")));
    }
}
