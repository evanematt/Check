package ua.lewandivka.entity;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.registry.ModItems;

/** Гопник у спортивці (а також тіньовий безквитковий і безбарвник — той самий клас, різні типи). */
public class GopnikEntity extends ZombieEntity {
    private boolean leader;

    public GopnikEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createGopnikAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 22.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.5);
    }

    public static DefaultAttributeContainer.Builder createShadeAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0);
    }

    public static DefaultAttributeContainer.Builder createColorlessAttributes() {
        return ZombieEntity.createZombieAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 26.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.5);
    }

    public void setLeader(boolean leader) {
        this.leader = leader;
    }

    @Override
    protected void initCustomGoals() {
        this.goalSelector.add(2, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    @Nullable
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        return super.initialize(world, difficulty, spawnReason, new ZombieEntity.ZombieData(false, false), entityNbt);
    }

    @Override
    protected void initEquipment(Random random, LocalDifficulty localDifficulty) {
    }

    @Override
    protected void applyAttributeModifiers(float chanceMultiplier) {
    }

    @Override
    protected boolean burnsInDaylight() {
        return false;
    }

    @Override
    protected boolean canConvertInWater() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PILLAGER_DEATH;
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!getWorld().isClient && leader) {
            dropStack(new ItemStack(ModItems.TRAM_COMPOSTOR));
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("TramLeader", leader);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        leader = nbt.getBoolean("TramLeader");
    }
}
