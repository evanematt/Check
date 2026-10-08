package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.core.scale.Population;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.GopnikEntity;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * The night population of the district. The entity types are not spawned by the game's own spawner (they are not
 * monsters of a biome), so this service creates them: roaming hostile gopniks around the players (introduced by the first
 * night) and the neutral "got any seeds?" groups at the four marked corners. At dawn everybody who is not near a player
 * goes home.
 */
public final class DistrictSpawns {

    private static final String TAG = "lew.night";
    private static final String HOSTILE = "lew.night.hostile";
    private static final String GROUP = "lew.night.group.";
    private static final long[] GROUP_READY = new long[Population.NEUTRAL_GROUPS];
    private static int counter;

    private DistrictSpawns() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DistrictSpawns::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 60 != 11) {
            return;
        }
        ServerWorld world = Dimensions.district(server);
        if (world == null || !Campaign.world(server).started()) {
            return;
        }
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator() && p.isAlive());
        boolean night = Population.night(server.getOverworld().getTimeOfDay());
        List<? extends GopnikEntity> mine = world.getEntitiesByType(TypeFilter.instanceOf(GopnikEntity.class), e -> e.isAlive() && e.getCommandTags().contains(TAG));
        if (!night) {
            for (GopnikEntity g : mine) {
                if (players.stream().noneMatch(p -> p.squaredDistanceTo(g) < 20 * 20)) {
                    g.discard();
                }
            }
            return;
        }
        if (players.isEmpty()) {
            return;
        }
        PartyScale party = PartyService.scale(server);
        var step = Campaign.step(server);
        int cap = Population.hostileCap(step, party);
        long hostile = mine.stream().filter(g -> g.getCommandTags().contains(HOSTILE)).count();
        for (int i = 0; i < 2 && hostile < cap; i++) {
            ServerPlayerEntity target = players.get(world.random.nextInt(players.size()));
            if (spawnHostile(world, target)) {
                hostile++;
            }
        }
        if (Population.neutralGroups(step)) {
            for (int i = 0; i < Population.NEUTRAL_GROUPS; i++) {
                groups(world, i, mine, players, party, server.getTicks());
            }
        }
    }

    private static boolean spawnHostile(ServerWorld world, ServerPlayerEntity near) {
        double angle = world.random.nextDouble() * Math.PI * 2;
        double radius = 22 + world.random.nextDouble() * 12;
        int x = (int) Math.floor(near.getX() + Math.cos(angle) * radius);
        int z = (int) Math.floor(near.getZ() + Math.sin(angle) * radius);
        if (Structures.structureAt(Dimensions.DISTRICT_ID, new BlockPos(x, near.getBlockY(), z)) != null && world.random.nextInt(3) != 0) {
            return false;
        }
        GopnikEntity g = create(world, Population.hostileKind(counter++), x, z, GopnikEntity.Mood.HOSTILE);
        if (g == null) {
            return false;
        }
        g.addCommandTag(HOSTILE);
        world.spawnEntity(g);
        return true;
    }

    private static void groups(ServerWorld world, int index, List<? extends GopnikEntity> mine, List<ServerPlayerEntity> players, PartyScale party, long now) {
        Marker m = Structures.marker("district:neutral_" + index);
        if (m == null || now < GROUP_READY[index]) {
            return;
        }
        Vec3d at = m.stand();
        if (players.stream().noneMatch(p -> p.squaredDistanceTo(at) < 40 * 40)) {
            return;
        }
        String tag = GROUP + index;
        if (mine.stream().anyMatch(g -> g.getCommandTags().contains(tag))) {
            return;
        }
        GROUP_READY[index] = now + 4800;
        for (int i = 0; i < Population.neutralGroupSize(party); i++) {
            GopnikEntity g = create(world, i == 0 ? "gopnik" : world.random.nextBoolean() ? "gopnik" : "seed_thrower",
                    m.x() + world.random.nextInt(5) - 2, m.z() + world.random.nextInt(5) - 2, GopnikEntity.Mood.NEUTRAL);
            if (g != null) {
                g.addCommandTag(tag);
                world.spawnEntity(g);
            }
        }
    }

    private static GopnikEntity create(ServerWorld world, String id, int x, int z, GopnikEntity.Mood mood) {
        if (!GameEntities.has(id)) {
            return null;
        }
        world.getChunk(new BlockPos(x, 64, z));
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down()) || !world.getFluidState(feet).isEmpty()) {
            return null;
        }
        EntityType<?> type = GameEntities.type(id);
        Entity e = type.create(world);
        if (!(e instanceof GopnikEntity g)) {
            return null;
        }
        g.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0.0f);
        g.initialize(world, world.getLocalDifficulty(feet), SpawnReason.EVENT, null, null);
        g.setPersistent();
        g.setMood(mood);
        g.addCommandTag(TAG);
        return g;
    }
}
