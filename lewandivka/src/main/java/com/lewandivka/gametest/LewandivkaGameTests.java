package com.lewandivka.gametest;

import com.lewandivka.block.SpecBlock;
import com.lewandivka.campaign.NbtStore;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignModel;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ItemSpec;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.registry.ModSounds;
import com.lewandivka.core.registry.SoundSpec;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.util.Ids;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.dimension.Plans;
import com.lewandivka.world.structure.StateResolver;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Fabric GameTests, run headless on a dedicated server by {@code ./gradlew runGametest} (also in CI). They check that
 * everything the data-driven design promises really happens inside Minecraft: every catalog entry is registered, both
 * dimensions generate, the generated structures contain what the blueprints say, and the campaign survives its NBT trip.
 */
public final class LewandivkaGameTests implements FabricGameTest {

    private static void fail(String message) {
        throw new GameTestException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    // ------------------------------------------------------------------ registries

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyCatalogEntryIsRegistered(TestContext context) {
        List<String> problems = new ArrayList<>();
        for (BlockSpec spec : ModBlocks.ALL) {
            Block block = Registries.BLOCK.get(Ids.of(spec.id));
            if (!(block instanceof SpecBlock sb)) {
                problems.add("block " + spec.id + " is not registered");
                continue;
            }
            if (block.getStateManager().getStates().size() != spec.stateCount()) {
                problems.add("block " + spec.id + " has " + block.getStateManager().getStates().size() + " states, expected " + spec.stateCount());
            }
            if (!Registries.ITEM.containsId(Ids.of(spec.id))) {
                problems.add("block item " + spec.id + " is missing");
            }
            check(sb.spec == spec, "spec identity of " + spec.id);
        }
        for (ItemSpec spec : ModItems.ALL) {
            if (!Registries.ITEM.containsId(Ids.of(spec.id))) {
                problems.add("item " + spec.id + " is not registered");
            }
        }
        for (SoundSpec spec : ModSounds.ALL) {
            if (!Registries.SOUND_EVENT.containsId(Ids.of(spec.id()))) {
                problems.add("sound " + spec.id() + " is not registered");
            }
        }
        for (EntitySpec spec : ModEntities.ALL) {
            if (!Registries.ENTITY_TYPE.containsId(Ids.of(spec.id))) {
                problems.add("entity " + spec.id + " is not registered");
            }
        }
        check(problems.isEmpty(), String.join("; ", problems));
        context.complete();
    }

    // ------------------------------------------------------------------ structures and markers

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void everyBlueprintKeyResolves(TestContext context) {
        List<String> problems = new ArrayList<>();
        for (Structures.Site site : Structures.sites()) {
            for (String key : site.placement().blueprint().paletteKeys()) {
                BlockState state = StateResolver.parse(key);
                if (state.isAir() && !key.startsWith("minecraft:air") && !key.startsWith("minecraft:cave_air")) {
                    problems.add(site.placement().id() + ": " + key);
                }
            }
        }
        check(problems.isEmpty(), "unresolvable block keys: " + problems);
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void compassTargetsExist(TestContext context) {
        for (QuestStep step : QuestStep.values()) {
            if (step.compassMarker != null) {
                check(Structures.has(step.compassMarker), "step " + step + " points to missing marker " + step.compassMarker);
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 1200)
    public void bothDimensionsGenerateTheirStructures(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        int checked = 0;
        int mismatches = 0;
        List<String> examples = new ArrayList<>();
        for (String dimension : List.of(Dimensions.DISTRICT_ID, Dimensions.CHROMA_ID)) {
            ServerWorld world = Dimensions.world(server, dimension);
            check(world != null, "dimension " + dimension + " is not loaded");
            int[] spawn = Plans.byDimension(dimension).spawn();
            world.getChunk(spawn[0] >> 4, spawn[2] >> 4);
            BlockState below = world.getBlockState(new BlockPos(spawn[0], spawn[1] - 4, spawn[2]));
            check(!below.isAir(), dimension + ": nothing solid under the spawn point " + spawn[0] + "," + spawn[1] + "," + spawn[2]);
        }
        for (Structures.Site site : Structures.sites()) {
            StructurePlacement p = site.placement();
            Blueprint bp = p.blueprint();
            ServerWorld world = Dimensions.world(server, site.dimension());
            Random rnd = new Random(p.id().hashCode());
            int taken = 0;
            for (int i = 0; i < 600 && taken < 40; i++) {
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
                checked++;
                if (expected != actual) {
                    mismatches++;
                    if (examples.size() < 8) {
                        examples.add(p.id() + "@" + pos.toShortString() + " expected " + Registries.BLOCK.getId(expected) + " got " + Registries.BLOCK.getId(actual));
                    }
                }
            }
        }
        check(checked > 500, "too few sampled cells: " + checked);
        check(mismatches * 25 <= checked, mismatches + " of " + checked + " sampled structure cells differ: " + examples);
        context.complete();
    }

    private static boolean coveredByAnother(Structures.Site site, BlockPos pos) {
        for (Structures.Site other : Structures.sites()) {
            if (other != site && other.dimension().equals(site.dimension())
                    && other.placement().contains(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ campaign state

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void campaignSurvivesItsNbtRoundTrip(TestContext context) {
        CampaignModel model = new CampaignModel();
        UUID player = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
        model.world().forceStep(QuestStep.RG_WINGS);
        model.world().setFlag("kettle.placed");
        model.world().defeatBoss("garage_king");
        model.world().restoreRingFragment();
        model.player(player).grant(Ability.DASH);
        model.player(player).discoverSecret("secret.test");
        model.player(player).collect("cat.test");

        NbtCompound nbt = new NbtCompound();
        model.write(new NbtStore(nbt));
        CampaignModel loaded = CampaignModel.read(new NbtStore(nbt.copy()));

        check(loaded.world().step() == QuestStep.RG_WINGS, "step was lost");
        check(loaded.world().flag("kettle.placed"), "world flag was lost");
        check(loaded.world().bossDefeated("garage_king"), "defeated boss was lost");
        check(loaded.world().ringFragments() == 1, "ring fragment was lost");
        check(loaded.player(player).has(Ability.DASH), "ability was lost");
        check(!loaded.player(player).has(Ability.GLIDER), "ability appeared out of nowhere");
        check(loaded.player(player).hasSecret("secret.test"), "secret was lost");
        check(loaded.player(player).hasCollectible("cat.test"), "collectible was lost");
        check(loaded.dataVersion() == CampaignModel.CURRENT_VERSION, "data version");
        context.complete();
    }
}
