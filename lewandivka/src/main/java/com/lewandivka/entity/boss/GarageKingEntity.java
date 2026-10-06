package com.lewandivka.entity.boss;

import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.boss.GarageKingRules;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.projectile.WheelProjectileEntity;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.service.Lifts;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/** The Garage King: three arena levers held together open the core; later wheels, minions, cars and lift malfunctions. */
public class GarageKingEntity extends BossEntity {

    private static final String[] LEVERS = {"lever_a", "lever_b", "lever_c"};
    private static final String[] LIFTS = {"lift_a", "lift_b", "lift_c"};

    public GarageKingEntity(EntityType<? extends GarageKingEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected BossRules createRules(long seed) {
        return new GarageKingRules(seed);
    }

    @Override
    protected String structure() {
        return "rainbow_garage";
    }

    private GarageKingRules king() {
        return (GarageKingRules) rules;
    }

    @Override
    public boolean station(String action, int index, UUID player) {
        if (!fighting || !action.equals("lever") || index < 0 || index >= LEVERS.length) {
            return false;
        }
        apply(king().useLever(index, now()));
        BlockOps.setProp(sw(), blockAt(LEVERS[index]), "powered", Boolean.toString(king().leverLit(index, now())));
        sw().playSound(null, blockAt(LEVERS[index]), GameSounds.get("lever.pull"), SoundCategory.BLOCKS, 1.0f, 1.0f);
        int window = king().syncWindowTicks();
        Scheduler.later(window + 2, () -> {
            if (fighting && !king().leverLit(index, now())) {
                BlockOps.setProp(sw(), blockAt(LEVERS[index]), "powered", "false");
            }
        });
        return true;
    }

    @Override
    protected void onBossEvent(BossEvent e) {
        switch (e.type()) {
            case LIFTS -> {
                for (String lift : LIFTS) {
                    Lifts.move(sw(), structure(), lift, e.a() == 1);
                }
            }
            case CORE_OPEN -> {
                play("core_open");
                tell(Text.translatable("message.lewandivka.boss.core_open"));
                for (String lever : LEVERS) {
                    BlockOps.setProp(sw(), blockAt(lever), "powered", "true");
                }
            }
            case CORE_CLOSE -> {
                tell(Text.translatable("message.lewandivka.boss.core_closed"));
                for (String lever : LEVERS) {
                    BlockOps.setProp(sw(), blockAt(lever), "powered", "false");
                }
            }
            case WHEEL -> {
                List<ServerPlayerEntity> ps = participants(40);
                if (!ps.isEmpty()) {
                    ServerPlayerEntity target = ps.get(Math.floorMod(e.a(), ps.size()));
                    play("wheels");
                    launchWheel(at("arena_center").add(random.nextDouble() * 16 - 8, 0, random.nextDouble() * 16 - 8), target.getPos(), 0.55, 4.0f, 1.0);
                }
            }
            case CAR -> {
                // a car crosses the arena along one of the two lanes after a clear warning (bell + smoke)
                Vec3d from = at(e.a() == 0 ? "car_lane_a" : "car_lane_b");
                Vec3d to = at(e.a() == 0 ? "car_lane_b" : "car_lane_a");
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.bell"), SoundCategory.HOSTILE, 1.0f, 1.0f);
                sw().spawnParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 0.5, from.z, 30, 1.5, 0.3, 1.5, 0.02);
                long warn = party.deadlineTicks(50);
                Scheduler.later(warn, () -> {
                    if (fighting) {
                        launchWheel(from, to, 0.9, 6.0f, 2.2);
                    }
                });
            }
            case MALFUNCTION -> {
                int lever = Math.floorMod(e.a(), LEVERS.length);
                BlockOps.setProp(sw(), blockAt(LEVERS[lever]), "powered", "false");
                var p = at(LEVERS[lever]);
                sw().spawnParticles(ParticleTypes.SMOKE, p.x, p.y + 1, p.z, 25, 0.3, 0.5, 0.3, 0.02);
                sw().playSound(null, blockAt(LEVERS[lever]), GameSounds.get("garage.power_off"), SoundCategory.BLOCKS, 1.0f, 0.8f);
                tell(Text.translatable("message.lewandivka.lift.sync"));
            }
            case REPAIRED -> {
                var p = at(LEVERS[Math.floorMod(e.a(), LEVERS.length)]);
                sw().spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1, p.z, 20, 0.3, 0.5, 0.3, 0.05);
                sw().playSound(null, blockAt(LEVERS[Math.floorMod(e.a(), LEVERS.length)]), GameSounds.get("garage.power_on"), SoundCategory.BLOCKS, 1.0f, 1.0f);
            }
            case ADDS -> {
                int n = Math.max(1, e.a());
                for (int i = 0; i < n; i++) {
                    spawnAdd("mechanic_minion", "minion_spawn_" + (1 + random.nextInt(4)));
                }
            }
            default -> { }
        }
    }

    private void launchWheel(Vec3d from, Vec3d to, double speed, float damage, double scale) {
        WheelProjectileEntity w = new WheelProjectileEntity(GameEntities.type("wheel_projectile"), sw());
        w.refreshPositionAndAngles(from.x, from.y, from.z, 0, 0);
        w.launch(new Vec3d(to.x - from.x, 0, to.z - from.z), speed, (float) (damage * party.damageFactor() * com.lewandivka.config.LewandivkaConfig.get().bossDifficulty), 160);
        adopt(w);
        sw().spawnEntity(w);
    }

    @Override
    protected void onReset() {
        if (sw() != null) {
            for (String lift : LIFTS) {
                Lifts.move(sw(), structure(), lift, false);
            }
            for (String lever : LEVERS) {
                BlockOps.setProp(sw(), blockAt(lever), "powered", "false");
            }
        }
    }

    @Override
    protected String ambientSoundId() {
        return null;
    }
}
