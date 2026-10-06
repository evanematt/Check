package com.lewandivka.entity.boss;

import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.boss.ColorlessHeadRules;
import com.lewandivka.core.boss.ColorlessHeadRules.Segment;
import com.lewandivka.core.puzzle.PlatformDecay;
import com.lewandivka.core.puzzle.ZonePlanner.Zone;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.npc.CatEntity;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Colorless Head, the final boss: the Chromatic Charge, decaying platforms, an exam of the whole campaign and the
 * final moment with the two cats. See {@link ColorlessHeadRules} for the rules; this class makes them visible.
 */
public class ColorlessHeadEntity extends BossEntity {

    private static final int PLATFORMS = ColorlessHeadRules.PLATFORMS;
    private static final double[] VALIDATOR_PLATFORMS = {1, 4};

    private final List<List<BlockPos>> cells = new ArrayList<>();
    private boolean decoy;
    private int decoyIndex = -1;
    private final List<UUID> decoys = new ArrayList<>();
    private int calledIndex = -1;
    private UUID called;
    private final List<double[]> rings = new ArrayList<>();      // {x, z, radius, speed, max, damage, y}
    private final List<Zone> flooded = new ArrayList<>();
    private long lastHolderHint;

    public ColorlessHeadEntity(EntityType<? extends ColorlessHeadEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected BossRules createRules(long seed) {
        return new ColorlessHeadRules(seed);
    }

    @Override
    protected String structure() {
        return "tower";
    }

    private ColorlessHeadRules head() {
        return (ColorlessHeadRules) rules;
    }

    @Override
    protected boolean isDecoy() {
        return decoy;
    }

    // ------------------------------------------------------------------ stations and the charge

    @Override
    public boolean station(String action, int index, UUID player) {
        if (!fighting) {
            return false;
        }
        if (action.equals("relay") && index >= 0 && index < ColorlessHeadRules.RELAYS) {
            apply(head().throwToRelay(player, index, now()));
            return true;
        }
        return false;
    }

    /** The holder pressed Q or the pass key: hand the charge on, towards the aimed player or the nearest other one. */
    public boolean passCharge(ServerPlayerEntity from, UUID aimed) {
        if (!fighting || !head().relay().isHolder(from.getUuid())) {
            return false;
        }
        UUID to = aimed;
        if (to == null || to.equals(from.getUuid())) {
            ServerPlayerEntity best = null;
            for (ServerPlayerEntity p : participants(40)) {
                if (p != from && (best == null || p.squaredDistanceTo(from) < best.squaredDistanceTo(from))) {
                    best = p;
                }
            }
            to = best == null ? null : best.getUuid();
        }
        if (to != null) {
            apply(head().passCharge(from.getUuid(), to, now()));
            return true;
        }
        // alone: throw it into the nearest relay device
        double bestD = 12 * 12;
        int relay = -1;
        for (int i = 0; i < ColorlessHeadRules.RELAYS; i++) {
            double d = from.squaredDistanceTo(at(i == 0 ? "relay_a" : "relay_b"));
            if (d < bestD) {
                bestD = d;
                relay = i;
            }
        }
        if (relay >= 0) {
            apply(head().throwToRelay(from.getUuid(), relay, now()));
            return true;
        }
        from.sendMessage(Text.translatable("message.lewandivka.charge.relay"), true);
        return false;
    }

    public UUID holder() {
        return head().relay().holder();
    }

    @Override
    protected boolean mayBeHurtBy(Entity attacker) {
        if (attacker instanceof ServerPlayerEntity p && head().canDamage(p.getUuid(), now())) {
            return true;
        }
        if (attacker instanceof ServerPlayerEntity p && now() - lastHolderHint > 60) {
            lastHolderHint = now();
            p.sendMessage(Text.translatable("message.lewandivka.charge.only_holder"), true);
        }
        return false;
    }

    // ------------------------------------------------------------------ the tick

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || !fighting || decoy) {
            return;
        }
        long now = now();
        List<ServerPlayerEntity> ps = participants(40);
        ps.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(this)));
        List<UUID> eligible = new ArrayList<>();
        for (ServerPlayerEntity p : ps) {
            eligible.add(p.getUuid());
        }
        apply(head().tickCharge(now, eligible));
        UUID holder = head().relay().holder();
        if (age % 4 == 0) {
            Net.charge(ps, holder, head().relay().fraction(now), head().charged());
        }
        if (holder != null && age % 6 == 0) {
            ServerPlayerEntity h = sw().getServer().getPlayerManager().getPlayer(holder);
            if (h != null) {
                sw().spawnParticles(ParticleTypes.END_ROD, h.getX(), h.getY() + 2.2, h.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
                h.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20, 0, false, false));
            }
        }
        sensors(ps, now);
        tickRings();
        tickSectors(ps);
    }

    /** Platforms the party stands on act as lift pads and validator platforms during the exam. */
    private void sensors(List<ServerPlayerEntity> ps, long now) {
        Segment seg = head().segment();
        if (seg == Segment.GARAGE) {
            for (int i = 0; i < ColorlessHeadRules.LIFTS; i++) {
                Marker m = marker("plat_" + (2 + 2 * i));
                if (m != null && ps.stream().anyMatch(p -> onPlatform(p, m))) {
                    apply(head().liftOccupied(i, now));
                }
                if (m != null && head().liftLit(i, now) && age % 10 == 0) {
                    Vec3d c = m.center();
                    sw().spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 4, 1.5, 0.1, 1.5, 0.02);
                }
            }
        } else if (seg == Segment.TICKET && called != null) {
            ServerPlayerEntity p = sw().getServer().getPlayerManager().getPlayer(called);
            if (p != null) {
                for (double v : VALIDATOR_PLATFORMS) {
                    Marker m = marker("plat_" + (int) v);
                    if (m != null && onPlatform(p, m)) {
                        apply(head().ticketValidated(calledIndex, now));
                        called = null;
                        break;
                    }
                }
            }
        }
    }

    private static boolean onPlatform(ServerPlayerEntity p, Marker m) {
        return p.isOnGround() && p.getX() >= m.x() && p.getX() < m.x() + m.sx() && p.getZ() >= m.z() && p.getZ() < m.z() + m.sz()
                && Math.abs(p.getY() - (m.y() + 1)) < 1.5;
    }

    // ------------------------------------------------------------------ events

    @Override
    protected void onBossEvent(BossEvent e) {
        switch (e.type()) {
            case CHARGE_GRANT -> chargeMessage(e.text(), "message.lewandivka.charge.got", "relay.active");
            case CHARGE_PASS -> chargeMessage(e.text(), "message.lewandivka.charge.passed", "relay.pass");
            case CHARGE_RETURN -> chargeMessage(e.text(), "message.lewandivka.charge.passed", "relay.active");
            case CHARGE_RELAY -> {
                tell(Text.translatable("message.lewandivka.charge.relay"));
                sw().playSound(null, blockAt(e.a() == 0 ? "relay_a" : "relay_b"), GameSounds.get("relay.pass"), SoundCategory.BLOCKS, 1.0f, 1.0f);
                BlockOps.setProp(sw(), blockAt(e.a() == 0 ? "relay_a" : "relay_b"), "active", "true");
            }
            case CHARGE_OVERLOAD -> overload(e.text());
            case TELEGRAPH -> telegraph(e.a(), e.b());
            case ATTACK -> attack(e.a());
            case PLATFORM_DECAY -> {
                setStage(e.a(), e.b());
                sw().playSound(null, getBlockPos(), GameSounds.get(e.b() >= 3 ? "platform.collapse" : "platform.crumble"), SoundCategory.BLOCKS, 1.2f, 1.0f);
                tell(Text.translatable("message.lewandivka.platform.crumbling"));
            }
            case PLATFORM_REGROW -> {
                if (e.a() < 0) {
                    for (int i = 0; i < PLATFORMS; i++) {
                        setStage(i, 0);
                    }
                } else {
                    setStage(e.a(), 1);
                }
            }
            case SEGMENT_START -> segmentStart(Segment.values()[e.a()], e.b());
            case SEGMENT_DONE -> {
                tell(Text.translatable(e.b() == 1 ? "message.lewandivka.quest_done" : "message.lewandivka.encounter_reset"));
                called = null;
                clearDecoys();
            }
            case ZONE_WARN -> {
                Zone z = Zone.values()[Math.floorMod(e.a(), Zone.values().length)];
                tell(Text.translatable("message.lewandivka.vortex.zone", Text.translatable("hud.lewandivka.zone." + z.color)));
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.telegraph"), SoundCategory.HOSTILE, 1.0f, 0.8f);
            }
            case ZONE_FLOOD -> flooded.add(Zone.values()[Math.floorMod(e.a(), Zone.values().length)]);
            case ZONE_CLEAR -> flooded.clear();
            case TICKET_CALL -> {
                List<ServerPlayerEntity> ps = participants(40);
                if (!ps.isEmpty()) {
                    ServerPlayerEntity target = ps.get(Math.floorMod(e.a(), ps.size()));
                    called = target.getUuid();
                    calledIndex = e.a();
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, e.b(), 0, false, false));
                    target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.translatable("message.lewandivka.conductor.call")));
                }
            }
            case TICKET_FAIL -> {
                ServerPlayerEntity p = called == null ? null : sw().getServer().getPlayerManager().getPlayer(called);
                if (p != null) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                }
                called = null;
            }
            case CLONES -> spawnDecoys(e.a());
            case CLONES_GONE -> {
                if (e.b() == 1) {
                    popDecoy(e.a());
                } else {
                    clearDecoys();
                }
            }
            case REAL_BOSS_HINT -> {
                for (CatEntity c : cats()) {
                    c.play("point");
                    c.getLookControl().lookAt(getX(), getEyeY(), getZ());
                }
                sw().spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + spec.height + 0.5, getZ(), 8, 0.2, 0.4, 0.2, 0.0);
            }
            case STEAL_CHARGE -> {
                play("phase");
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.roar"), SoundCategory.HOSTILE, 1.5f, 0.6f);
                shake(8, 40);
            }
            case MONOCHROME -> Net.monochrome(participants(80), e.a() == 1);
            case CATS_ENTER -> {
                for (CatEntity c : cats()) {
                    c.commandMove(at(c.isChinazik() ? "cat_entry_a" : "cat_entry_b").add(0, 0, 3));
                }
            }
            case CATS_PASS_CHARGE -> {
                for (CatEntity c : cats()) {
                    boolean knocker = e.a() == 0 ? c.isChinazik() : !c.isChinazik();
                    if (knocker) {
                        c.play(e.a() == 0 ? "scratch" : "point");
                        sw().playSound(null, c.getBlockPos(), GameSounds.get(e.a() == 0 ? "cat.hiss" : "cat.meow_high"), SoundCategory.NEUTRAL, 1.2f, 1.0f);
                        sw().spawnParticles(ParticleTypes.END_ROD, c.getX(), c.getY() + 0.6, c.getZ(), 16, 0.3, 0.3, 0.3, 0.08);
                    }
                }
            }
            case FINAL_WINDOW -> {
                tell(Text.translatable("message.lewandivka.final.mono"));
                sw().playSound(null, getBlockPos(), GameSounds.get("chroma.arrive"), SoundCategory.HOSTILE, 1.5f, 1.0f);
                bar.setName(Text.translatable("entity.lewandivka.colorless_head"));
            }
            case DEFEATED -> {
                Net.monochrome(participants(80), false);
                clearDecoys();
                restorePlatforms();
            }
            default -> { }
        }
    }

    private void chargeMessage(String uuid, String key, String sound) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }
        ServerPlayerEntity p = sw().getServer().getPlayerManager().getPlayer(UUID.fromString(uuid));
        if (p != null) {
            Text msg = key.endsWith(".got") ? Text.translatable(key, ColorlessHeadRules.relayTicks() / 20) : Text.translatable(key, p.getName());
            p.sendMessage(msg, true);
            sw().playSound(null, p.getBlockPos(), GameSounds.get(sound), SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void overload(String uuid) {
        ServerPlayerEntity p = uuid == null || uuid.isEmpty() ? null : sw().getServer().getPlayerManager().getPlayer(UUID.fromString(uuid));
        if (p == null) {
            return;
        }
        sw().playSound(null, p.getBlockPos(), GameSounds.get("charge.overload"), SoundCategory.PLAYERS, 1.2f, 1.0f);
        sw().spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
        // an overload hurts but never kills
        if (p.getHealth() > 6.0f) {
            p.damage(getDamageSources().magic(), 4.0f);
        }
        p.sendMessage(Text.translatable("message.lewandivka.charge.overload"), true);
    }

    private List<CatEntity> cats() {
        return sw().getEntitiesByClass(CatEntity.class, getBoundingBox().expand(70, 30, 70), c -> true);
    }

    // ------------------------------------------------------------------ attacks

    private void telegraph(int kind, int ticks) {
        Vec3d c = getPos();
        sw().playSound(null, getBlockPos(), GameSounds.get("boss.telegraph"), SoundCategory.HOSTILE, 1.0f, 1.0f);
        if (kind == ColorlessHeadRules.ATTACK_BEAM) {
            UUID h = holder();
            ServerPlayerEntity p = h == null ? null : sw().getServer().getPlayerManager().getPlayer(h);
            if (p != null) {
                Vec3d dir = p.getPos().subtract(c).normalize();
                for (int i = 2; i < 30; i += 2) {
                    Vec3d pt = c.add(0, 1.5, 0).add(dir.multiply(i));
                    sw().spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
        } else {
            double r = kind == ColorlessHeadRules.ATTACK_SLAM ? 7 : 20;
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                sw().spawnParticles(ParticleTypes.CRIT, c.x + Math.cos(a) * r * 0.4, c.y + 0.2, c.z + Math.sin(a) * r * 0.4, 1, 0, 0, 0, 0);
            }
        }
        play("telegraph");
    }

    private void attack(int kind) {
        Vec3d c = getPos();
        float dmg = (float) (spec.attack * 0.7 * party.damageFactor() * com.lewandivka.config.LewandivkaConfig.get().bossDifficulty);
        sw().playSound(null, getBlockPos(), GameSounds.get("boss.slam"), SoundCategory.HOSTILE, 1.2f, 1.0f);
        switch (kind) {
            case ColorlessHeadRules.ATTACK_BEAM -> {
                UUID h = holder();
                ServerPlayerEntity target = h == null ? null : sw().getServer().getPlayerManager().getPlayer(h);
                play("attack");
                if (target != null) {
                    Vec3d dir = target.getPos().subtract(c).normalize();
                    for (ServerPlayerEntity p : participants(40)) {
                        Vec3d rel = p.getPos().subtract(c);
                        double along = rel.dotProduct(dir);
                        double off = rel.subtract(dir.multiply(along)).length();
                        if (along > 0 && off < 1.6) {
                            p.damage(getDamageSources().magic(), dmg);
                        }
                    }
                }
            }
            case ColorlessHeadRules.ATTACK_SLAM -> {
                play("slam");
                rings.add(new double[] {c.x, c.z, 1, 0.9, 8, dmg, c.y});
            }
            default -> {
                play("attack");
                rings.add(new double[] {c.x, c.z, 1, 0.55, 26, dmg, c.y});
            }
        }
        shake(5, 14);
    }

    /** Expanding rings: they hurt everyone whose feet are still on the floor when the ring passes, so jumping dodges them. */
    private void tickRings() {
        for (int i = rings.size() - 1; i >= 0; i--) {
            double[] r = rings.get(i);
            double prev = r[2];
            r[2] += r[3];
            for (ServerPlayerEntity p : participants(40)) {
                double d = Math.hypot(p.getX() - r[0], p.getZ() - r[1]);
                if (d >= prev - 0.6 && d <= r[2] + 0.6 && p.getY() - r[6] < 1.1 && p.getY() - r[6] > -3) {
                    p.damage(getDamageSources().magic(), (float) r[5]);
                    Vec3d push = p.getPos().subtract(r[0], p.getY(), r[1]).normalize().multiply(0.5).add(0, 0.25, 0);
                    p.addVelocity(push.x, push.y, push.z);
                    p.velocityModified = true;
                }
            }
            if (age % 2 == 0) {
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8;
                    sw().spawnParticles(ParticleTypes.CLOUD, r[0] + Math.cos(a) * r[2], r[6] + 0.2, r[1] + Math.sin(a) * r[2], 1, 0, 0, 0, 0);
                }
            }
            if (r[2] >= r[4]) {
                rings.remove(i);
            }
        }
    }

    /** The quarters flooded by the aquapark segment hurt (never below three hearts). */
    private void tickSectors(List<ServerPlayerEntity> ps) {
        if (flooded.isEmpty() || age % 20 != 0) {
            return;
        }
        Vec3d center = at("arena_center");
        for (ServerPlayerEntity p : ps) {
            double angle = Math.atan2(p.getZ() - center.z, p.getX() - center.x);
            int quarter = (int) Math.floorMod(Math.round(Math.floor((angle + Math.PI) / (Math.PI / 2))), 4);
            for (Zone z : flooded) {
                if (z.ordinal() == quarter && p.isOnGround()) {
                    if (p.getHealth() > 6.0f) {
                        p.damage(getDamageSources().magic(), 2.0f);
                    }
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    sw().spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.4, 0.1, 0.4, 0.02);
                }
            }
        }
        sw().spawnParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 8, center.z, 20, 14, 0.5, 14, 0.0);
    }

    // ------------------------------------------------------------------ platforms

    private void scanPlatforms() {
        cells.clear();
        for (int i = 0; i < PLATFORMS; i++) {
            List<BlockPos> list = new ArrayList<>();
            Marker m = marker("plat_" + i);
            if (m != null) {
                BlockPos.Mutable p = new BlockPos.Mutable();
                for (int x = m.x(); x < m.x() + m.sx(); x++) {
                    for (int z = m.z(); z < m.z() + m.sz(); z++) {
                        p.set(x, m.y(), z);
                        if (sw().getBlockState(p).getBlock() == com.lewandivka.block.GameBlocks.get("crumbling_platform")) {
                            list.add(p.toImmutable());
                        }
                    }
                }
            }
            cells.add(list);
        }
    }

    private void setStage(int platform, int stage) {
        if (cells.isEmpty()) {
            scanPlatforms();
        }
        if (platform < 0 || platform >= cells.size()) {
            return;
        }
        for (BlockPos pos : cells.get(platform)) {
            if (stage >= PlatformDecay.COLLAPSED) {
                sw().setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            } else if (sw().getBlockState(pos).isAir()) {
                sw().setBlockState(pos, com.lewandivka.world.structure.StateResolver.parse("lewandivka:crumbling_platform[stage=" + stage + "]"), Block.NOTIFY_LISTENERS);
            } else {
                BlockOps.setProp(sw(), pos, "stage", Integer.toString(stage));
            }
        }
    }

    private void restorePlatforms() {
        if (cells.isEmpty()) {
            return;
        }
        for (int i = 0; i < cells.size(); i++) {
            setStage(i, 0);
        }
    }

    // ------------------------------------------------------------------ segments and decoys

    private void segmentStart(Segment seg, int ticks) {
        String key = "message.lewandivka.head.seg." + seg.name().toLowerCase(java.util.Locale.ROOT);
        tell(Text.translatable(key));
        play("phase");
    }

    private void spawnDecoys(int total) {
        clearDecoys();
        Vec3d c = at("arena_center");
        int real = head().realClone();
        decoyIndex = real;
        for (int i = 0; i < total; i++) {
            double a = i * 2 * Math.PI / total;
            Vec3d spot = c.add(Math.cos(a) * 9, 7, Math.sin(a) * 9);
            if (i == real) {
                teleport(spot.x, spot.y, spot.z);
                continue;
            }
            ColorlessHeadEntity d = new ColorlessHeadEntity(GameEntities.type("colorless_head"), sw());
            d.decoy = true;
            d.decoyIndex = i;
            d.setNoGravity(true);
            d.refreshPositionAndAngles(spot.x, spot.y, spot.z, 0, 0);
            d.setHealth(1.0f);
            adopt(d);
            sw().spawnEntity(d);
            decoys.add(d.getUuid());
        }
    }

    private void popDecoy(int index) {
        for (UUID id : new ArrayList<>(decoys)) {
            Entity e = sw().getEntity(id);
            if (e instanceof ColorlessHeadEntity d && d.decoyIndex == index) {
                sw().spawnParticles(ParticleTypes.POOF, d.getX(), d.getY() + 1, d.getZ(), 20, 0.5, 1.0, 0.5, 0.02);
                d.discard();
                decoys.remove(id);
            }
        }
    }

    private void clearDecoys() {
        for (UUID id : decoys) {
            Entity e = sw().getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        decoys.clear();
        if (head() != null && fighting && spec != null && home != null && head().segment() != Segment.CLONES) {
            setNoGravity(false);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (decoy) {
            if (!getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity) {
                // the decoys are owned by the real boss: report the hit to it
                for (Entity e : sw().getEntitiesByClass(ColorlessHeadEntity.class, getBoundingBox().expand(60, 30, 60), x -> !x.decoy && x.fighting)) {
                    ((ColorlessHeadEntity) e).decoyHit(decoyIndex);
                }
            }
            return false;
        }
        if (fighting && head().segment() == Segment.CLONES && source.getAttacker() instanceof ServerPlayerEntity) {
            // hitting the real one during the clone segment ends it
            apply(head().cloneHit(head().realClone(), now()));
            return false;
        }
        return super.damage(source, amount);
    }

    private void decoyHit(int index) {
        if (fighting) {
            apply(head().cloneHit(index, now()));
        }
    }

    @Override
    protected void onReset() {
        clearDecoys();
        restorePlatforms();
        rings.clear();
        flooded.clear();
        called = null;
        Net.monochrome(participants(80), false);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("decoy", decoy);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        decoy = nbt.getBoolean("decoy");
        if (decoy) {
            discard();
        }
    }
}
