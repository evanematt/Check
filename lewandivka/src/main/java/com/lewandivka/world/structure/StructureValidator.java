package com.lewandivka.world.structure;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.world.structure.Structures.Site;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Development check of the generated world: every block key of every blueprint resolves, and a deterministic sample of
 * cells of every structure really is in the world where the blueprint says (so offsets, rotations and chunk borders are
 * right). Used by {@code /lewandivka validate} on a running server and by the GameTests.
 */
public final class StructureValidator {

    public record Report(int structures, int sampled, int mismatches, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }

        public String summary() {
            return structures + " structures, " + sampled + " cells sampled, " + mismatches + " differ, " + problems.size() + " problems";
        }
    }

    private StructureValidator() {
    }

    /** @param worlds gives the block view of a dimension id ({@code lewandivka:district}), or null when it is not available */
    public static Report validate(Function<String, BlockView> worlds, int cellsPerStructure) {
        List<String> problems = new ArrayList<>();
        int sampled = 0;
        int mismatches = 0;
        int structures = 0;
        for (Site site : Structures.sites()) {
            StructurePlacement p = site.placement();
            Blueprint bp = p.blueprint();
            structures++;
            for (String key : bp.paletteKeys()) {
                BlockState state = StateResolver.parse(key);
                if (state.isAir() && !key.startsWith("minecraft:air") && !key.startsWith("minecraft:cave_air")) {
                    problems.add(p.id() + ": block key '" + key + "' does not resolve");
                }
            }
            BlockView world = worlds.apply(site.dimension());
            if (world == null) {
                problems.add(p.id() + ": dimension " + site.dimension() + " is not available");
                continue;
            }
            Random rnd = new Random(p.id().hashCode());
            int taken = 0;
            int bad = 0;
            List<String> examples = new ArrayList<>();
            for (int i = 0; i < cellsPerStructure * 15 && taken < cellsPerStructure; i++) {
                int x = rnd.nextInt(bp.sizeX());
                int y = rnd.nextInt(bp.sizeY());
                int z = rnd.nextInt(bp.sizeZ());
                int raw = bp.rawAt(x, y, z);
                if (raw == Blueprint.UNTOUCHED) {
                    continue;
                }
                BlockPos pos = new BlockPos(p.x() + x, p.y() + y, p.z() + z);
                if (coveredByAnother(site, pos)) {
                    continue;
                }
                Block expected = StateResolver.parse(bp.paletteKey(raw)).getBlock();
                Block actual = world.getBlockState(pos).getBlock();
                taken++;
                if (expected != actual) {
                    bad++;
                    if (examples.size() < 3) {
                        examples.add(pos.toShortString() + " expected " + Registries.BLOCK.getId(expected) + " got " + Registries.BLOCK.getId(actual));
                    }
                }
            }
            sampled += taken;
            mismatches += bad;
            // fluids and blocks that need support may legitimately update after generation: allow a few percent
            if (taken > 0 && bad * 12 > taken) {
                problems.add(p.id() + ": " + bad + " of " + taken + " sampled cells differ " + examples);
            }
        }
        return new Report(structures, sampled, mismatches, problems);
    }

    private static boolean coveredByAnother(Site site, BlockPos pos) {
        for (Site other : Structures.sites()) {
            if (other != site && other.dimension().equals(site.dimension())
                    && other.placement().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }
}
