package ua.lewandivka.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.world.Sites;

/** Гаражний Король — велетень з машин і металобрухту. Вразливий, лише коли вимкнені всі три підйомники. */
public class GarageKingEntity extends ChromaBossEntity {
    private boolean wasVulnerable;

    public GarageKingEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.YELLOW);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 260.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String siteKey() {
        return "garage";
    }

    private boolean vulnerable() {
        return Sites.isSatisfied((ServerWorld) getWorld(), "garage");
    }

    @Override
    protected boolean canBeHurtBy(DamageSource source) {
        return vulnerable();
    }

    @Override
    protected Text shieldMessage() {
        return Text.literal("Ядро закрите! Вимкніть усі три підйомники одночасно.");
    }

    @Override
    protected void bossTick(ServerWorld world) {
        if (age % 10 == 0) {
            boolean v = vulnerable();
            setGlowing(v);
            if (v && !wasVulnerable) {
                say("Моє ядро… ВІДКРИТЕ?!");
                world.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            wasVulnerable = v;
        }
        if (age % 300 == 150) {
            // Удар по асфальту.
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2f, 0.5f);
            world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, 2, 0.2, 2, 0);
            for (ServerPlayerEntity p : playersNear(30)) {
                if (p.squaredDistanceTo(this) < 7 * 7) {
                    p.damage(getDamageSources().mobAttack(this), 6f);
                    p.setVelocity(p.getVelocity().add(0, 0.9, 0));
                    p.velocityModified = true;
                }
            }
        }
        if (age % 400 == 0) {
            say(random.nextBoolean() ? "Охорона! До гаражів!" : "Хто без пропуску — на металобрухт!");
            summonMinions(ModEntities.SHADE, 2, 6);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_REPAIR;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }
}
