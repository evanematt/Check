package com.lewandivka.entity.boss;

import com.lewandivka.campaign.PartyService;
import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.entity.LewMob;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.quest.Dialogues;
import com.lewandivka.quest.Encounters;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable boss framework. The fight itself is a pure state machine ({@link BossRules}); this class is the thin shell
 * that supplies the clock and the party, applies the damage factor, shows the boss bar, turns the emitted
 * {@link BossEvent}s into effects, resets cleanly when the party leaves or dies and reports the victory.
 */
public abstract class BossEntity extends LewMob {

    private static final String ADD_TAG = "lew.boss.add";

    protected BossRules rules;
    protected final ServerBossBar bar;
    protected boolean fighting;
    private boolean broken;
    protected PartyScale party = PartyScale.of(1);
    protected Vec3d home;
    private long lastSeen;
    private long nextSlam;
    private long slamAt = -1;
    private long lastShieldSound;

    protected BossEntity(EntityType<? extends BossEntity> type, World world) {
        super(type, world);
        this.bar = new ServerBossBar(getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
        this.bar.setVisible(false);
        setPersistent();
    }

    protected abstract BossRules createRules(long seed);

    /** Decoys (the collector's clones) look like the boss but never run a fight of their own. */
    protected boolean isDecoy() {
        return false;
    }

    /** Structure whose markers the fight uses. */
    protected abstract String structure();

    /** Reacts to the events of the rules (spawns, hazards, animations). Called for every event. */
    protected void onBossEvent(BossEvent e) {
    }

    /** A station of the arena was used (lever, stand, drain, composter ...). */
    public boolean station(String action, int index, UUID player) {
        return false;
    }

    /** The final boss only takes damage from the charge holder. */
    protected boolean mayBeHurtBy(Entity attacker) {
        return true;
    }

    public boolean fighting() {
        return fighting;
    }

    public PartyScale party() {
        return party;
    }

    protected ServerWorld sw() {
        return (ServerWorld) getWorld();
    }

    protected long now() {
        return getWorld().getTime();
    }

    protected Marker marker(String name) {
        return Structures.marker(structure() + ":" + name);
    }

    protected Vec3d at(String markerName) {
        Marker m = marker(markerName);
        return m == null ? getPos() : new Vec3d(m.x() + 0.5, m.y(), m.z() + 0.5);
    }

    protected List<ServerPlayerEntity> participants(double radius) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        Box box = getBoundingBox().expand(radius, 24, radius);
        for (ServerPlayerEntity p : sw().getEntitiesByClass(ServerPlayerEntity.class, box, x -> x.isAlive() && !x.isSpectator())) {
            out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ------------------------------------------------------------------ the fight

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || isDecoy()) {
            return;
        }
        if (home == null) {
            home = getPos();
            rules = createRules(getUuid().getLeastSignificantBits());
        }
        long now = now();
        List<ServerPlayerEntity> near = participants(32);
        if (!fighting) {
            for (ServerPlayerEntity p : near) {
                if (!broken && p.squaredDistanceTo(this) < 22 * 22) {
                    startFight();
                    break;
                }
            }
            return;
        }
        if (near.isEmpty()) {
            if (now - lastSeen > 200) {
                resetFight();
                return;
            }
        } else {
            lastSeen = now;
        }
        for (ServerPlayerEntity p : near) {
            bar.addPlayer(p);
        }
        double hp = getHealth() / Math.max(1.0f, getMaxHealth());
        try {
            for (BossEvent e : rules.tick(now, hp)) {
                handle(e);
            }
        } catch (RuntimeException ex) {
            // a boss that was summoned outside its arena (admin, test) must not take the server down
            com.lewandivka.LewandivkaMod.LOGGER.error("Boss {} failed and was reset", spec.id, ex);
            resetFight();
            broken = true;
            return;
        }
        if (age % 5 == 0) {
            bar.setPercent((float) hp);
            bar.setColor(rules.damageFactor(now) <= 0 ? BossBar.Color.RED : BossBar.Color.GREEN);
        }
        slam(now);
    }

    protected void startFight() {
        party = PartyService.scaleFor(sw().getServer(), structure());
        double max = spec.health * party.bossHealthFactor();
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
        long now = now();
        lastSeen = now;
        fighting = true;
        nextSlam = now + 120;
        bar.setVisible(true);
        for (BossEvent e : rules.start(party, now)) {
            handle(e);
        }
        sw().playSound(null, getBlockPos(), GameSounds.get("boss.spawn"), SoundCategory.HOSTILE, 1.0f, 1.0f);
        if (LewandivkaConfig.get().developerLogs) {
            com.lewandivka.LewandivkaMod.LOGGER.info("{} fight starts, party {}", spec.id, party.size());
        }
    }

    /** Clean reset: everybody died or left. Nothing but the boss is touched; solved puzzles stay solved. */
    public void resetFight() {
        fighting = false;
        rules.reset();
        bar.clearPlayers();
        bar.setVisible(false);
        discardAdds();
        onReset();
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(spec.health);
        setHealth(getMaxHealth());
        setTarget(null);
        if (home != null) {
            refreshPositionAndAngles(home.x, home.y, home.z, getYaw(), 0);
        }
        for (ServerPlayerEntity p : participants(80)) {
            p.sendMessage(Text.translatable("message.lewandivka.encounter_reset"), true);
        }
    }

    /** Subclasses undo their hazards (floods, lifts, clones, leashes). */
    protected void onReset() {
    }

    protected void discardAdds() {
        for (Entity e : sw().getEntitiesByClass(Entity.class, getBoundingBox().expand(90, 40, 90), x -> x.getCommandTags().contains(ADD_TAG + "." + getUuid()))) {
            e.discard();
        }
    }

    /** Marks an entity as belonging to this fight so a reset removes it. */
    protected <T extends Entity> T adopt(T entity) {
        entity.addCommandTag(ADD_TAG + "." + getUuid());
        return entity;
    }

    /** Spawns a mod entity at a marker (or at the boss when the marker is missing). */
    protected Entity spawnAdd(String entityId, String markerName) {
        EntityType<?> type = GameEntities.type(entityId);
        Entity e = type.create(sw());
        if (e == null) {
            return null;
        }
        Vec3d p = markerName == null ? getPos() : at(markerName);
        e.refreshPositionAndAngles(p.x, p.y, p.z, random.nextFloat() * 360f, 0);
        if (e instanceof MobEntity mob) {
            mob.initialize(sw(), sw().getLocalDifficulty(mob.getBlockPos()), net.minecraft.entity.SpawnReason.EVENT, null, null);
        }
        adopt(e);
        sw().spawnEntity(e);
        return e;
    }

    // ------------------------------------------------------------------ events

    /** Applies events returned by an interaction with the rules (lever pulled, drain used ...). */
    protected void apply(java.util.Collection<BossEvent> events) {
        for (BossEvent e : events) {
            handle(e);
        }
    }

    protected void handle(BossEvent e) {
        switch (e.type()) {
            case PHASE -> {
                play("phase");
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.phase"), SoundCategory.HOSTILE, 1.0f, 1.0f);
                shake(6, 30);
            }
            case SHIELD_ON -> {
                play("shield");
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.shield_on"), SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
            case SHIELD_OFF -> sw().playSound(null, getBlockPos(), GameSounds.get("boss.shield_off"), SoundCategory.HOSTILE, 1.0f, 1.0f);
            case VULNERABLE_START -> play("stagger");
            case MESSAGE -> tell(Text.translatable(e.text()));
            case DIALOGUE -> Dialogues.play(sw().getServer(), e.text(), participants(60), this);
            case TELEGRAPH -> {
                play("telegraph");
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.telegraph"), SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
            default -> { }
        }
        onBossEvent(e);
    }

    protected void tell(Text text) {
        for (ServerPlayerEntity p : participants(80)) {
            p.sendMessage(text, true);
        }
    }

    protected void shake(int strength, int ticks) {
        com.lewandivka.network.Net.cameraShake(participants(60), strength, ticks);
    }

    /** The boss' own heavy attack: telegraph, then a slam that hits everyone close (dodge by moving away). */
    private void slam(long now) {
        if (slamAt < 0 && now >= nextSlam && getTarget() != null && squaredDistanceTo(getTarget()) < 36) {
            slamAt = now + party.deadlineTicks(30);
            play("telegraph");
            sw().playSound(null, getBlockPos(), GameSounds.get("boss.telegraph"), SoundCategory.HOSTILE, 1.0f, 1.0f);
            sw().spawnParticles(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), 24, 2.5, 0.1, 2.5, 0.05);
        }
        if (slamAt >= 0 && now >= slamAt) {
            slamAt = -1;
            nextSlam = now + party.deadlineTicks(160);
            play("slam");
            sw().playSound(null, getBlockPos(), GameSounds.get("boss.slam"), SoundCategory.HOSTILE, 1.0f, 1.0f);
            float damage = (float) (spec.attack * 0.9 * party.damageFactor() * LewandivkaConfig.get().bossDifficulty);
            for (ServerPlayerEntity p : participants(8)) {
                if (p.squaredDistanceTo(this) < 16) {
                    p.damage(getDamageSources().mobAttack(this), damage);
                    Vec3d push = p.getPos().subtract(getPos()).normalize().multiply(0.8).add(0, 0.3, 0);
                    p.addVelocity(push.x, push.y, push.z);
                    p.velocityModified = true;
                }
            }
            shake(4, 12);
        }
    }

    // ------------------------------------------------------------------ damage and death

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            return super.damage(source, amount);
        }
        if (!fighting) {
            return false;
        }
        Entity attacker = source.getAttacker();
        if (attacker != null && !mayBeHurtBy(attacker)) {
            return false;
        }
        double factor = rules.damageFactor(now());
        if (factor <= 0.0) {
            long t = now();
            if (t - lastShieldSound > 10) {
                lastShieldSound = t;
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.shield_on"), SoundCategory.HOSTILE, 0.7f, 1.4f);
            }
            return false;
        }
        return super.damage(source, (float) (amount * factor));
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (getWorld().isClient) {
            return;
        }
        if (rules != null && fighting) {
            for (BossEvent e : rules.defeat(now())) {
                handle(e);
            }
        }
        fighting = false;
        bar.clearPlayers();
        discardAdds();
        onReset();
        sw().playSound(null, getBlockPos(), GameSounds.get("boss.defeat"), SoundCategory.HOSTILE, 1.0f, 1.0f);
        Encounters.bossDefeated(sw(), spec.id);
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        bar.clearPlayers();
    }

    /** {@code /kill}, unloading and every other removal go through here (onDeath alone is not enough). */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!getWorld().isClient) {
            try {
                releaseHud();
            } catch (RuntimeException e) {
                // the world may already be shutting down; the bar goes away with the entity anyway
            }
        }
        super.remove(reason);
    }

    /** Takes the boss bar (and whatever the fight put on the screens) away from the players. */
    protected void releaseHud() {
        bar.clearPlayers();
        bar.setVisible(false);
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected String hurtSoundId() {
        return null;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        fighting = false;
    }

    protected BlockPos blockAt(String markerName) {
        Marker m = marker(markerName);
        return m == null ? getBlockPos() : m.pos();
    }
}
