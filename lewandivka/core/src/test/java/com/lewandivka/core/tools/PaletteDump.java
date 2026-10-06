package com.lewandivka.core.tools;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.core.world.gen.Catalog;
import com.lewandivka.core.world.gen.ChromaPlan;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.core.world.gen.Nature;
import com.lewandivka.core.world.gen.Props;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * {@code gradle dumpPalette} writes every block key the world generators can place (all blueprints,
 * every variant, terrain tops and decorations) to {@code build/palette.txt}; the registry validator
 * checks the list against the Minecraft 1.20.1 block registry.
 */
public final class PaletteDump {

    private PaletteDump() {
    }

    public static void main(String[] args) throws Exception {
        TreeSet<String> keys = new TreeSet<>();
        List<Blueprint> all = new ArrayList<>(Catalog.namedBlueprints().values());
        for (int i = 0; i < Nature.MUSHROOM_VARIANTS; i++) {
            all.add(Nature.glowshroom(i));
        }
        for (int i = 0; i < Nature.RAINBOW_TREE_VARIANTS; i++) {
            all.add(Nature.rainbowTree(i));
        }
        for (int i = 0; i < Nature.CRYSTAL_VARIANTS; i++) {
            all.add(Nature.crystals(i));
        }
        for (int i = 0; i < Props.TREE_VARIANTS; i++) {
            all.add(Props.tree(i));
        }
        for (WorldPlan plan : List.of(DistrictPlan.get(), ChromaPlan.get())) {
            all.addAll(plan.fixedPlacements().stream().map(p -> p.blueprint()).toList());
            for (int x = -260; x <= 260; x += 6) {
                for (int z = -280; z <= 240; z += 6) {
                    all.addAll(plan.scatterIn(x, z, x + 5, z + 5).stream().map(p -> p.blueprint()).toList());
                }
            }
            TerrainColumn c = new TerrainColumn();
            for (int x = -260; x <= 260; x += 3) {
                for (int z = -280; z <= 240; z += 3) {
                    plan.column(x, z, c);
                    keys.add(c.top);
                    keys.add(c.sub);
                    keys.add(c.base);
                    keys.add(c.fluid);
                    if (c.decor != null) {
                        keys.add(c.decor);
                    }
                }
            }
        }
        for (Blueprint bp : all) {
            for (String k : bp.paletteKeys()) {
                if (k != null) {
                    keys.add(k);
                }
            }
        }
        File out = new File(System.getProperty("out", "build/palette.txt"));
        out.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            for (String k : keys) {
                w.println(k);
            }
        }
        System.out.println("wrote " + keys.size() + " keys to " + out);
    }
}
