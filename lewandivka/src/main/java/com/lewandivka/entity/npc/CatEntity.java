package com.lewandivka.entity.npc;

import com.lewandivka.entity.LewMob;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.EnumSet;

/**
 * Chinazik and Metadonna. The story flows command them ({@link #commandMove}): they walk to a point, sit, sniff, point,
 * scratch. They never follow players on their own (Chinazik even avoids them while his route is running) and nothing
 * can kill them, so the campaign cannot be lost through a cat.
 */
public class CatEntity extends LewMob {

    private Vec3d goal;
    private boolean avoidPlayers;
    private boolean helper;
    private String pose = "";
    private int poseTicks;

    public CatEntity(EntityType<? extends CatEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public boolean isChinazik() {
        return spec.id.equals("chinazik");
    }

    public void commandMove(Vec3d target) {
        this.goal = target;
    }

    public boolean arrived(double radius) {
        return goal == null || squaredDistanceTo(goal) <= radius * radius;
    }

    public Vec3d goal() {
        return goal;
    }

    public void setAvoidPlayers(boolean avoid) {
        this.avoidPlayers = avoid;
    }

    public void setHelper(boolean helper) {
        this.helper = helper;
    }

    public boolean helper() {
        return helper;
    }

    /** Holds a looping pose ({@code sit}, {@code sleep}, {@code alert}) for a number of ticks. */
    public void pose(String pose, int ticks) {
        this.pose = pose;
        this.poseTicks = ticks;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new FleeEntityGoal<>(this, PlayerEntity.class, 3.5f, 1.0, 1.25, e -> avoidPlayers));
        goalSelector.add(2, new GoToGoal());
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 7.0f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected String loopAnimation(AnimationState<LewMob> state) {
        if (poseTicks > 0 && !state.isMoving()) {
            return pose;
        }
        return super.loopAnimation(state);
    }

    @Override
    public void tick() {
        super.tick();
        if (poseTicks > 0) {
            poseTicks--;
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            return super.damage(source, amount);
        }
        return false;
    }

    @Override
    protected String ambientSoundId() {
        return "cat.purr";
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("helper", helper);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        helper = nbt.getBoolean("helper");
    }

    private final class GoToGoal extends Goal {
        GoToGoal() {
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return goal != null && !arrived(1.2);
        }

        @Override
        public boolean shouldContinue() {
            return goal != null && !arrived(1.2) && !getNavigation().isIdle();
        }

        @Override
        public void start() {
            getNavigation().startMovingTo(goal.x, goal.y, goal.z, 1.0);
        }

        @Override
        public void tick() {
            if (getNavigation().isIdle() && !arrived(1.2)) {
                getNavigation().startMovingTo(goal.x, goal.y, goal.z, 1.0);
            }
        }
    }
}
