package com.lewandivka.core;

import com.lewandivka.core.world.gen.DistrictPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The district is laid out so that nothing is built on a street, on another building or on a place where a quest puts somebody. */
class DistrictLayoutTest {

    @Test
    void noBuildingStandsOnAStreetOnAnotherBuildingOrOnAQuest() {
        List<String> problems = DistrictPlan.get().layoutProblems();
        assertTrue(problems.isEmpty(), problems.size() + " problems: " + problems);
    }
}
