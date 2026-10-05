package ua.lewandivka.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.entity.GopnikEntity;
import ua.lewandivka.logic.Bosses;

import java.util.List;

/** Спільна основа всіх босів Хромандівки: бос-бар, прив'язка до арени, захист. */
public abstract class ChromaBossEntity extends HostileEntity {
    protected final ServerBossBar bossBar;
    @Nullable protected BlockPos arena;
    private int vulnerableMsgCooldown;

    protected ChromaBossEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
        super(type, world);
        this.bossBar = new ServerBossBar(getDisplayName(), color, BossBar.Style.NOTCHED_10);
        this.experiencePoints = 120;
        setPersistent();
    }

    /** Ключ локації: garage, aqua, depot, shelter, tower. */
    public abstract String siteKey();

    public void setArena(BlockPos pos) {
        this.arena = pos.toImmutable();
        setPositionTarget(pos, 20);
    }

    @Nullable
    public BlockPos getArena() {
        return arena;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    /** Чи можна зараз ранити боса цим джерелом. */
    protected boolean canBeHurtBy(DamageSource source) {
        return true;
    }

    protected Text shieldMessage() {
        return Text.literal("Захист! Удари не проходять.");
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isSourceCreativePlayer() && !canBeHurtBy(source)) {
            if (source.getAttacker() instanceof PlayerEntity p && vulnerableMsgCooldown <= 0) {
                p.sendMessage(shieldMessage().copy().formatted(Formatting.RED), true);
                vulnerableMsgCooldown = 20;
            }
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (vulnerableMsgCooldown > 0) {
            vulnerableMsgCooldown--;
        }
        bossBar.setPercent(getHealth() / getMaxHealth());
        if (arena != null && age % 40 == 0 && !arena.isWithinDistance(getPos(), 26)) {
            getNavigation().startMovingTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 1.2);
            if (!arena.isWithinDistance(getPos(), 40)) {
                requestTeleport(arena.getX() + 0.5, arena.getY() + 1, arena.getZ() + 3.5);
            }
        }
        bossTick((ServerWorld) getWorld());
    }

    protected abstract void bossTick(ServerWorld world);

    protected List<ServerPlayerEntity> playersNear(double radius) {
        Vec3d c = arena != null ? Vec3d.ofCenter(arena) : getPos();
        return ((ServerWorld) getWorld()).getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(c) < radius * radius);
    }

    protected void say(String line) {
        Text t = Text.literal(getDisplayName().getString() + ": «" + line + "»").formatted(Formatting.LIGHT_PURPLE);
        for (ServerPlayerEntity p : playersNear(48)) {
            p.sendMessage(t, false);
        }
    }

    protected void summonMinions(net.minecraft.entity.EntityType<GopnikEntity> type, int count, int cap) {
        ServerWorld w = (ServerWorld) getWorld();
        int existing = w.getEntitiesByClass(GopnikEntity.class, getBoundingBox().expand(24), e -> true).size();
        for (int i = 0; i < count && existing + i < cap; i++) {
            GopnikEntity g = type.create(w);
            if (g == null) {
                return;
            }
            double a = random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * 5;
            double z = getZ() + Math.sin(a) * 5;
            g.refreshPositionAndAngles(x, getY() + 0.5, z, random.nextFloat() * 360, 0);
            g.setPersistent();
            w.spawnEntity(g);
            w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, x, getY() + 1, z, 10, 0.3, 0.6, 0.3, 0.02);
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient) {
            Bosses.onDefeated((ServerWorld) getWorld(), this);
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (arena != null) {
            nbt.putLong("Arena", arena.asLong());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Arena")) {
            setArena(BlockPos.fromLong(nbt.getLong("Arena")));
        }
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}
