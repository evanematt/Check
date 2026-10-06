package com.lewandivka.entity.boss;

import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.boss.ConductorRules;
import com.lewandivka.entity.GameEntities;
import com.lewandivka.entity.vehicle.TramEntity;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.service.Gates;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * The Conductor. Trams cross the arena on three lanes after a clear warning (bell, lamps, sound and the lane gates
 * sliding open). He shouts "КВИТОК!" and the target has to reach a validator; the three composters (held together)
 * bring the shield down.
 */
public class ConductorEntity extends BossEntity {

    private UUID called;
    private int calledIndex = -1;

    public ConductorEntity(EntityType<? extends ConductorEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected BossRules createRules(long seed) {
        return new ConductorRules(seed);
    }

    @Override
    protected String structure() {
        return "sky_depot";
    }

    private ConductorRules conductor() {
        return (ConductorRules) rules;
    }

    @Override
    public boolean station(String action, int index, UUID player) {
        if (!fighting) {
            return false;
        }
        if (action.equals("composter") && index >= 0 && index < ConductorRules.COMPOSTERS) {
            apply(conductor().useComposter(index, now()));
            BlockOps.setProp(sw(), blockAt("composter_" + (index + 1)), "active", Boolean.toString(conductor().composterLit(index, now())));
            sw().playSound(null, blockAt("composter_" + (index + 1)), GameSounds.get("tram.compost"), SoundCategory.BLOCKS, 1.0f, 1.0f);
            Scheduler.later(conductor().syncWindowTicks() + 2, () -> {
                if (fighting && !conductor().composterLit(index, now())) {
                    BlockOps.setProp(sw(), blockAt("composter_" + (index + 1)), "active", "false");
                }
            });
            return true;
        }
        if (action.equals("validator") && called != null && called.equals(player)) {
            apply(conductor().ticketValidated(calledIndex, now()));
            called = null;
            calledIndex = -1;
            sw().playSound(null, blockAt("validator_a"), GameSounds.get("tram.validate"), SoundCategory.BLOCKS, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    @Override
    protected void onBossEvent(BossEvent e) {
        switch (e.type()) {
            case TRAM_WARN -> {
                int lane = Math.floorMod(e.a(), 3) + 1;
                for (String side : List.of("a", "b")) {
                    Vec3d p = at("lane_lamp_" + lane + "_" + side);
                    sw().spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 12, 0.2, 0.2, 0.2, 0.01);
                }
                sw().playSound(null, getBlockPos(), GameSounds.get("tram.bell"), SoundCategory.HOSTILE, 1.5f, 1.0f);
                tell(Text.translatable("message.lewandivka.conductor.warning", lane));
            }
            case TRAM_RUN -> {
                int lane = Math.floorMod(e.a(), 3) + 1;
                boolean forward = random.nextBoolean();
                Vec3d from = at("lane_" + lane + "_" + (forward ? "a" : "b"));
                Vec3d to = at("lane_" + lane + "_" + (forward ? "b" : "a"));
                Gates.set(sw(), structure(), "lane_gate_" + lane + "_a", true);
                Gates.set(sw(), structure(), "lane_gate_" + lane + "_b", true);
                TramEntity tram = new TramEntity(GameEntities.type("arena_tram"), sw());
                tram.drive(List.of(from, to), 0.9, true, e.b(), () -> {
                    Gates.set(sw(), structure(), "lane_gate_" + lane + "_a", false);
                    Gates.set(sw(), structure(), "lane_gate_" + lane + "_b", false);
                });
                adopt(tram);
                sw().spawnEntity(tram);
                Scheduler.later(e.b() + 160, () -> {
                    if (!tram.isRemoved()) {
                        tram.discard();
                    }
                    Gates.set(sw(), structure(), "lane_gate_" + lane + "_a", false);
                    Gates.set(sw(), structure(), "lane_gate_" + lane + "_b", false);
                });
            }
            case TICKET_CALL -> {
                List<ServerPlayerEntity> ps = participants(40);
                if (!ps.isEmpty()) {
                    ServerPlayerEntity target = ps.get(Math.floorMod(e.a(), ps.size()));
                    called = target.getUuid();
                    calledIndex = e.a();
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, e.b(), 0, false, false));
                    target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.translatable("message.lewandivka.conductor.call")));
                    play("attack");
                    com.lewandivka.quest.Dialogues.play(sw().getServer(), "conductor_call", participants(60), this);
                }
            }
            case TICKET_FAIL -> {
                ServerPlayerEntity p = called == null ? null : sw().getServer().getPlayerManager().getPlayer(called);
                if (p != null) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                    p.removeStatusEffect(StatusEffects.GLOWING);
                }
                called = null;
                calledIndex = -1;
            }
            default -> { }
        }
    }

    @Override
    protected void onReset() {
        called = null;
        calledIndex = -1;
        for (int i = 1; i <= 3; i++) {
            Gates.set(sw(), structure(), "lane_gate_" + i + "_a", false);
            Gates.set(sw(), structure(), "lane_gate_" + i + "_b", false);
            BlockOps.setProp(sw(), blockAt("composter_" + i), "active", "false");
        }
    }
}
