package com.lewandivka.core.story;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.gen.DistrictPlan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Debtor's traces are a quest-critical set: the counter needs exactly as many markers as the step asks for. */
class DebtorCluesTest {

    @Test
    void everyTraceHasAMarkerInTheDistrictAndTheCountMatchesTheStep() {
        Set<String> ids = new HashSet<>();
        for (StructurePlacement sp : DistrictPlan.get().fixedPlacements()) {
            sp.markers().forEach(m -> ids.add(m.id()));
        }
        for (String clue : DebtorClues.MARKERS) {
            assertTrue(ids.contains(clue), "the plan has no marker " + clue);
        }
        assertEquals(QuestStep.DEBTOR_CLUES.counterMax, DebtorClues.MARKERS.size());
        assertEquals(DebtorClues.MARKERS.size(), new HashSet<>(DebtorClues.MARKERS).size(), "duplicate trace ids");
    }
}
