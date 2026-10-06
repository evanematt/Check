package com.lewandivka.world.dimension;

import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;

/** The two world plans by the name used in the dimension JSON files. */
public final class Plans {

    private Plans() {
    }

    public static WorldPlan get(String name) {
        return switch (name) {
            case "district" -> DistrictPlan.get();
            case "chromandivka" -> ChromaPlan.get();
            default -> throw new IllegalArgumentException("unknown world plan '" + name + "'");
        };
    }

    public static WorldPlan byDimension(String dimensionId) {
        return switch (dimensionId) {
            case DistrictPlan.ID -> DistrictPlan.get();
            case ChromaPlan.ID -> ChromaPlan.get();
            default -> null;
        };
    }
}
