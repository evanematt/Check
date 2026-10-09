package com.lewandivka.gametest;

import com.lewandivka.block.SpecBlock;
import com.lewandivka.campaign.NbtStore;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignModel;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.puzzle.ChasePace;
import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ItemSpec;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.registry.ModSounds;
import com.lewandivka.core.registry.SoundSpec;
import com.lewandivka.core.trade.Wares;
import com.lewandivka.core.world.WorldPlan;
import com.lewandivka.entity.npc.VendorEntity;
import com.lewandivka.quest.NetherGate;
import com.lewandivka.util.Ids;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.dimension.PlanBlockView;
import com.lewandivka.world.dimension.Plans;
import com.lewandivka.world.structure.StateResolver;
import com.lewandivka.world.structure.StructureValidator;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    // ------------------------------------------------------------------ entities

    /**
     * Every creature and vehicle of the catalog must be creatable, spawnable, survive some ticks outside its home
     * structure (an admin can summon anything anywhere) and load again from its own NBT, which is also how the game reads
     * entities from disk. A constructor that calls into half-built state fails here, not in front of a player.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 300)
    public void everyEntityCanBeCreatedTicksAndReloads(TestContext context) {
        ServerWorld world = context.getWorld();
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int n = 0;
        for (EntitySpec spec : ModEntities.ALL) {
            EntityType<?> type = Registries.ENTITY_TYPE.get(Ids.of(spec.id));
            try {
                Entity e = type.create(world);
                if (e == null) {
                    problems.add(spec.id + ": create() returned null");
                    continue;
                }
                BlockPos pos = context.getAbsolutePos(new BlockPos(1 + (n % 6) * 2, 2, 1 + (n / 6) * 2));
                n++;
                e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
                if (!world.spawnEntity(e)) {
                    problems.add(spec.id + ": spawnEntity() refused");
                    continue;
                }
                made.add(e);
                ids.add(spec.id);
            } catch (RuntimeException ex) {
                problems.add(spec.id + ": " + ex);
            }
        }
        check(problems.isEmpty(), "entities that cannot be created: " + problems);
        context.runAtTick(60, () -> {
            List<String> late = new ArrayList<>();
            for (int i = 0; i < made.size(); i++) {
                Entity e = made.get(i);
                try {
                    NbtCompound nbt = new NbtCompound();
                    if (e.saveNbt(nbt)) {
                        Entity back = EntityType.loadEntityWithPassengers(nbt, world, x -> x);
                        if (back == null) {
                            late.add(ids.get(i) + ": could not be loaded from its own NBT");
                        } else {
                            back.discard();
                        }
                    }
                } catch (RuntimeException ex) {
                    late.add(ids.get(i) + ": " + ex);
                }
                e.discard();
            }
            check(late.isEmpty(), "entities that cannot be saved and loaded: " + late);
            context.complete();
        });
    }

    // ------------------------------------------------------------------ springs

    /**
     * A spring pad is a plate without collision (the rider stands in its cell), a hatch is a full block (the rider stands
     * on it): both must throw whatever touches them, the pad also along its arrow.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60)
    public void springsThrowWhateverTouchesThem(TestContext context) {
        for (int x = 1; x <= 7; x++) {
            context.setBlockState(new BlockPos(x, 1, 2), Blocks.STONE.getDefaultState());
        }
        context.setBlockState(new BlockPos(2, 2, 2), StateResolver.parse("lewandivka:spring_pad[facing=east]"));
        context.setBlockState(new BlockPos(6, 2, 2), StateResolver.parse("lewandivka:spring_hatch"));
        PigEntity onPad = context.spawnMob(EntityType.PIG, new BlockPos(2, 2, 2));
        PigEntity onHatch = context.spawnMob(EntityType.PIG, new BlockPos(6, 3, 2));
        double padX = onPad.getX();
        double padY = onPad.getY();
        double hatchY = onHatch.getY();
        context.runAtTick(12, () -> {
            check(onPad.getY() > padY + 6, "the pad did not throw the pig up: y " + padY + " -> " + onPad.getY());
            check(onPad.getX() > padX + 4, "the pad did not throw the pig along its arrow: x " + padX + " -> " + onPad.getX());
            check(onHatch.getY() > hatchY + 8, "the hatch did not throw the pig up: y " + hatchY + " -> " + onHatch.getY());
            onPad.discard();
            onHatch.discard();
            context.complete();
        });
    }

    // ------------------------------------------------------------------ the chase of the Debtor

    /**
     * The chase is only a chase if a sprinting player gains on the Debtor and a strolling one cannot simply walk him down.
     * His pace comes from the multiplier of the navigation call (see {@link ChasePace}); the real navigation runs along a
     * straight road at three multipliers and the measured blocks per second are compared with the model and the design.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "debtor_pace", tickLimit = 160)
    public void theDebtorRunsAtThePaceOfTheChase(TestContext context) {
        ServerWorld world = context.getWorld();
        EntityType<?> type = Registries.ENTITY_TYPE.get(Ids.of("debtor"));
        MobEntity[] runners = new MobEntity[3];
        BlockPos[] goals = new BlockPos[3];
        double[] multipliers = new double[3];
        for (int lane = 0; lane < 3; lane++) {
            int z = lane * 4 + 1;
            for (int x = -1; x <= 40; x++) {
                for (int dz = -1; dz <= 1; dz++) {
                    context.setBlockState(new BlockPos(x, 1, z + dz), Blocks.STONE.getDefaultState());
                    for (int y = 2; y <= 4; y++) {
                        context.setBlockState(new BlockPos(x, y, z + dz), Blocks.AIR.getDefaultState());
                    }
                }
            }
            Entity e = type.create(world);
            check(e instanceof MobEntity, "the debtor is not a mob");
            MobEntity mob = (MobEntity) e;
            BlockPos start = context.getAbsolutePos(new BlockPos(0, 2, z));
            mob.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, -90.0f, 0.0f);
            check(world.spawnEntity(mob), "the debtor was not spawned");
            runners[lane] = mob;
            goals[lane] = context.getAbsolutePos(new BlockPos(40, 2, z));
            double attribute = mob.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            multipliers[lane] = lane == 0 ? ChasePace.multiplierFor(attribute, ChasePace.SOLO)
                    : lane == 1 ? ChasePace.multiplierFor(attribute, ChasePace.PARTY) : 1.0;
        }
        for (int t = 1; t <= 110; t += 5) {
            context.runAtTick(t, () -> {
                for (int lane = 0; lane < 3; lane++) {
                    runners[lane].getNavigation().startMovingTo(goals[lane].getX() + 0.5, goals[lane].getY(), goals[lane].getZ() + 0.5, multipliers[lane]);
                }
            });
        }
        double[] started = new double[3];
        context.runAtTick(25, () -> {
            for (int lane = 0; lane < 3; lane++) {
                started[lane] = runners[lane].getX();
            }
        });
        context.runAtTick(105, () -> {
            double[] pace = new double[3];
            double[] model = new double[3];
            for (int lane = 0; lane < 3; lane++) {
                pace[lane] = (runners[lane].getX() - started[lane]) / 80.0 * 20.0;
                model[lane] = ChasePace.blocksPerSecond(runners[lane].getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED), multipliers[lane]);
                runners[lane].discard();
            }
            String report = String.format(Locale.ROOT, "solo %.2f (model %.2f), party %.2f (model %.2f), full speed %.2f (model %.2f) blocks per second",
                    pace[0], model[0], pace[1], model[1], pace[2], model[2]);
            com.lewandivka.LewandivkaMod.LOGGER.info("Debtor pace: {}", report);
            for (int lane = 0; lane < 3; lane++) {
                check(Math.abs(pace[lane] - model[lane]) <= 0.15 * model[lane] + 0.2, "the model of the pace is wrong: " + report);
            }
            check(pace[0] >= 3.8 && pace[0] <= 5.0, "the lone pursuer's Debtor must be about as fast as a walking player and slower than a sprinting one: " + report);
            check(pace[1] > pace[0] && pace[1] >= 4.4 && pace[1] <= 5.4, "the party's Debtor must be faster, but slower than a sprinting player: " + report);
            context.complete();
        });
    }

    // ------------------------------------------------------------------ the people of the district

    /**
     * Every trader of the market has the goods of his table, in the order of the table, and they are still his goods after his
     * data went to the disk and came back (a better table reaches the traders of an old world, too: the offers are made again).
     * A trade really takes the payment and gives the goods.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void theTradersOfTheMarketHaveTheirGoods(TestContext context) {
        ServerWorld world = context.getWorld();
        List<String> problems = new ArrayList<>();
        for (EntitySpec spec : ModEntities.ALL) {
            if (spec.role != EntitySpec.Role.VENDOR) {
                continue;
            }
            Entity made = Registries.ENTITY_TYPE.get(Ids.of(spec.id)).create(world);
            if (!(made instanceof VendorEntity trader)) {
                problems.add(spec.id + " is not a trader");
                continue;
            }
            List<Wares.Offer> table = Wares.of(spec.id);
            for (int round = 0; round < 2; round++) {
                if (round == 1) {
                    NbtCompound nbt = new NbtCompound();
                    trader.writeCustomDataToNbt(nbt);
                    trader.readCustomDataFromNbt(nbt);
                }
                var offers = trader.getOffers();
                if (offers.size() != table.size()) {
                    problems.add(spec.id + " has " + offers.size() + " offers, the table has " + table.size());
                    continue;
                }
                for (int i = 0; i < table.size(); i++) {
                    Wares.Offer t = table.get(i);
                    var o = offers.get(i);
                    boolean same = o.getOriginalFirstBuyItem().getCount() == t.giveCount()
                            && Registries.ITEM.getId(o.getOriginalFirstBuyItem().getItem()).toString().equals(t.give())
                            && o.getSellItem().getCount() == t.getCount()
                            && Registries.ITEM.getId(o.getSellItem().getItem()).toString().equals(t.get())
                            && o.getMaxUses() == t.maxUses() && o.getSecondBuyItem().isEmpty();
                    if (!same) {
                        problems.add(spec.id + " offer " + i + " is not " + t);
                    }
                }
            }
            // the first offer of the table: what the player pays is taken, what he gets is the sell item
            var first = trader.getOffers().get(0);
            var pay = first.getOriginalFirstBuyItem().copy();
            if (!first.matchesBuyItems(pay, net.minecraft.item.ItemStack.EMPTY) || !first.depleteBuyItems(pay, net.minecraft.item.ItemStack.EMPTY) || !pay.isEmpty()) {
                problems.add(spec.id + ": the payment of the first offer is not taken");
            }
            trader.discard();
        }
        check(problems.isEmpty(), "traders of the market: " + problems);
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

    /** A frame of obsidian becomes a Nether portal with the game's own portal code (NetherGate uses it in the district, where the game does not). */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void aFrameOfObsidianBecomesANetherPortal(TestContext context) {
        for (int dx = 0; dx <= 3; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                context.setBlockState(new BlockPos(dx, 1 + dy, 2), frame ? Blocks.OBSIDIAN.getDefaultState() : Blocks.AIR.getDefaultState());
            }
        }
        ServerWorld world = context.getWorld();
        check(NetherGate.light(world, context.getAbsolutePos(new BlockPos(1, 2, 2)), Direction.NORTH), "the frame of obsidian was not lit");
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                check(world.getBlockState(context.getAbsolutePos(new BlockPos(dx, 1 + dy, 2))).isOf(Blocks.NETHER_PORTAL), "no portal block at " + dx + "," + dy);
            }
        }
        context.complete();
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 1200)
    public void chunkPainterBuildsEveryStructureWhereTheBlueprintSaysAndTheSpawnIsSolid(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        java.util.Map<String, BlockView> views = new java.util.HashMap<>();
        for (String dimension : List.of(Dimensions.DISTRICT_ID, Dimensions.CHROMA_ID)) {
            WorldPlan plan = Plans.byDimension(dimension);
            PlanBlockView view = new PlanBlockView(plan, server.getRegistryManager());
            views.put(dimension, view);
            int[] spawn = plan.spawn();
            BlockState below = view.getBlockState(new BlockPos(spawn[0], spawn[1] - 4, spawn[2]));
            check(!below.isAir(), dimension + ": nothing solid under the spawn point " + spawn[0] + "," + spawn[1] + "," + spawn[2]);
            // the first arrival must not put anybody inside a block
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos at = new BlockPos(spawn[0], spawn[1] + dy, spawn[2]);
                BlockState inside = view.getBlockState(at);
                check(inside.getCollisionShape(view, at).isEmpty(), dimension + ": the spawn point is inside " + Registries.BLOCK.getId(inside.getBlock()) + " at " + at.toShortString());
            }
        }
        StructureValidator.Report report = StructureValidator.validate(views::get, 40);
        check(report.sampled() > 500, "too few sampled cells: " + report.summary());
        check(report.ok(), report.summary() + " " + report.problems());
        context.complete();
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
