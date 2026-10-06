package com.lewandivka.ability;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.Dimensions;
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
        long springReadyAt;
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
        s.dashReadyAt = now + Ability.DASH.cooldownTicks;
        s.dashActiveUntil = now + 8;
        dir = dir.normalize();
        p.addVelocity(dir.x * 1.45, p.isOnGround() ? 0.22 : 0.05, dir.z * 1.45);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ability.dash_1"), SoundCategory.PLAYERS, 1.0f, 1.0f);
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

    /** Spring pads rate-limit themselves per player so a bounce is never triggered twice in a row. */
    public static boolean canSpring(ServerPlayerEntity p) {
        State s = state(p);
        long now = p.getServerWorld().getTime();
        if (now < s.springReadyAt) {
            return false;
        }
        s.springReadyAt = now + 8;
        return true;
    }

    /** Spring Insoles make landings soft: most of the fall damage disappears. */
    public static float softenFall(ServerPlayerEntity p, float amount, DamageSource source) {
        if (source.isOf(DamageTypes.FALL) && Campaign.hasAbility(p, Ability.SPRING_INSOLES)) {
            return amount * 0.2f;
        }
        return amount;
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }
}
