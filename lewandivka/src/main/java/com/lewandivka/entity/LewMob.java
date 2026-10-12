package com.lewandivka.entity;

import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.sound.GameSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Set;

/**
 * Base class of every living creature of the mod: attributes, sounds and GeckoLib animation come from the catalog entry
 * ({@link EntitySpec}). A "move" controller loops idle/walk/run; everything else is a one-shot "action" animation that
 * the server triggers by name.
 */
public abstract class LewMob extends PathAwareEntity implements GeoEntity {

    private static final Set<String> LOOPS = Set.of("idle", "walk", "run");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final EntitySpec spec;

    protected LewMob(EntityType<? extends LewMob> type, World world) {
        super(type, world);
        this.spec = ModEntities.byId(Registries.ENTITY_TYPE.getId(type).getPath());
        this.experiencePoints = spec != null && spec.boss ? 40 : 4;
    }

    public EntitySpec spec() {
        return spec;
    }

    /**
     * The catalog entry, also while the super constructor is still running: {@code MobEntity} calls {@code initGoals()}
     * before {@link #spec} is assigned, so goal setup must use this instead of the field.
     */
    protected final EntitySpec specNow() {
        return spec != null ? spec : ModEntities.byId(Registries.ENTITY_TYPE.getId(getType()).getPath());
    }

    public static DefaultAttributeContainer.Builder attributes(EntitySpec s) {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, s.health)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, s.speed)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Math.max(0.0, s.attack))
                .add(EntityAttributes.GENERIC_ARMOR, s.armor)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, s.knockbackResistance)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, s.followRange);
    }

    protected String anim(String name) {
        return "animation." + spec.model + "." + name;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, this::moveState));
        AnimationController<LewMob> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : spec.animations) {
            if (!LOOPS.contains(a)) {
                action.triggerableAnim(a, RawAnimation.begin().thenPlay(anim(a)));
            }
        }
        controllers.add(action);
    }

    /** Which looping animation plays now. Subclasses add poses (cats sit and sleep, bosses show a shield). */
    protected String loopAnimation(AnimationState<LewMob> state) {
        if (state.isMoving()) {
            return getVelocity().horizontalLengthSquared() > 0.02 ? "run" : "walk";
        }
        return "idle";
    }

    private PlayState moveState(AnimationState<LewMob> state) {
        String name = loopAnimation(state);
        return state.setAndContinue(RawAnimation.begin().thenLoop(anim(spec.animations.contains(name) ? name : "idle")));
    }

    /** Server side: plays a one-shot animation on all tracking clients. */
    public void play(String animation) {
        if (!getWorld().isClient && spec.animations.contains(animation)) {
            triggerAnim("action", animation);
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ names

    /** Shows a story name above the creature ("???" hides it); an empty name restores the plain one. */
    public void setStoryName(String name) {
        if (name == null || name.isEmpty()) {
            setCustomName(null);
            setCustomNameVisible(false);
        } else {
            setCustomName(Text.literal(name));
            setCustomNameVisible(true);
        }
    }

    // ------------------------------------------------------------------ sounds

    private static SoundEvent sound(String id) {
        return GameSounds.has(id) ? GameSounds.get(id) : null;
    }

    protected String ambientSoundId() {
        return null;
    }

    protected String hurtSoundId() {
        return null;
    }

    protected String deathSoundId() {
        return null;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        String id = ambientSoundId();
        return id == null ? null : sound(id);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        String id = hurtSoundId();
        return id == null ? super.getHurtSound(source) : sound(id);
    }

    @Override
    protected SoundEvent getDeathSound() {
        String id = deathSoundId();
        return id == null ? super.getDeathSound() : sound(id);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }
}
