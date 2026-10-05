package ua.lewandivka.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import ua.lewandivka.network.ModNetworking;

import java.util.List;

/** Маленька панель завдання в лівому верхньому куті. */
public final class QuestHud {
    private static String title = "";
    private static String desc = "";

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.HUD, (client, handler, buf, sender) -> {
            String t = buf.readString();
            String d = buf.readString();
            client.execute(() -> {
                title = t;
                desc = d;
            });
        });
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (title.isEmpty() || mc.options.hudHidden || mc.options.debugEnabled) {
                return;
            }
            TextRenderer tr = mc.textRenderer;
            int width = 190;
            List<OrderedText> lines = tr.wrapLines(Text.literal(desc), width);
            int h = 16 + lines.size() * 10;
            ctx.fill(4, 4, 4 + width + 8, 4 + h, 0x99140A28);
            ctx.fill(4, 4, 6, 4 + h, 0xFFFF5FC8);
            ctx.drawTextWithShadow(tr, Text.literal("◆ " + title), 10, 8, 0xFFFFD28A);
            int y = 20;
            for (OrderedText line : lines) {
                ctx.drawTextWithShadow(tr, line, 10, y, 0xFFE8E0FF);
                y += 10;
            }
        });
    }

    private QuestHud() {
    }
}
