package com.lewandivka.core.flow;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.gen.Catalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointsTest {

    @Test
    void everyCheckpointNamedInTheTableExistsInItsBlueprint() {
        for (String structure : Checkpoints.structures()) {
            Blueprint bp = Catalog.namedBlueprints().get(structure);
            assertNotNull(bp, "blueprint of " + structure);
            for (String name : Checkpoints.names(structure)) {
                boolean found = bp.markers().stream().anyMatch(m -> m.name().equals(name));
                assertTrue(found, structure + " has no marker " + name);
            }
        }
    }

    @Test
    void indexesAreClampedAndUnknownStructuresHaveNone() {
        assertEquals("cp_0", Checkpoints.marker("garage13", -4));
        assertEquals("cp_3", Checkpoints.marker("garage13", 9));
        assertEquals("cp_pumps", Checkpoints.marker("aquapark", 1));
        assertNull(Checkpoints.marker("base", 1));
    }
}
