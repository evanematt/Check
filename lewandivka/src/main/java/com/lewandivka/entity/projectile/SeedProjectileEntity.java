package com.lewandivka.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A thrown sunflower seed: small, slow enough to dodge, never lethal on its own. */
public class SeedProjectileEntity extends ProjectileEntity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float damage = 2.0f;

    public SeedProjectileEntity(EntityType<? extends SeedProjectileEntity> type, World world) {
        super(type, world);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
        }
        Vec3d v = getVelocity();
        setPosition(getX() + v.x, getY() + v.y, getZ() + v.z);
        setVelocity(v.x * 0.99, v.y - 0.03, v.z * 0.99);
        if (age > 80 || isOnGround()) {
            discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        Entity target = hit.getEntity();
        if (!getWorld().isClient && target instanceof LivingEntity living && target != getOwner()) {
            living.damage(getDamageSources().thrown(this, getOwner()), damage);
            discard();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient && hit.getType() == HitResult.Type.BLOCK) {
            discard();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
