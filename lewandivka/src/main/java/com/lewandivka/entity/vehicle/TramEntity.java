package com.lewandivka.entity.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena tram and sky tram. It follows a list of waypoints at a fixed speed. The sky tram carries riders (players sit on
 * it for the two-minute panorama ride); the arena tram is a hazard that hits whoever stands on its lane.
 */
public class TramEntity extends Entity implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Vec3d> path = new ArrayList<>();
    private int next;
    private double speed = 0.4;
    private boolean hazard;
    private float damage = 5.0f;
    private int delay;
    private Runnable onArrive;

    public TramEntity(EntityType<? extends TramEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    /** @param delayTicks wait before the first move (the warning time of a conductor tram) */
    public void drive(List<Vec3d> waypoints, double speed, boolean hazard, int delayTicks, Runnable onArrive) {
        path.clear();
        path.addAll(waypoints);
        this.speed = speed;
        this.hazard = hazard;
        this.delay = delayTicks;
        this.next = 1;
        this.onArrive = onArrive;
        if (!path.isEmpty()) {
            Vec3d p = path.get(0);
            refreshPositionAndAngles(p.x, p.y, p.z, getYaw(), 0);
        }
    }

    public boolean driving() {
        return next < path.size();
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < 4 && !hazard;
    }

    @Override
    public boolean isCollidable() {
        return !hazard;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        int i = getPassengerList().indexOf(passenger);
        double[][] seats = {{-0.8, -0.8}, {0.8, -0.8}, {-0.8, 0.8}, {0.8, 0.8}};
        double[] s = seats[Math.max(0, Math.min(3, i))];
        Vec3d off = new Vec3d(s[0], 0.0, s[1]).rotateY(-getYaw() * ((float) Math.PI / 180));
        positionUpdater.accept(passenger, getX() + off.x, getY() + 1.1, getZ() + off.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        if (path.isEmpty()) {
            strayCheck();
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        if (next >= path.size()) {
            return;
        }
        Vec3d target = path.get(next);
        Vec3d to = target.subtract(getPos());
        double dist = to.length();
        if (dist <= speed) {
            setPosition(target);
            next++;
            if (next >= path.size() && onArrive != null) {
                Runnable r = onArrive;
                onArrive = null;
                r.run();
            }
        } else {
            Vec3d step = to.multiply(speed / dist);
            setVelocity(step);
            setYaw((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
            setPosition(getX() + step.x, getY() + step.y, getZ() + step.z);
        }
        if (hazard) {
            for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.1), x -> x instanceof PlayerEntity)) {
                if (e.damage(getDamageSources().generic(), damage)) {
                    Vec3d push = getVelocity().normalize().multiply(1.2).add(0, 0.4, 0);
                    e.addVelocity(push.x, push.y, push.z);
                    e.velocityModified = true;
                }
            }
        }
    }

    /**
     * A tram without a route is what is left of a ride that a server restart interrupted (the route is not saved, the
     * dungeon flow starts over): whoever still sits on it is put back on the platform of the lower stop, and the tram
     * goes away. Trams that an admin summoned to look at them stay for a minute.
     */
    private void strayCheck() {
        if (age <= 100) {
            return;
        }
        List<Entity> riders = new ArrayList<>(getPassengerList());
        if (riders.isEmpty() && age <= 1200) {
            return;
        }
        removeAllPassengers();
        for (Entity rider : riders) {
            if (rider instanceof ServerPlayerEntity player) {
                com.lewandivka.quest.Travel.toMarker(player, "sky_ascent:stop_platform");
            } else {
                rider.discard();
            }
        }
        discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 0, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(driving() ? "animation.tram.move" : "animation.tram.idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
