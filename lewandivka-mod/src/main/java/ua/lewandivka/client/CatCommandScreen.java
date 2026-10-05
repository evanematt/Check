package ua.lewandivka.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.network.ModNetworking;

import java.util.List;
import java.util.UUID;

/** Меню команд: будь-хто з гравців командує будь-яким котом. */
public class CatCommandScreen extends Screen {
    public record PlayerEntry(UUID id, String name) {
    }

    private final int entityId;
    private final String catName;
    private final int mode;
    private final List<PlayerEntry> players;

    public CatCommandScreen(int entityId, String catName, int mode, List<PlayerEntry> players) {
        super(Text.literal(catName));
        this.entityId = entityId;
        this.catName = catName;
        this.mode = mode;
        this.players = players;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2 - 70;
        addDrawableChild(ButtonWidget.builder(Text.literal("Сидіти / чекати"), b -> send(ChromaCatEntity.CMD_SIT, null))
                .dimensions(cx - 152, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Йти за мною"), b -> send(ChromaCatEntity.CMD_FOLLOW_ME, null))
                .dimensions(cx + 2, y, 150, 20).build());
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Гуляти поруч"), b -> send(ChromaCatEntity.CMD_WANDER, null))
                .dimensions(cx - 152, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("До будинку"), b -> send(ChromaCatEntity.CMD_HOME, null))
                .dimensions(cx + 2, y, 150, 20).build());
        y += 24;
        String help = catName.startsWith("Чіназік") ? "Допомогти: «Відкрийте, бляха!»" : "Допомогти: пролізти і муркнути";
        addDrawableChild(ButtonWidget.builder(Text.literal(help), b -> send(ChromaCatEntity.CMD_HELP, null))
                .dimensions(cx - 152, y, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Тепер дім тут"), b -> send(ChromaCatEntity.CMD_SET_HOME, null))
                .dimensions(cx + 2, y, 150, 20).build());
        y += 30;
        UUID self = MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getUuid() : null;
        int col = 0;
        for (PlayerEntry p : players) {
            if (p.id().equals(self)) {
                continue;
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("Йти за: " + p.name()), b -> send(ChromaCatEntity.CMD_FOLLOW_PLAYER, p.id()))
                    .dimensions(col == 0 ? cx - 152 : cx + 2, y, 150, 20).build());
            col++;
            if (col == 2) {
                col = 0;
                y += 24;
            }
        }
    }

    private void send(int cmd, @Nullable UUID target) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeVarInt(cmd);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUuid(target);
        }
        ClientPlayNetworking.send(ModNetworking.CAT_COMMAND, buf);
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        String state = switch (mode) {
            case ChromaCatEntity.MODE_SIT -> "сидить";
            case ChromaCatEntity.MODE_FOLLOW -> "йде за кимось";
            case ChromaCatEntity.MODE_HOME -> "іде додому";
            default -> "гуляє";
        };
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(catName + " — зараз " + state), width / 2, height / 2 - 92, 0xFFC8F0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Будь-хто з вас може дати команду. Діє остання."), width / 2, height / 2 - 82, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
