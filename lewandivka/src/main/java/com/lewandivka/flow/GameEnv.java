package com.lewandivka.flow;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.flow.Checkpoints;
import com.lewandivka.core.flow.FlowEnv;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.entity.FareDodgerEntity;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.GopnikEntity;
import com.lewandivka.entity.boss.BossEntity;
import com.lewandivka.entity.vehicle.TramEntity;
import com.lewandivka.entity.npc.CatEntity;
import com.lewandivka.quest.Cinematics;
import com.lewandivka.quest.Dialogues;
import com.lewandivka.quest.QuestInventory;
import com.lewandivka.quest.Story;
import com.lewandivka.quest.Travel;
import com.lewandivka.quest.TimeControl;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.service.Gates;
import com.lewandivka.world.service.Lifts;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The real world behind a dungeon flow: markers become block positions, flow commands become blocks, entities and messages. */
public final class GameEnv implements FlowEnv {

    private static final String TAG = "lew.flow.";

    private final MinecraftServer server;
    private final String structure;
    private final String dimension;
    private PartyScale party = PartyScale.of(1);

    GameEnv(MinecraftServer server, String structure) {
        this.server = server;
        this.structure = structure;
        this.dimension = Structures.dimensionOf(structure);
    }

    void beginEncounter() {
        this.party = PartyService.scaleFor(server, structure);
    }

    ServerWorld level() {
        return Dimensions.world(server, dimension);
    }

    private Marker marker(String name) {
        String id = name.indexOf(':') >= 0 ? name : structure + ":" + name;
        Marker m = Structures.marker(id);
        if (m == null) {
            LewandivkaMod.LOGGER.warn("Flow {} uses the unknown marker {}", structure, id);
        }
        return m;
    }

    private Vec3d stand(String name) {
        Marker m = marker(name);
        return m == null ? Vec3d.ZERO : m.isRegion() ? m.center() : m.stand();
    }

    private Box area() {
        return Structures.bounds(structure).expand(64, 32, 64);
    }

    private List<ServerPlayerEntity> insidePlayers() {
        return PartyService.inStructure(server, structure, Math.max(12, FlowHost.margin(structure)));
    }

    // ------------------------------------------------------------------ FlowEnv

    @Override
    public String structure() {
        return structure;
    }

    @Override
    public long now() {
        return server.getTicks();
    }

    @Override
    public PartyScale party() {
        return party;
    }

    @Override
    public EncounterRecord record() {
        return Campaign.world(server).encounter(structure);
    }

    @Override
    public WorldProgress world() {
        return Campaign.world(server);
    }

    @Override
    public List<UUID> players() {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayerEntity p : insidePlayers()) {
            out.add(p.getUuid());
        }
        return out;
    }

    @Override
    public void station(String marker, String property, String value) {
        Marker m = marker(marker);
        if (m != null && level() != null) {
            BlockOps.setProp(level(), m.pos(), property, value);
        }
    }

    @Override
    public void block(String marker, String blockKey) {
        Marker m = marker(marker);
        if (m != null && level() != null) {
            BlockOps.set(level(), m.pos(), blockKey);
        }
    }

    @Override
    public void gate(String gate, boolean open) {
        if (level() != null) {
            Gates.set(level(), structure, gate, open);
        }
    }

    @Override
    public void moveLift(String lift, boolean up) {
        if (level() != null) {
            Lifts.move(level(), structure, lift, up);
        }
    }

    @Override
    public void flood(String region, boolean fill) {
        Marker m = marker(region);
        if (m == null || level() == null) {
            return;
        }
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = m.x(); x < m.x() + m.sx(); x++) {
            for (int y = m.y(); y < m.y() + m.sy(); y++) {
                for (int z = m.z(); z < m.z() + m.sz(); z++) {
                    p.set(x, y, z);
                    if (fill && level().getBlockState(p).isAir()) {
                        level().setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                    } else if (!fill && level().getBlockState(p).isOf(Blocks.WATER)) {
                        level().setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    @Override
    public void say(UUID player, String langKey, Object... args) {
        Text text = Text.translatable(langKey, args);
        if (player == null) {
            for (ServerPlayerEntity p : insidePlayers()) {
                p.sendMessage(text, true);
            }
        } else {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
            if (p != null) {
                p.sendMessage(text, true);
            }
        }
    }

    @Override
    public void sound(String marker, String soundId) {
        Marker m = marker(marker);
        if (m != null && level() != null && GameSounds.has(soundId)) {
            Vec3d c = m.isRegion() ? m.center() : m.stand();
            level().playSound(null, c.x, c.y, c.z, GameSounds.get(soundId), SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    @Override
    public void soundAt(UUID player, String soundId) {
        soundAt(player, soundId, 1.0f);
    }

    @Override
    public void soundAt(UUID player, String soundId, float volume) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null && GameSounds.has(soundId)) {
            p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), GameSounds.get(soundId), SoundCategory.PLAYERS, volume, 1.0f);
        }
    }

    @Override
    public void fx(String marker, String effect) {
        Marker m = marker(marker);
        if (m == null || level() == null) {
            return;
        }
        Vec3d c = m.isRegion() ? m.center() : m.stand().add(0, 0.8, 0);
        ParticleEffect particle = switch (effect) {
            case "reveal" -> ParticleTypes.END_ROD;
            case "spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "warn" -> ParticleTypes.SMOKE;
            case "food" -> ParticleTypes.HEART;
            case "sludge" -> ParticleTypes.SQUID_INK;
            case "scratch" -> ParticleTypes.CRIT;
            case "rustle" -> ParticleTypes.POOF;
            default -> ParticleTypes.CLOUD;
        };
        double spread = m.isRegion() ? Math.max(0.5, Math.min(m.sx(), m.sz()) / 3.0) : 0.4;
        level().spawnParticles(particle, c.x, c.y, c.z, 14, spread, 0.4, spread, 0.02);
    }

    @Override
    public void spawn(String entity, String marker, int count, String tag) {
        ServerWorld w = level();
        Vec3d at = stand(marker);
        if (w == null || !GameEntities.has(entity)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            EntityType<?> type = GameEntities.type(entity);
            Entity e = type.create(w);
            if (e == null) {
                continue;
            }
            e.refreshPositionAndAngles(at.x + (w.random.nextDouble() - 0.5) * 2.4, at.y, at.z + (w.random.nextDouble() - 0.5) * 2.4, w.random.nextFloat() * 360f, 0);
            if (e instanceof MobEntity mob) {
                mob.initialize(w, w.getLocalDifficulty(mob.getBlockPos()), net.minecraft.entity.SpawnReason.EVENT, null, null);
                mob.setPersistent();
            }
            if (e instanceof GopnikEntity g) {
                g.setMood(GopnikEntity.Mood.HOSTILE);
            }
            if (e instanceof CatEntity cat && tag.startsWith("cat.helper")) {
                cat.setHelper(true);
            }
            e.addCommandTag(TAG + tag);
            w.spawnEntity(e);
        }
    }

    @Override
    public void dropItem(String marker, String item, int count, String tag) {
        ServerWorld w = level();
        Vec3d at = stand(marker);
        if (w == null) {
            return;
        }
        ItemEntity drop = new ItemEntity(w, at.x, at.y + 0.5, at.z, new ItemStack(QuestInventory.item(item), count));
        drop.addCommandTag(TAG + tag);
        drop.setNeverDespawn();
        w.spawnEntity(drop);
    }

    private List<Entity> tagged(String tag) {
        ServerWorld w = level();
        if (w == null) {
            return List.of();
        }
        return w.getOtherEntities(null, area(), e -> e.getCommandTags().contains(TAG + tag));
    }

    @Override
    public void despawn(String tag) {
        for (Entity e : tagged(tag)) {
            e.discard();
        }
    }

    @Override
    public int alive(String tag) {
        int n = 0;
        for (Entity e : tagged(tag)) {
            if (e.isAlive()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int near(String tag, String marker, double radius) {
        Vec3d c = stand(marker);
        int n = 0;
        for (Entity e : tagged(tag)) {
            if (e.isAlive() && e.getPos().squaredDistanceTo(c) <= radius * radius) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void spawnBoss(String entity, String marker) {
        ServerWorld w = level();
        if (w == null || !GameEntities.has(entity) || !w.getEntitiesByType(GameEntities.type(entity), area(), Entity::isAlive).isEmpty()) {
            return;
        }
        spawn(entity, marker, 1, "boss." + entity);
    }

    @Override
    public void despawnBoss(String entity) {
        ServerWorld w = level();
        if (w != null && GameEntities.has(entity)) {
            for (Entity e : w.getEntitiesByType(GameEntities.type(entity), area(), x -> true)) {
                e.discard();
            }
        }
    }

    @Override
    public void attract(String tag, UUID target) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(target);
        if (p == null) {
            return;
        }
        for (Entity e : tagged(tag)) {
            if (e instanceof MobEntity mob && mob.isAlive()) {
                mob.setTarget(p);
                mob.getNavigation().startMovingTo(p, 1.1);
            }
        }
    }

    @Override
    public boolean take(UUID player, String item, int count) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        return p != null && QuestInventory.take(p, item, count);
    }

    @Override
    public boolean has(UUID player, String item) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        return p != null && QuestInventory.has(p, item);
    }

    @Override
    public List<UUID> playersAt(String marker, double radius) {
        Marker m = marker(marker);
        List<UUID> out = new ArrayList<>();
        if (m == null) {
            return out;
        }
        for (ServerPlayerEntity p : insidePlayers()) {
            boolean in = m.isRegion()
                    ? m.box().expand(radius).contains(p.getPos())
                    : p.getPos().squaredDistanceTo(m.stand()) <= radius * radius;
            if (in) {
                out.add(p.getUuid());
            }
        }
        return out;
    }

    @Override
    public double distance(UUID player, String marker) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        Marker m = marker(marker);
        if (p == null || m == null) {
            return 1000.0;
        }
        return p.getPos().distanceTo(m.isRegion() ? m.center() : m.stand());
    }

    private CatEntity cat(String npc) {
        ServerWorld w = level();
        if (w == null || !GameEntities.has(npc)) {
            return null;
        }
        List<? extends Entity> list = w.getEntitiesByType(GameEntities.type(npc), area(), e -> e.isAlive() && e instanceof CatEntity c && !c.helper());
        return list.isEmpty() ? null : (CatEntity) list.get(0);
    }

    @Override
    public void npcMove(String npc, String marker) {
        CatEntity c = cat(npc);
        if (c != null) {
            c.commandMove(stand(marker));
            c.setAvoidPlayers(npc.equals("chinazik") && !level().getEntitiesByClass(BossEntity.class, area(), b -> true).stream().anyMatch(BossEntity::fighting));
        }
    }

    @Override
    public boolean npcAt(String npc, String marker, double radius) {
        CatEntity c = cat(npc);
        return c != null && c.getPos().squaredDistanceTo(stand(marker)) <= radius * radius;
    }

    @Override
    public void npcAnim(String npc, String anim) {
        CatEntity c = cat(npc);
        if (c != null) {
            if (anim.equals("sit") || anim.equals("sleep")) {
                c.pose(anim, 200);
            } else {
                c.play(anim);
            }
        }
    }

    @Override
    public void npcName(String npc, String name) {
        CatEntity c = cat(npc);
        if (c != null) {
            c.setStoryName(name);
        }
    }

    @Override
    public int playersNear(String npc, double radius) {
        CatEntity c = cat(npc);
        if (c == null) {
            return 0;
        }
        int n = 0;
        for (ServerPlayerEntity p : insidePlayers()) {
            if (p.squaredDistanceTo(c) <= radius * radius) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void give(UUID player, String item, int count) {
        if (player == null) {
            for (ServerPlayerEntity p : insidePlayers()) {
                QuestInventory.give(p, item, count);
            }
            return;
        }
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null) {
            QuestInventory.give(p, item, count);
        }
    }

    @Override
    public boolean night() {
        ServerWorld w = level();
        if (w == null) {
            return false;
        }
        long t = w.getTimeOfDay() % 24000;
        return t >= 13000 && t <= 23000;
    }

    @Override
    public void forceNight(boolean on) {
        TimeControl.holdNight(server, dimension, on);
    }

    @Override
    public void resistance(String entity, double factor) {
        ServerWorld w = level();
        if (w != null && GameEntities.has(entity)) {
            for (Entity e : w.getEntitiesByType(GameEntities.type(entity), area(), x -> true)) {
                if (e instanceof FareDodgerEntity f) {
                    f.setResistance(factor);
                }
            }
        }
    }

    @Override
    public boolean bossStation(String entity, String action, int index, UUID player) {
        ServerWorld w = level();
        if (w == null || !GameEntities.has(entity)) {
            return false;
        }
        for (Entity e : w.getEntitiesByType(GameEntities.type(entity), area(), Entity::isAlive)) {
            if (e instanceof BossEntity b && b.station(action, index, player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cinematic(String id) {
        Cinematics.play(server, id);
    }

    @Override
    public void event(String id) {
        Story.event(server, id);
    }

    @Override
    public void stepCounter(int value) {
        Campaign.world(server).setStepCounter(value);
        Campaign.dirty(server);
        com.lewandivka.network.Net.broadcastCampaign(server);
    }

    @Override
    public void checkpoint(int index) {
        EncounterRecord r = record();
        r.setCheckpoint(index);
        String name = Checkpoints.marker(structure, index);
        Marker m = Structures.marker(structure + ":" + (name == null ? "spawn" : name));
        if (m != null && level() != null) {
            for (ServerPlayerEntity p : insidePlayers()) {
                p.setSpawnPoint(level().getRegistryKey(), m.pos().up(), 0.0f, true, false);
            }
        }
        if (com.lewandivka.config.LewandivkaConfig.get().checkpointDebug) {
            for (ServerPlayerEntity p : insidePlayers()) {
                p.sendMessage(Text.translatable("hud.lewandivka.checkpoint"), true);
            }
        }
    }

    // ------------------------------------------------------------------ trams

    private final java.util.Map<String, UUID> trams = new java.util.HashMap<>();

    private TramEntity tram(String tag) {
        UUID id = trams.get(tag);
        if (id == null || level() == null) {
            return null;
        }
        Entity e = level().getEntity(id);
        return e instanceof TramEntity t && t.isAlive() ? t : null;
    }

    /** Exact feet position of a tram waypoint: sky route markers carry their coordinates, other markers stand on the block. */
    private Vec3d waypoint(String name) {
        Marker m = marker(name);
        if (m == null) {
            return Vec3d.ZERO;
        }
        String[] parts = m.data().split(",");
        if (parts.length == 3) {
            try {
                return new Vec3d(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            } catch (NumberFormatException ignored) {
                // an ordinary marker whose data happens to contain commas
            }
        }
        return m.isRegion() ? m.center() : m.stand();
    }

    @Override
    public void tramDrive(String tag, List<String> markers, double speed) {
        if (level() == null || markers.isEmpty()) {
            return;
        }
        List<Vec3d> path = new ArrayList<>();
        for (String m : markers) {
            path.add(waypoint(m));
        }
        TramEntity t = tram(tag);
        if (t == null) {
            t = (TramEntity) GameEntities.<TramEntity>type("sky_tram").create(level());
            if (t == null) {
                return;
            }
            t.addCommandTag(TAG + "tram." + tag);
            t.refreshPositionAndAngles(path.get(0).x, path.get(0).y, path.get(0).z, 0.0f, 0.0f);
            level().getChunk(BlockPos.ofFloored(path.get(0)));
            level().spawnEntity(t);
            trams.put(tag, t.getUuid());
        }
        t.drive(path, speed, false, 0, null);
    }

    @Override
    public boolean tramBusy(String tag) {
        TramEntity t = tram(tag);
        return t != null && t.driving();
    }

    @Override
    public void tramBoard(String tag, List<UUID> players) {
        TramEntity t = tram(tag);
        if (t == null) {
            return;
        }
        for (UUID id : players) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && !p.hasVehicle()) {
                p.startRiding(t, true);
            }
        }
    }

    @Override
    public void tramBoardMobs(String tag, String entity, int count, String mobTag) {
        TramEntity t = tram(tag);
        if (t == null || !GameEntities.has(entity)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Entity e = GameEntities.<Entity>type(entity).create(level());
            if (e == null) {
                continue;
            }
            e.refreshPositionAndAngles(t.getX(), t.getY() + 1.0, t.getZ(), 0.0f, 0.0f);
            if (e instanceof MobEntity mob) {
                mob.initialize(level(), level().getLocalDifficulty(mob.getBlockPos()), net.minecraft.entity.SpawnReason.EVENT, null, null);
                mob.setPersistent();
            }
            if (e instanceof GopnikEntity g) {
                g.setMood(GopnikEntity.Mood.HOSTILE);
            }
            e.addCommandTag(TAG + mobTag);
            level().spawnEntity(e);
            e.startRiding(t, true);
        }
    }

    @Override
    public void tramClear(String tag, String dismountMarker) {
        TramEntity t = tram(tag);
        trams.remove(tag);
        if (t == null) {
            return;
        }
        List<Entity> riders = new ArrayList<>(t.getPassengerList());
        t.removeAllPassengers();
        for (Entity e : riders) {
            if (e instanceof ServerPlayerEntity p && dismountMarker != null) {
                Travel.toMarker(p, dismountMarker.indexOf(':') >= 0 ? dismountMarker : structure + ":" + dismountMarker);
            } else if (!(e instanceof ServerPlayerEntity)) {
                e.discard();
            }
        }
        t.discard();
    }

    @Override
    public void dialogue(String scriptId) {
        Dialogues.play(server, scriptId, insidePlayers(), null);
    }
}
