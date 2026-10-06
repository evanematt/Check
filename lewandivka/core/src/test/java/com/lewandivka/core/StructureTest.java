package com.lewandivka.core;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructureChecks;
import com.lewandivka.core.world.gen.Catalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates every structure of the game against its specification (markers, reachability, gates). */
class StructureTest {

    @Test
    void allStructuresMeetTheirSpecifications() {
        Map<String, Blueprint> all = Catalog.namedBlueprints();
        List<StructureChecks.Issue> issues = new java.util.ArrayList<>();
        for (StructureChecks.Spec spec : Catalog.specs()) {
            Blueprint bp = all.get(spec.id());
            assertTrue(bp != null, "no blueprint for spec " + spec.id());
            issues.addAll(StructureChecks.validate(bp, spec));
        }
        assertEquals(List.of(), issues.stream().map(StructureChecks.Issue::toString).toList());
    }

    @Test
    void everyBlueprintKeyIsAKnownBlock() {
        List<String> unknown = new java.util.ArrayList<>();
        for (Blueprint bp : Catalog.namedBlueprints().values()) {
            for (String key : bp.paletteKeys()) {
                String id = com.lewandivka.core.structure.Keys.blockId(key);
                if (!id.startsWith("minecraft:") && !Catalog.MOD_BLOCKS.contains(id)) {
                    unknown.add(bp.id() + " uses unknown block " + id);
                }
            }
        }
        assertEquals(List.of(), unknown);
    }
}
