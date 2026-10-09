package com.lewandivka.client;

import com.lewandivka.config.LewandivkaConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/** Client-side feedback that has no server state: the camera shake, the fade of the monochrome overlay, the cinematic clock. */
public final class Effects {

    private static final Random RANDOM = new Random();
    private static int shakeTicks;
    private static int shakeTotal = 1;
    private static int shakeStrength;

    private Effects() {
    }

    /** @param strength 1 (rumble) to 5 (slam) */
    public static void shake(int strength, int ticks) {
        if (LewandivkaConfig.get().screenShake <= 0.01) {
            return;
        }
        shakeStrength = Math.max(shakeStrength, MathHelper.clamp(strength, 1, 5));
        shakeTicks = Math.max(shakeTicks, ticks);
        shakeTotal = Math.max(shakeTicks, 1);
    }

    public static void tick(MinecraftClient client) {
        ClientState.tick++;
        float target = ClientState.monochrome ? 1.0f : 0.0f;
        ClientState.monochromeAlpha += (target - ClientState.monochromeAlpha) * (ClientState.monochrome ? 0.12f : 0.2f);
        if (ClientState.cinematicLength > 0 && ++ClientState.cinematicTicks >= ClientState.cinematicLength) {
            ClientState.cinematic = "";
            ClientState.cinematicLength = 0;
            ClientState.cinematicTicks = 0;
        }
        ClientPlayerEntity player = client.player;
        if (shakeTicks > 0) {
            shakeTicks--;
            if (player != null && !client.isPaused()) {
                double scale = LewandivkaConfig.get().screenShake;
                float amplitude = (float) (shakeStrength * 0.32 * scale * (shakeTicks / (double) shakeTotal));
                player.setYaw(player.getYaw() + (RANDOM.nextFloat() - 0.5f) * amplitude);
                player.setPitch(MathHelper.clamp(player.getPitch() + (RANDOM.nextFloat() - 0.5f) * amplitude, -90.0f, 90.0f));
            }
            if (shakeTicks == 0) {
                shakeStrength = 0;
            }
        }
    }

    public static boolean cinematicActive() {
        return !ClientState.cinematic.isEmpty();
    }
}
