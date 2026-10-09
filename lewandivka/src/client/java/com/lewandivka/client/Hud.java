package com.lewandivka.client;

import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.item.SpecItem;
import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The HUD of the campaign: the current objective, the ability icons with their cooldowns, the chromatic charge, the grey
 * overlay of the final fight and the letterbox of cinematics. Every state is shown with text or a number as well as with
 * colour, never with colour alone.
 */
public final class Hud {

    private Hud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(Hud::render);
    }

    private static void render(DrawContext g, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        int w = g.getScaledWindowWidth();
        int h = g.getScaledWindowHeight();
        if (ClientState.monochromeAlpha > 0.01f) {
            int a = (int) (ClientState.monochromeAlpha * 150);
            g.fill(0, 0, w, h, (a << 24) | 0x808080);
        }
        if (Effects.cinematicActive()) {
            cinematic(g, client, w, h, tickDelta);
            return;
        }
        if (client.options.hudHidden || client.world == null || !Dimensions.isOurs(client.world)) {
            return;
        }
        objective(g, client);
        abilities(g, client, w, h);
        charge(g, client, w, h);
    }

    // ------------------------------------------------------------------ objective

    private static void objective(DrawContext g, MinecraftClient client) {
        if (!ClientState.synced || ClientState.step == QuestStep.POST_FREE) {
            return;
        }
        TextRenderer font = client.textRenderer;
        QuestStep step = ClientState.step;
        Text title = Text.translatable(step.titleKey()).formatted(Formatting.GOLD);
        Text line = step.counterMax > 0
                ? Text.translatable(step.objectiveKey(), ClientState.counter, step.counterMax)
                : Text.translatable(step.objectiveKey());
        int width = Math.max(font.getWidth(title), font.getWidth(line)) + 12;
        int y = 6;
        g.fill(4, y, 4 + width, y + 30, 0x90000000);
        g.fill(4, y, 6, y + 30, 0xFFE0B040);
        g.drawTextWithShadow(font, title, 10, y + 4, 0xFFFFFF);
        g.drawTextWithShadow(font, line, 10, y + 17, 0xE8E8E8);
        compass(g, client, font, y + 34);
    }

    private static boolean holdsCompass(MinecraftClient client) {
        for (ItemStack s : new ItemStack[] {client.player.getMainHandStack(), client.player.getOffHandStack()}) {
            if (s.getItem() instanceof SpecItem item && item.spec.id.equals("chromatic_compass")) {
                return true;
            }
        }
        return false;
    }

    /** The compass shows an arrow and the distance, never a beam in the world. */
    private static void compass(DrawContext g, MinecraftClient client, TextRenderer font, int y) {
        if (!holdsCompass(client) || ClientState.compassTarget == null
                || !ClientState.compassDimension.equals(Dimensions.idOf(client.world))) {
            return;
        }
        Vec3d d = Vec3d.ofCenter(ClientState.compassTarget).subtract(client.player.getPos());
        double bearing = Math.toDegrees(Math.atan2(-d.x, d.z));
        double relative = MathHelper.wrapDegrees(bearing - client.player.getYaw());
        String arrow = Math.abs(relative) > 135 ? "↓" : relative > 45 ? "→" : relative < -45 ? "←" : "↑";
        int meters = (int) Math.round(Math.sqrt(d.x * d.x + d.z * d.z));
        Text text = Text.literal(arrow + "  " + meters + " m").formatted(Formatting.LIGHT_PURPLE);
        g.fill(4, y, 12 + font.getWidth(text) + 4, y + 14, 0x90000000);
        g.drawTextWithShadow(font, text, 8, y + 3, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ abilities

    private static void abilities(DrawContext g, MinecraftClient client, int w, int h) {
        TextRenderer font = client.textRenderer;
        int x = w - 26;
        int y = h - 70;
        for (Ability a : Ability.values()) {
            if (!ClientState.has(a)) {
                continue;
            }
            ItemStack icon = new ItemStack(switch (a) {
                case DASH -> Items.FEATHER;
                case SPRING_INSOLES -> Items.SLIME_BALL;
                case GLIDER -> Items.ELYTRA;
            });
            g.fill(x - 2, y - 2, x + 20, y + 20, 0x90000000);
            g.drawItem(icon, x, y);
            int left = ClientState.cooldownLeft(a);
            int total = Math.max(1, ClientState.COOLDOWN_TOTAL[a.ordinal()]);
            if (left > 0) {
                int fill = (int) (18.0 * left / total);
                g.fill(x - 1, y + 19 - fill, x + 19, y + 19, 0xA0303030);
                String sec = String.format("%.1f", left / 20.0);
                g.drawTextWithShadow(font, sec, x + 9 - font.getWidth(sec) / 2, y + 5, 0xFFFFFF);
            } else if (a == Ability.DASH) {
                g.drawTextWithShadow(font, Keys.dash.getBoundKeyLocalizedText(), x + 2, y + 21, 0xC0FFC0);
            } else if (a == Ability.GLIDER) {
                g.drawTextWithShadow(font, Text.translatable("key.jump"), x - 4, y + 21, ClientState.gliding ? 0xFFE080 : 0xC0FFC0);
            }
            y -= 26;
        }
        if (ClientState.rings > 0) {
            Text ring = Text.translatable("hud.lewandivka.ring", ClientState.rings).formatted(Formatting.AQUA);
            g.drawTextWithShadow(font, ring, w - font.getWidth(ring) - 6, 6, 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------ the chromatic charge

    private static void charge(DrawContext g, MinecraftClient client, int w, int h) {
        if (!ClientState.chargeActive) {
            return;
        }
        TextRenderer font = client.textRenderer;
        boolean mine = client.player.getUuid().equals(ClientState.chargeHolder);
        float f = MathHelper.clamp(ClientState.chargeFraction, 0f, 1f);
        int seconds = (int) Math.ceil(f * 12.0);
        int bw = 140;
        int x = w / 2 - bw / 2;
        int y = h - 62;
        int color = f > 0.5f ? 0xFF60D060 : f > 0.25f ? 0xFFE0A030 : 0xFFE04040;
        g.fill(x - 2, y - 2, x + bw + 2, y + 10, 0xA0000000);
        g.fill(x, y, x + (int) (bw * f), y + 8, color);
        Text label = Text.translatable("hud.lewandivka.charge", seconds);
        g.drawCenteredTextWithShadow(font, label, w / 2, y - 12, mine ? 0xFFFFFF : 0xB0B0B0);
        if (mine) {
            g.drawCenteredTextWithShadow(font, Text.translatable("hud.lewandivka.charge.pass"), w / 2, y + 12, 0xFFE080);
        }
    }

    // ------------------------------------------------------------------ cinematics

    private static void cinematic(DrawContext g, MinecraftClient client, int w, int h, float tickDelta) {
        int t = ClientState.cinematicTicks;
        int length = Math.max(1, ClientState.cinematicLength);
        float progress = (t + tickDelta) / length;
        float in = MathHelper.clamp(t / 20.0f, 0, 1);
        float out = MathHelper.clamp((length - t) / 20.0f, 0, 1);
        float bars = Math.min(in, out);
        int bar = (int) (h * 0.12f * bars);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        String id = ClientState.cinematic;
        boolean flashes = LewandivkaConfig.get().flashEffects;
        // the crossing: the colours of the district slide towards pink and white
        if (id.equals("transition")) {
            float hue = progress * 0.9f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0xFFFFFF;
            int a = (int) (MathHelper.clamp(progress * 1.4f - 0.1f, 0, 1) * (flashes ? 235 : 150));
            g.fill(0, 0, w, h, (a << 24) | rgb);
        } else if (id.equals("rescue_ring") || id.equals("ring_restored")) {
            float pulse = progress < 0.5f ? progress * 2 : (1 - progress) * 2;
            int a = (int) (pulse * (flashes ? 200 : 110));
            g.fill(0, 0, w, h, (a << 24) | (id.equals("ring_restored") ? 0xE0E0E0 : 0xFFFFFF));
        }
        Text title = Text.translatable("cinematic.lewandivka." + id);
        float alpha = MathHelper.clamp(Math.min(in, out) * 1.5f, 0, 1);
        if (alpha > 0.05f) {
            int a = (int) (alpha * 255) << 24;
            g.drawCenteredTextWithShadow(client.textRenderer, title, w / 2, h - bar - 24, 0x00FFFFFF | a);
        }
    }
}
