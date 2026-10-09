package com.lewandivka.quest;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.gen.Cars;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.StateResolver;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The cars of the district. The plan has a car at many places; the block-built car is only built in a game without the mod
 * <b>Trep's Cars</b> ({@link Keys#unlessMod}). With the mod, a car of the mod is made at the marker of every place, once (the place is
 * written down in the save, so that a car a player has driven away is not made again), the first time somebody comes near. A world
 * that was made before the mod was installed still has its block-built cars: they are taken away as the car of the mod is made.
 *
 * <p>The mod itself cannot be run by a dedicated server (its main class touches a class that only the client has), so the cars of
 * the mod exist in a game that is played on one computer: a world on its own or opened to the local network. Everywhere else the
 * cars stay built of blocks.</p>
 */
public final class CarPark {

    private CarPark() {
    }

    public static final String KEY = "lewandivka_cars";
    /** A player this close makes the place count. */
    private static final double NEAR = 48.0;

    /** The places that have had their car, in the save of the world. */
    public static final class State extends PersistentState {
        private final Set<String> made = new TreeSet<>();

        public static State fromNbt(NbtCompound nbt) {
            State s = new State();
            NbtList list = nbt.getList("made", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                s.made.add(list.getString(i));
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (String id : made) {
                list.add(NbtString.of(id));
            }
            nbt.put("made", list);
            return nbt;
        }

        public Set<String> made() {
            return made;
        }
    }

    private static volatile List<Cars.Spot> spots;
    /** Places whose car could not be made (the creature is not in the game): said once, not every five seconds. */
    private static final Set<String> FAILED = new HashSet<>();

    public static List<Cars.Spot> spots() {
        List<Cars.Spot> s = spots;
        if (s == null) {
            s = Cars.spots(DistrictPlan.get());
            spots = s;
        }
        return s;
    }

    /** Whether this game has the mod of the cars. */
    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded(Cars.MOD);
    }

    public static State state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, KEY);
    }

    /** Every five seconds: the places with a player near that have no car yet get one. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 100 != 61 || !available()) {
            return;
        }
        ServerWorld world = Dimensions.district(server);
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        State state = state(server);
        for (Cars.Spot spot : spots()) {
            if (state.made().contains(spot.marker()) || FAILED.contains(spot.marker())) {
                continue;
            }
            Vec3d at = new Vec3d(spot.x() + 0.5, spot.y(), spot.z() + 0.5);
            if (!world.getPlayers(p -> p.squaredDistanceTo(at) < NEAR * NEAR).isEmpty()) {
                make(world, state, spot, new int[2]);
            }
        }
    }

    /** The creature of the mod for a body (1 to 3) and a colour; another colour or another body when the mod does not have that one. */
    static EntityType<?> typeFor(int body, String color) {
        for (int b = 0; b < 3; b++) {
            int body2 = 1 + (body - 1 + b) % 3;
            for (String c : new String[] {color, "ww", "wb", "wr"}) {
                Identifier id = new Identifier(Cars.entityId(body2, c));
                if (Registries.ENTITY_TYPE.containsId(id)) {
                    return Registries.ENTITY_TYPE.get(id);
                }
            }
        }
        return null;
    }

    /** Makes the car of a place; {@code counts[0]} counts the cars made, {@code counts[1]} the blocks of block-built cars taken away. */
    private static boolean make(ServerWorld world, State state, Cars.Spot spot, int[] counts) {
        EntityType<?> type = typeFor(Cars.bodyAt(spot.x(), spot.z()), spot.color());
        Entity car = type == null ? null : type.create(world);
        if (car == null) {
            if (FAILED.add(spot.marker())) {
                LewandivkaMod.LOGGER.warn("[cars] no creature of {} for the place {}", Cars.MOD, spot.marker());
            }
            return false;
        }
        counts[1] += clearBlocks(world, spot);
        car.refreshPositionAndAngles(spot.x() + 0.5, spot.y(), spot.z() + 0.5, Cars.yaw(spot.facing()), 0.0f);
        if (car instanceof MobEntity mob) {
            mob.setPersistent();
            mob.setBodyYaw(Cars.yaw(spot.facing()));
            mob.setHeadYaw(Cars.yaw(spot.facing()));
        }
        world.spawnEntity(car);
        state.made().add(spot.marker());
        state.markDirty();
        counts[0]++;
        return true;
    }

    /**
     * Takes away the blocks of the block-built car of a place: only the blocks that are still as the plan made them, so that nothing
     * a player has built there is touched.
     */
    private static int clearBlocks(ServerWorld world, Cars.Spot spot) {
        int colon = spot.marker().indexOf(':');
        Structures.Site site = colon < 0 ? null : Structures.site(spot.marker().substring(0, colon));
        if (site == null) {
            return 0;
        }
        StructurePlacement p = site.placement();
        int[] fp = spot.footprint();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int removed = 0;
        for (int x = fp[0]; x <= fp[2]; x++) {
            for (int z = fp[1]; z <= fp[3]; z++) {
                for (int y = spot.y(); y < spot.y() + Cars.HEIGHT; y++) {
                    String key = p.blueprint().keyAt(x - p.x(), y - p.y(), z - p.z());
                    if (key == null || !Keys.isUnlessMod(key)) {
                        continue;
                    }
                    BlockState expected = StateResolver.parse(Keys.fallback(key));
                    pos.set(x, y, z);
                    if (world.getBlockState(pos) == expected) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /** Development tool: a car at every place now, loading the chunks (what players do by coming near); says how it went. */
    public static String makeAll(MinecraftServer server) {
        List<Cars.Spot> all = spots();
        if (!available()) {
            return "cars: " + all.size() + " places, the mod " + Cars.MOD + " is not in this game: the cars stay built of blocks";
        }
        ServerWorld world = Dimensions.district(server);
        if (world == null) {
            return "cars: the district is not loaded";
        }
        State state = state(server);
        int[] counts = new int[2];
        int already = 0;
        int missing = 0;
        Set<String> types = new TreeSet<>();
        for (Cars.Spot spot : all) {
            if (state.made().contains(spot.marker())) {
                already++;
                continue;
            }
            world.getChunk(spot.x() >> 4, spot.z() >> 4);
            if (!make(world, state, spot, counts)) {
                missing++;
            }
        }
        for (Cars.Spot spot : all) {
            EntityType<?> t = typeFor(Cars.bodyAt(spot.x(), spot.z()), spot.color());
            if (t != null) {
                types.add(Registries.ENTITY_TYPE.getId(t).getPath());
            }
        }
        int standing = 0;
        for (Entity e : world.iterateEntities()) {
            standing += Registries.ENTITY_TYPE.getId(e.getType()).getNamespace().equals(Cars.MOD) ? 1 : 0;
        }
        return "cars: " + all.size() + " places, " + counts[0] + " cars made now, " + already + " stood already, " + missing
                + " could not be made, " + counts[1] + " blocks of cars built of blocks taken away, " + standing + " cars of " + Cars.MOD
                + " in the district now, " + types.size() + " kinds: " + types;
    }

    /** Cars of the mod near a point (the pictures and the checks of the tests). */
    public static List<Entity> carsNear(ServerWorld world, Vec3d at, double radius) {
        return world.getOtherEntities(null, Box.of(at, radius * 2, radius * 2, radius * 2),
                e -> Registries.ENTITY_TYPE.getId(e.getType()).getNamespace().equals(Cars.MOD));
    }
}
