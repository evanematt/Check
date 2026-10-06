package com.lewandivka.entity.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A rolling wheel of the Garage King: slow enough to jump over, telegraphed by the boss, damages and knocks back. */
public class WheelProjectileEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3d heading = Vec3d.ZERO;
    private float damage = 4.0f;
    private int life = 140;

    public WheelProjectileEntity(EntityType<? extends WheelProjectileEntity> type, World world) {
        super(type, world);
    }

    public void launch(Vec3d direction, double speed, float damage, int lifeTicks) {
        this.heading = direction.normalize().multiply(speed);
        this.damage = damage;
        this.life = lifeTicks;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        if (--life <= 0) {
            discard();
            return;
        }
        setVelocity(heading.x, getVelocity().y - 0.08, heading.z);
        move(MovementType.SELF, getVelocity());
        setYaw((float) (Math.toDegrees(Math.atan2(-heading.x, heading.z))));
        Box box = getBoundingBox().expand(0.2);
        for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, box, x -> x instanceof PlayerEntity)) {
            if (e.damage(getDamageSources().mobProjectile(this, null), damage)) {
                Vec3d push = heading.normalize().multiply(0.9).add(0, 0.35, 0);
                e.addVelocity(push.x, push.y, push.z);
                e.velocityModified = true;
            }
        }
        if (horizontalCollision) {
            discard();
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("life", life);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
