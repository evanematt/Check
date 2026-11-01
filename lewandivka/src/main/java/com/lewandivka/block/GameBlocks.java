package com.lewandivka.block;

import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.util.Ids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers every block of the catalog. */
public final class GameBlocks {

    private static final Map<String, SpecBlock> BY_ID = new LinkedHashMap<>();

    private GameBlocks() {
    }

    public static void register() {
        for (BlockSpec spec : ModBlocks.ALL) {
            SpecBlock block = SpecBlock.create(spec);
            Registry.register(Registries.BLOCK, Ids.of(spec.id), block);
            BY_ID.put(spec.id, block);
        }
    }

    public static SpecBlock get(String id) {
        SpecBlock b = BY_ID.get(id);
        if (b == null) {
            throw new IllegalArgumentException("unknown block " + id);
        }
        return b;
    }

    public static Iterable<SpecBlock> all() {
        return BY_ID.values();
    }
}
