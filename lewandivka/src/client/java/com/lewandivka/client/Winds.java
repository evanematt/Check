package com.lewandivka.client;

import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.service.WindRegions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The wind of the shafts and the tube (see {@link WindRegions}) pushes the player who is in the air. It is simulated here, like
 * the glide: the client owns the movement of its player and knows its real velocity, while a push that the server computed from
 * the positions it was sent was always a little out of date, and a spring hatch lost two blocks of its throw to that. The push is
 * {@code Launch.wind}, the function that the level tests fly through every hatch, applied to the ticks of one parity.
 */
public final class Winds {

    private Winds() {
    }

    /** Runs at the start of a client tick, before the player moves. */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !Dimensions.isOurs(client.world)) {
            return;
        }
        WindRegions.prepare();
        if (player.age % 2 != 0 || player.isOnGround() || player.isSpectator() || player.hasVehicle() || player.isFallFlying()
                || player.isTouchingWater() || player.getAbilities().flying) {
            return;
        }
        WindRegions.Region wind = WindRegions.at(Dimensions.idOf(client.world), player.getPos());
        if (wind != null) {
            player.setVelocity(wind.push(player.getVelocity()));
            player.fallDistance = 0.0f;
        }
    }
}
