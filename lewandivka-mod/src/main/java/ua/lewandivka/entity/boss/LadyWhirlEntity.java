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
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Пані Вирва — прозора істота, всередині якої плаває цілий басейн. Затоплює чверті арени. */
public class LadyWhirlEntity extends ChromaBossEntity {
    private static final String[] QUADRANTS = {"бірюзову", "блакитну", "жовту", "рожеву"};
    private int quadrant = -1;

    public LadyWhirlEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.BLUE);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 230.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String siteKey() {
        return "aqua";
    }

    /** 0: x≥0,z≥0 бірюзова; 1: x<0,z≥0 блакитна; 2: x<0,z<0 жовта; 3: x≥0,z<0 рожева. */
    private static int quadrantOf(double dx, double dz) {
        if (dz >= 0) {
            return dx >= 0 ? 0 : 1;
        }
        return dx < 0 ? 2 : 3;
    }

    @Override
    protected void bossTick(ServerWorld world) {
        BlockPos c = arena != null ? arena : getBlockPos();
        int phase = age % 180;
        if (phase == 0) {
            quadrant = random.nextInt(4);
            say("Затоплюю " + QUADRANTS[quadrant] + " чверть! Пливіть, мої хороші!");
            for (ServerPlayerEntity p : playersNear(30)) {
                p.sendMessage(Text.literal("Тікайте з " + QUADRANTS[quadrant] + "ї чверті!").formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
        }
        if (quadrant >= 0 && phase < 60 && phase % 4 == 0) {
            for (int i = 0; i < 12; i++) {
                double r = 2 + random.nextDouble() * 14;
                double a = random.nextDouble() * Math.PI / 2 + quadrant * Math.PI / 2;
                double x = c.getX() + 0.5 + Math.cos(a) * r;
                double z = c.getZ() + 0.5 + Math.sin(a) * r;
                if (quadrantOf(x - c.getX() - 0.5, z - c.getZ() - 0.5) != quadrant) {
                    continue;
                }
                world.spawnParticles(ParticleTypes.SPLASH, x, c.getY() + 0.2, z, 4, 0.3, 0.1, 0.3, 0.1);
                world.spawnParticles(ParticleTypes.BUBBLE_POP, x, c.getY() + 0.5, z, 2, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (quadrant >= 0 && phase == 60) {
            world.playSound(null, c, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 3f, 0.5f);
            for (ServerPlayerEntity p : playersNear(26)) {
                double dx = p.getX() - c.getX() - 0.5;
                double dz = p.getZ() - c.getZ() - 0.5;
                if (quadrantOf(dx, dz) == quadrant && p.getY() < c.getY() + 6) {
                    p.damage(getDamageSources().mobAttack(this), 8f);
                    Vec3d pull = new Vec3d(-dx, 0, -dz).normalize().multiply(0.8);
                    p.setVelocity(pull.x, 0.7, pull.z);
                    p.velocityModified = true;
                    world.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
                }
            }
            quadrant = -1;
        }
        if (age % 500 == 250) {
            say("Хто без шапочки — той без басейну!");
            summonMinions(ua.lewandivka.registry.ModEntities.SHADE, 2, 5);
        }
        if (age % 3 == 0) {
            world.spawnParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + 2.5, getZ(), 2, 1.2, 0.8, 1.2, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SLIME_DEATH;
    }
}
