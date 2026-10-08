package com.lewandivka.entity;

import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.registry.EntitySpec.Role;
import com.lewandivka.entity.projectile.SeedProjectileEntity;
import com.lewandivka.item.GameItems;
import com.lewandivka.quest.Dialogues;
import com.lewandivka.sound.GameSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;

import java.util.EnumSet;
import java.util.List;

/**
 * Gopnik, Seed Thrower and Senior Yard Gopnik. Groups from the neutral spots start out peaceful and ask
 * "Сємки є?"; seeds make them friends for good, walking away keeps the peace, an attack makes them hostile.
 */
public class GopnikEntity extends LewMob {

    public enum Mood { NEUTRAL, ASKING, FRIENDLY, HOSTILE }

    private Mood mood = Mood.HOSTILE;
    private int askCooldown;
    private int askedFor;
    private int blockTicks;

    public GopnikEntity(EntityType<? extends GopnikEntity> type, World world) {
        super(type, world);
    }

    public Mood mood() {
        return mood;
    }

    public void setMood(Mood mood) {
        this.mood = mood;
    }

    /** Neutral groups are placed by the district plan at the {@code neutral_*} markers. */
    public void makeNeutral() {
        this.mood = Mood.NEUTRAL;
    }

    private boolean ranged() {
        return spec.role == Role.SEED_THROWER;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        if (ranged()) {
            goalSelector.add(2, new SeedThrowGoal(this));
        } else {
            goalSelector.add(2, new MeleeAttackGoal(this, 1.1, false));
        }
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.7));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true) {
            @Override
            public boolean canStart() {
                return mood == Mood.HOSTILE && super.canStart();
            }
        });
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (getWorld().isClient) {
            return;
        }
        if (blockTicks > 0) {
            blockTicks--;
        }
        if (mood == Mood.NEUTRAL && askCooldown > 0) {
            askCooldown--;
        }
        if (mood == Mood.NEUTRAL && askCooldown <= 0 && age % 10 == 0) {
            PlayerEntity p = getWorld().getClosestPlayer(this, 4.5);
            if (p instanceof ServerPlayerEntity sp) {
                mood = Mood.ASKING;
                askedFor = 0;
                getNavigation().stop();
                getLookControl().lookAt(sp);
                Dialogues.play(sp.getServer(), "seeds_demand", List.of(sp), this);
            }
        } else if (mood == Mood.ASKING && age % 10 == 0) {
            PlayerEntity p = getWorld().getClosestPlayer(this, 12.0);
            askedFor += 10;
            if (p == null || p.squaredDistanceTo(this) > 100) {
                mood = Mood.NEUTRAL;
                askCooldown = 600;
                if (p instanceof ServerPlayerEntity sp) {
                    Dialogues.play(sp.getServer(), "seeds_left", List.of(sp), this);
                }
            }
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient || !(player instanceof ServerPlayerEntity sp) || (mood != Mood.ASKING && mood != Mood.NEUTRAL)) {
            return super.interactMob(player, hand);
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() == GameItems.get(QuestItems.SEEDS)) {
            held.decrement(1);
            mood = Mood.FRIENDLY;
            setTarget(null);
            Dialogues.play(sp.getServer(), "seeds_given", List.of(sp), this);
            play("talk");
            com.lewandivka.campaign.Campaign.player(sp).addRepPoints(1);
            com.lewandivka.campaign.Campaign.dirty(sp.getServer());
            com.lewandivka.quest.Advancements.grant(sp, "seeds");
            if (random.nextInt(2) == 0) {
                // a thank-you: talking is worth more than fighting
                dropStack(new ItemStack(GameItems.get(QuestItems.TOKEN), 1));
                com.lewandivka.quest.Story.event(sp.getServer(), com.lewandivka.core.story.Events.TOKEN_COLLECTED);
            }
            return ActionResult.SUCCESS;
        }
        Dialogues.play(sp.getServer(), "seeds_none", List.of(sp), this);
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity && mood != Mood.HOSTILE) {
            mood = Mood.HOSTILE;
        }
        // the senior yard gopnik can block frontal attacks for a moment
        if (spec.role == Role.SENIOR_GOPNIK && source.getAttacker() != null && blockTicks > 0 && isFrontal(source.getAttacker().getPos())) {
            playSound(net.minecraft.sound.SoundEvents.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            return false;
        }
        boolean hit = super.damage(source, amount);
        if (hit && spec.role == Role.SENIOR_GOPNIK && random.nextInt(4) == 0) {
            blockTicks = 40;
        }
        return hit;
    }

    private boolean isFrontal(Vec3d from) {
        Vec3d look = getRotationVec(1.0f).multiply(1, 0, 1).normalize();
        Vec3d to = from.subtract(getPos()).multiply(1, 0, 1).normalize();
        return look.dotProduct(to) > 0.35;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (getWorld().isClient) {
            return;
        }
        dropStack(new ItemStack(GameItems.get(QuestItems.SEEDS), 1 + random.nextInt(3)));
        boolean senior = spec.role == Role.SENIOR_GOPNIK;
        if (senior || random.nextInt(ranged() ? 2 : 3) == 0) {
            dropStack(new ItemStack(GameItems.get(QuestItems.TOKEN), 1));
            if (source.getAttacker() instanceof ServerPlayerEntity sp) {
                com.lewandivka.quest.Story.event(sp.getServer(), com.lewandivka.core.story.Events.TOKEN_COLLECTED);
            }
        }
    }

    @Override
    protected String loopAnimation(AnimationState<LewMob> state) {
        return super.loopAnimation(state);
    }

    @Override
    protected String ambientSoundId() {
        return "gopnik.ambient";
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
        nbt.putString("mood", mood.name());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try {
            mood = Mood.valueOf(nbt.getString("mood"));
        } catch (IllegalArgumentException e) {
            mood = Mood.HOSTILE;
        }
        if (mood == Mood.ASKING) {
            mood = Mood.NEUTRAL;
        }
    }

    /** Ranged attack of the seed thrower: a slow seed every two seconds from a safe distance. */
    private static final class SeedThrowGoal extends Goal {
        private final GopnikEntity mob;
        private int cooldown;

        SeedThrowGoal(GopnikEntity mob) {
            this.mob = mob;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.squaredDistanceTo(t) < 20 * 20;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().lookAt(t, 30f, 30f);
            double d = mob.squaredDistanceTo(t);
            if (d < 36) {
                mob.getNavigation().stop();
                Vec3d away = mob.getPos().subtract(t.getPos()).normalize().multiply(4);
                mob.getNavigation().startMovingTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.0);
            } else if (d > 144) {
                mob.getNavigation().startMovingTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
            }
            if (--cooldown <= 0 && mob.canSee(t)) {
                cooldown = 40;
                SeedProjectileEntity seed = new SeedProjectileEntity(com.lewandivka.entity.GameEntities.type("seed_projectile"), mob.getWorld());
                seed.setOwner(mob);
                seed.setPosition(mob.getX(), mob.getEyeY() - 0.2, mob.getZ());
                Vec3d dir = new Vec3d(t.getX() - mob.getX(), t.getBodyY(0.5) - seed.getY(), t.getZ() - mob.getZ()).normalize().multiply(0.9);
                seed.setVelocity(dir.x, dir.y + 0.05, dir.z);
                seed.setDamage(1.5f * (float) com.lewandivka.config.LewandivkaConfig.get().bossDifficulty);
                mob.getWorld().spawnEntity(seed);
                mob.play("throw");
                mob.getWorld().playSound(null, mob.getBlockPos(), GameSounds.get("gopnik.throw"), SoundCategory.HOSTILE, 0.8f, 1.0f);
            }
        }
    }
}
