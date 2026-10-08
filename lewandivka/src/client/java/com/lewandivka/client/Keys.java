package com.lewandivka.client;

import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.world.Launch;
import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Key bindings and the input-driven abilities. The client only asks: the server checks the grant, the cooldown and the
 * state before anything moves. The glide is the one exception that has to be simulated here, because the client owns
 * the movement of its own player.
 */
public final class Keys {

    public static KeyBinding dash;
    public static KeyBinding notebook;
    public static KeyBinding hint;
    private static boolean jumpWasDown;

    private Keys() {
    }

    public static void register() {
        dash = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lewandivka.dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.lewandivka"));
        notebook = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lewandivka.notebook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.lewandivka"));
        hint = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.lewandivka.hint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.lewandivka"));
    }

    /** Runs at the start of a client tick, before the game reads the keys (so a consumed Q is never also a drop). */
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !Dimensions.isOurs(client.world)) {
            jumpWasDown = false;
            return;
        }
        boolean cinematic = Effects.cinematicActive();
        if (client.currentScreen == null && !cinematic) {
            while (notebook.wasPressed()) {
                ClientNet.openNotebook();
            }
            while (hint.wasPressed()) {
                showHint(player);
            }
            while (dash.wasPressed()) {
                if (ClientState.has(Ability.DASH) && ClientState.cooldownLeft(Ability.DASH) <= 0) {
                    ClientNet.requestAbility(Ability.DASH, true);
                }
            }
            passCharge(client, player);
            glide(client, player);
        } else {
            jumpWasDown = false;
        }
    }

    private static void showHint(ClientPlayerEntity player) {
        if (!LewandivkaConfig.get().questHints) {
            return;
        }
        Text hintText = Text.translatable(ClientState.step.hintKey());
        player.sendMessage(hintText.getString().isEmpty() ? Text.translatable("screen.lewandivka.notebook.no_hint") : hintText, true);
    }

    /** The drop key (Q) passes the chromatic charge while you hold it; otherwise it drops items as always. */
    private static void passCharge(MinecraftClient client, ClientPlayerEntity player) {
        if (!ClientState.chargeActive || !player.getUuid().equals(ClientState.chargeHolder)) {
            return;
        }
        boolean pressed = false;
        while (client.options.dropKey.wasPressed()) {
            pressed = true;
        }
        if (pressed) {
            UUID aimed = null;
            Entity target = client.targetedEntity;
            if (target instanceof PlayerEntity other && other != player) {
                aimed = other.getUuid();
            }
            ClientNet.passCharge(aimed);
        }
    }

    private static void glide(MinecraftClient client, ClientPlayerEntity player) {
        boolean down = client.options.jumpKey.isPressed();
        boolean edge = down && !jumpWasDown;
        jumpWasDown = down;
        if (!ClientState.has(Ability.GLIDER)) {
            ClientState.gliding = false;
            return;
        }
        boolean airborne = !player.isOnGround() && !player.isTouchingWater() && !player.isClimbing() && !player.hasVehicle() && !player.getAbilities().flying;
        if (ClientState.gliding) {
            if (!airborne || edge) {
                ClientState.gliding = false;
                ClientNet.requestAbility(Ability.GLIDER, false);
                return;
            }
            // fly where you look; looking down or up does not change the speed
            Vec3d look = player.getRotationVec(1.0f);
            double flat = Math.sqrt(look.x * look.x + look.z * look.z);
            double dx = flat < 1.0E-3 ? 0.0 : look.x / flat;
            double dz = flat < 1.0E-3 ? 0.0 : look.z / flat;
            Vec3d v = player.getVelocity();
            // the game slows the player down after the move, so the speed asked for is higher than the one that is wanted
            double hx = v.x + (dx * Launch.GLIDE_TARGET - v.x) * Launch.GLIDE_BLEND;
            double hz = v.z + (dz * Launch.GLIDE_TARGET - v.z) * Launch.GLIDE_BLEND;
            player.setVelocity(hx, Math.max(v.y, -Launch.GLIDE_SINK), hz);
            player.fallDistance = 0.0f;
        } else if (edge && airborne && ClientState.cooldownLeft(Ability.GLIDER) <= 0 && player.getVelocity().y < 0.0 && player.fallDistance > 0.6f) {
            ClientState.gliding = true;
            ClientNet.requestAbility(Ability.GLIDER, true);
        }
    }
}
