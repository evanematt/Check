package ua.lewandivka.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import ua.lewandivka.registry.ModItems;

/** Боржник із третього під'їзду. Тікає від усіх, але з ним можна домовитись. */
public class BorzhnykEntity extends PathAwareEntity {
    private static final String[] EXCUSES = {
            "Я нікому нічого не винен!",
            "Чайник? Який чайник? Не було ніякого чайника.",
            "Слухай, давай завтра, га?",
            "Три смарагди — і розійдемось по-людськи."
    };

    public BorzhnykEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new FleeEntityGoal<>(this, PlayerEntity.class, 9.0f, 1.0, 1.35));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(Items.EMERALD) && held.getCount() >= 3) {
            if (!player.getAbilities().creativeMode) {
                held.decrement(3);
            }
            player.giveItemStack(new ItemStack(ModItems.MAGIC_KETTLE));
            player.sendMessage(Text.literal("Боржник: «Та на, на твій чайник… Тільки Шлагбауму не кажи, де я живу.»").formatted(Formatting.YELLOW), false);
            ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            discard();
            return ActionResult.CONSUME;
        }
        player.sendMessage(Text.literal("Боржник: «" + EXCUSES[random.nextInt(EXCUSES.length)] + "»").formatted(Formatting.YELLOW), false);
        return ActionResult.CONSUME;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient) {
            dropStack(new ItemStack(ModItems.MAGIC_KETTLE));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }
}
