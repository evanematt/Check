package ua.lewandivka.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.lewandivka.registry.ModEntities;

/** Колекціонер Нашийників: вкрав імена котів, щоб ті забули дорогу додому. */
public class CollectorEntity extends ChromaBossEntity {
    public CollectorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.PURPLE);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 170.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_ARMOR, 3.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    public String siteKey() {
        return "shelter";
    }

    @Override
    protected void bossTick(ServerWorld world) {
        LivingEntity target = getTarget();
        if (age % 200 == 100 && target != null) {
            Vec3d behind = target.getPos().subtract(target.getRotationVector().multiply(2.0));
            world.spawnParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 40, 0.4, 1, 0.4, 0.3);
            requestTeleport(behind.x, target.getY(), behind.z);
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, 1.2f);
            say(random.nextBoolean() ? "Гарний нашийник у тебе. Буде мій." : "Імена — це така гарна колекція…");
        }
        if (age % 360 == 0) {
            summonMinions(ModEntities.SHADE, 2, 4);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_EVOKER_DEATH;
    }
}
