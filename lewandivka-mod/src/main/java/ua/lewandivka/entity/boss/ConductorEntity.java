package ua.lewandivka.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.world.Sites;

import java.util.List;

/** Кондуктор Останнього Рейсу. Захист знімається, коли всі три компостери прокомпостовані різними гравцями. */
public class ConductorEntity extends ChromaBossEntity {
    private boolean wasVulnerable;

    public ConductorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.GREEN);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 240.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0)
                .add(EntityAttributes.GENERIC_ARMOR, 5.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    public String siteKey() {
        return "depot";
    }

    private boolean vulnerable() {
        return Sites.isSatisfied((ServerWorld) getWorld(), "depot");
    }

    @Override
    protected boolean canBeHurtBy(DamageSource source) {
        return vulnerable();
    }

    @Override
    protected Text shieldMessage() {
        return Text.literal("«Безквиткових не обслуговую!» Прокомпостуйте всі три компостери — кожен своїм квитком (різні гравці).");
    }

    @Override
    protected void bossTick(ServerWorld world) {
        if (age % 10 == 0) {
            boolean v = vulnerable();
            setGlowing(v);
            if (v && !wasVulnerable) {
                say("Всі квитки прокомпостовані?! Ну… проїзд оплачено…");
            }
            wasVulnerable = v;
        }
        if (age % 160 == 80) {
            List<ServerPlayerEntity> ps = playersNear(28);
            if (!ps.isEmpty()) {
                ServerPlayerEntity p = ps.get(random.nextInt(ps.size()));
                say("Ваш квиточок, будь ласка, " + p.getName().getString() + "!");
                Vec3d pull = getPos().subtract(p.getPos()).normalize().multiply(1.1);
                p.setVelocity(pull.x, 0.45, pull.z);
                p.velocityModified = true;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.5f, 1.6f);
            }
        }
        if (age % 320 == 0) {
            say("Безквиткові, на вихід… тобто на вас!");
            summonMinions(ModEntities.SHADE, 2, 6);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VINDICATOR_DEATH;
    }
}
