package ua.lewandivka.entity.boss;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ua.lewandivka.logic.Scheduler;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModItems;

/**
 * Безбарвний Голова району. Ранять його лише носії Кольорового заряду.
 * Він висмоктує колір з арени (платформи сіріють і розсипаються) і з гравців.
 */
public class ColorlessHeadEntity extends ChromaBossEntity {
    public ColorlessHeadEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.WHITE);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public String siteKey() {
        return "tower";
    }

    public static boolean holdsCharge(PlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).isOf(ModItems.COLOR_CHARGE)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canBeHurtBy(DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof PlayerEntity p && holdsCharge(p);
    }

    @Override
    protected Text shieldMessage() {
        return Text.literal("Без кольору його не взяти! Підберіть Кольоровий заряд.");
    }

    @Override
    protected void bossTick(ServerWorld world) {
        BlockPos c = arena != null ? arena : getBlockPos();
        if (age % 500 == 100) {
            boolean exists = !world.getEntitiesByClass(ItemEntity.class, getBoundingBox().expand(24),
                    e -> e.getStack().isOf(ModItems.COLOR_CHARGE)).isEmpty();
            if (!exists) {
                double x = c.getX() + 0.5 + random.nextInt(13) - 6;
                double z = c.getZ() + 0.5 + random.nextInt(13) - 6;
                ItemEntity item = new ItemEntity(world, x, c.getY() + 1.5, z, new ItemStack(ModItems.COLOR_CHARGE));
                item.setGlowing(true);
                item.setPickupDelay(10);
                world.spawnEntity(item);
                world.spawnParticles(ParticleTypes.END_ROD, x, c.getY() + 1.5, z, 40, 0.3, 0.6, 0.3, 0.1);
                for (ServerPlayerEntity p : playersNear(40)) {
                    p.sendMessage(Text.literal("На даху спалахнув Кольоровий заряд!").formatted(net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.BOLD), true);
                }
            }
        }
        if (age % 400 == 200) {
            say(random.nextBoolean() ? "Сірий — це теж колір. Єдиний." : "Ваш район буде таким, як я скажу. Сірим.");
            drainArena(world, c);
        }
        if (age % 300 == 0) {
            summonMinions(ModEntities.COLORLESS, 2, 6);
        }
        if (age % 200 == 50) {
            for (ServerPlayerEntity p : playersNear(30)) {
                if (!holdsCharge(p)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 0));
                }
            }
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2f, 0.6f);
        }
        if (age % 5 == 0) {
            world.spawnParticles(ParticleTypes.ASH, getX(), getY() + 3, getZ(), 6, 1.2, 2, 1.2, 0);
        }
    }

    /** Платформи сіріють і розсипаються, через 20 секунд колір повертається. */
    private void drainArena(ServerWorld world, BlockPos c) {
        BlockPos floorCenter = c.down();
        for (int i = 0; i < 12; i++) {
            int dx = random.nextInt(15) - 7;
            int dz = random.nextInt(15) - 7;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                continue;
            }
            BlockPos p = floorCenter.add(dx, 0, dz);
            if (!world.getBlockState(p).isOf(Blocks.WHITE_CONCRETE) && !world.getBlockState(p).isOf(Blocks.LIGHT_GRAY_CONCRETE)) {
                continue;
            }
            net.minecraft.block.BlockState original = world.getBlockState(p);
            world.setBlockState(p, ModBlocks.GREY_VOID.getDefaultState());
            world.spawnParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
            Scheduler.after(world.getServer(), 400, () -> {
                if (world.getBlockState(p).isAir() || world.getBlockState(p).isOf(ModBlocks.GREY_VOID)) {
                    world.setBlockState(p, original);
                }
            });
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WITHER_DEATH;
    }
}
