package com.lewandivka.entity;

import com.lewandivka.core.registry.EntitySpec.Role;
import com.lewandivka.quest.Encounters;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/** Fare dodgers of the last tram; the leader is shielded (damage resistance) until the validators are punched. */
public class FareDodgerEntity extends LewMob {

    private double resistance = 1.0;
    private final ServerBossBar bar;

    public FareDodgerEntity(EntityType<? extends FareDodgerEntity> type, World world) {
        super(type, world);
        this.bar = spec != null && spec.role == Role.FARE_LEADER
                ? new ServerBossBar(getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_6) : null;
    }

    public boolean isLeader() {
        return spec.role == Role.FARE_LEADER;
    }

    /** 1 = normal damage, below 1 = shielded. */
    public void setResistance(double factor) {
        this.resistance = factor;
        if (bar != null) {
            bar.setColor(factor < 1.0 ? BossBar.Color.RED : BossBar.Color.GREEN);
        }
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return super.damage(source, (float) (amount * resistance));
    }

    @Override
    public void tick() {
        super.tick();
        if (bar != null && !getWorld().isClient && age % 10 == 0) {
            bar.setPercent(getHealth() / getMaxHealth());
            for (ServerPlayerEntity p : bar.getPlayers().stream().toList()) {
                if (p.squaredDistanceTo(this) > 80 * 80) {
                    bar.removePlayer(p);
                }
            }
            for (PlayerEntity p : getWorld().getPlayers()) {
                if (p instanceof ServerPlayerEntity sp && sp.squaredDistanceTo(this) < 48 * 48) {
                    bar.addPlayer(sp);
                }
            }
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (bar != null) {
            bar.clearPlayers();
        }
        if (!getWorld().isClient && isLeader() && getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            Encounters.bossDefeated(sw, spec.id);
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (bar != null) {
            bar.clearPlayers();
        }
    }

    @Override
    protected String hurtSoundId() {
        return "gopnik.hurt";
    }

    @Override
    protected String deathSoundId() {
        return "gopnik.death";
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }
}
