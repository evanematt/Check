package com.lewandivka.ability;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the three traversal abilities. They are stored on the player in the campaign (never as items); the
 * client only asks, and the server checks the grant, the cooldown, the dimension and the player state before it moves
 * anybody.
 */
public final class Abilities {

    private static final class State {
        long dashReadyAt;
        long dashActiveUntil;
        long glideReadyAt;
        boolean gliding;
        int glideEnergy;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private Abilities() {
    }

    private static State state(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new State());
    }

    private static boolean valid(ServerPlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !p.hasVehicle() && Dimensions.isOurs(p.getWorld());
    }

    /** A client asked to use an ability ({@code on} = start for the glider, ignored for the dash). */
    public static void request(ServerPlayerEntity p, int abilityId, boolean on) {
        Ability a = Ability.byId(abilityId);
        if (a == null || !valid(p) || !Campaign.hasAbility(p, a)) {
            if (a == Ability.GLIDER) {
                Net.glideStop(p);
            }
            return;
        }
        switch (a) {
            case DASH -> dash(p);
            case GLIDER -> glide(p, on);
            default -> { }
        }
    }

    private static void dash(ServerPlayerEntity p) {
        State s = state(p);
        long now = p.getServerWorld().getTime();
        if (now < s.dashReadyAt) {
            return;
        }
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        if (dir.lengthSquared() < 1.0E-4) {
            return;
        }
        com.lewandivka.LewandivkaMod.LOGGER.info("dash by {} towards {}", p.getGameProfile().getName(), p.getHorizontalFacing());
        s.dashReadyAt = now + Ability.DASH.cooldownTicks;
        s.dashActiveUntil = now + 8;
        dir = dir.normalize();
        p.addVelocity(dir.x * 1.45, p.isOnGround() ? 0.22 : 0.05, dir.z * 1.45);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
        p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ability.dash"), SoundCategory.PLAYERS, 1.0f, 1.0f);
        Net.cooldown(p, Ability.DASH, Ability.DASH.cooldownTicks);
    }

    /** True while the player is inside the short dash window (dash doors check it). */
    public static boolean dashing(ServerPlayerEntity p) {
        State s = STATES.get(p.getUuid());
        return s != null && p.getServerWorld().getTime() <= s.dashActiveUntil;
    }

    private static void glide(ServerPlayerEntity p, boolean on) {
        State s = state(p);
        long now = p.getServerWorld().getTime();
        if (!on) {
            stopGlide(p, s, now);
            return;
        }
        if (s.gliding || now < s.glideReadyAt || p.isOnGround()) {
            if (!s.gliding) {
                Net.glideStop(p);                      // refused: tell the client to stop simulating the glide
            }
            return;
        }
        s.gliding = true;
        s.glideEnergy = Ability.GLIDER.energyTicks;
        p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ability.glide_start"), SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void stopGlide(ServerPlayerEntity p, State s, long now) {
        if (s.gliding) {
            s.gliding = false;
            s.glideReadyAt = now + Ability.GLIDER.cooldownTicks;
            Net.glideStop(p);
            Net.cooldown(p, Ability.GLIDER, Ability.GLIDER.cooldownTicks);
            p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ability.glide_end"), SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    public static boolean gliding(ServerPlayerEntity p) {
        State s = STATES.get(p.getUuid());
        return s != null && s.gliding;
    }

    /** Every server tick: the glide spends energy, ends on the ground and keeps the fall gentle. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            State s = STATES.get(p.getUuid());
            if (s != null && valid(p) && p.getServerWorld().getTime() <= s.dashActiveUntil) {
                DashDoors.check(p);
            }
            if (s == null || !s.gliding) {
                continue;
            }
            long now = p.getServerWorld().getTime();
            if (!valid(p) || p.isOnGround() || p.isTouchingWater() || --s.glideEnergy <= 0) {
                stopGlide(p, s, now);
                continue;
            }
            Vec3d v = p.getVelocity();
            if (v.y < -0.1) {
                p.setVelocity(v.x, -0.1, v.z);
                p.velocityModified = true;
            }
            p.fallDistance = 0;
        }
    }

    // ------------------------------------------------------------------ spring insoles

    /** Who was launched by a spring and when: a pad acts every tick somebody stands on it, one launch per few ticks is enough. */
    private static final Map<UUID, Long> SPRUNG = new HashMap<>();
    /** A launch can reach 30 blocks up; the whole flight (and the landing) counts as part of it. */
    private static final long FLIGHT_TICKS = 400;
    private static boolean softening;

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Abilities::allowDamage);
    }

    /** The high bounce of the pads needs the Spring Insoles (admins in creative mode always have them). */
    public static boolean hasInsoles(ServerPlayerEntity p) {
        return p.isCreative() || Campaign.hasAbility(p, Ability.SPRING_INSOLES);
    }

    /** True when the entity may be launched now (and remembers the launch). */
    public static boolean launched(Entity e, long now) {
        Long last = SPRUNG.get(e.getUuid());
        if (last != null && now - last < 8) {
            return false;
        }
        if (SPRUNG.size() > 256) {
            SPRUNG.values().removeIf(t -> now - t > FLIGHT_TICKS);
        }
        SPRUNG.put(e.getUuid(), now);
        return true;
    }

    private static boolean recentlyLaunched(Entity e, long now) {
        Long last = SPRUNG.get(e.getUuid());
        return last != null && now - last < FLIGHT_TICKS;
    }

    /** A spring never hurts, and the Spring Insoles take most of the sting out of every other fall. */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (softening || !source.isOf(DamageTypes.FALL)) {
            return true;
        }
        if (recentlyLaunched(entity, entity.getWorld().getTime())) {
            return false;
        }
        if (entity instanceof ServerPlayerEntity p && Campaign.hasAbility(p, Ability.SPRING_INSOLES)) {
            float reduced = amount * 0.2f;
            if (reduced >= 1.0f) {
                softening = true;
                try {
                    p.damage(source, reduced);
                } finally {
                    softening = false;
                }
            }
            return false;
        }
        return true;
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
