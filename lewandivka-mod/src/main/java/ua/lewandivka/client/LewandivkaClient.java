package ua.lewandivka.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.network.ModNetworking;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModWorldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LewandivkaClient implements ClientModInitializer {
    public static final EntityModelLayer CAT_LAYER = new EntityModelLayer(Lewandivka.id("chroma_cat"), "main");

    private static Identifier tex(String name) {
        return Lewandivka.id("textures/entity/" + name + ".png");
    }

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(CAT_LAYER, CatModelData::create);
        EntityRendererRegistry.register(ModEntities.CHINAZIK, ChromaCatRenderer::new);
        EntityRendererRegistry.register(ModEntities.METADONNA, ChromaCatRenderer::new);

        EntityRendererRegistry.<ZombieEntity>register(ModEntities.GOPNIK, ctx -> new GopnikRenderer(ctx, false, tex("gopnik"), tex("gopnik_zombie"), tex("gopnik_cap")));
        EntityRendererRegistry.<ZombieEntity>register(ModEntities.SHADE, ctx -> new GopnikRenderer(ctx, true, tex("shade")));
        EntityRendererRegistry.<ZombieEntity>register(ModEntities.COLORLESS, ctx -> new GopnikRenderer(ctx, false, tex("colorless")));
        EntityRendererRegistry.register(ModEntities.BORZHNYK, ctx -> new SkinnedBipedRenderer<>(ctx, tex("borzhnyk"), 1.0f));
        EntityRendererRegistry.register(ModEntities.GARAGE_KING, ctx -> new SkinnedBipedRenderer<>(ctx, tex("garage_king"), 2.2f));
        EntityRendererRegistry.register(ModEntities.CONDUCTOR, ctx -> new SkinnedBipedRenderer<>(ctx, tex("conductor"), 1.5f));
        EntityRendererRegistry.register(ModEntities.COLLECTOR, ctx -> new SkinnedBipedRenderer<>(ctx, tex("collector"), 1.3f));
        EntityRendererRegistry.register(ModEntities.COLORLESS_HEAD, ctx -> new SkinnedBipedRenderer<>(ctx, tex("colorless_head"), 3.0f));
        EntityRendererRegistry.register(ModEntities.LADY_WHIRL, LadyWhirlRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.RAINBOW_LEAVES, RenderLayer.getCutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.CHROMA_BARS, ModBlocks.GUARD_DOOR, ModBlocks.TINY_BOX,
                ModBlocks.SURPRISE, ModBlocks.LITTER_BOX, ModBlocks.LAUNCH_PAD, ModBlocks.DOOR_MAT);

        DimensionRenderingRegistry.registerSkyRenderer(ModWorldgen.CHROMA, new ChromaSkyRenderer());
        QuestHud.init();

        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.OPEN_CAT_MENU, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String name = buf.readString();
            int mode = buf.readVarInt();
            int n = buf.readVarInt();
            List<CatCommandScreen.PlayerEntry> players = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUuid();
                players.add(new CatCommandScreen.PlayerEntry(id, buf.readString()));
            }
            client.execute(() -> client.setScreen(new CatCommandScreen(entityId, name, mode, players)));
        });
    }
}
