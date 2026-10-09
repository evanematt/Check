package com.lewandivka.client.render;

import com.lewandivka.block.GameBlocks;
import com.lewandivka.block.SpecBlock;
import com.lewandivka.core.registry.BlockSpec.Model;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.entity.GameEntities;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityType;

/** Entity renderers (GeckoLib) and the render layers of the see-through blocks. */
public final class Renderers {

    private Renderers() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        for (EntitySpec spec : ModEntities.ALL) {
            EntityType type = GameEntities.type(spec.id);
            EntityRendererRegistry.register(type, ctx -> new LewRenderer(ctx, spec));
        }
        for (SpecBlock block : GameBlocks.all()) {
            if (block.spec.translucent) {
                BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getTranslucent());
            } else if (block.spec.cutout || needsCutout(block.spec.model)) {
                BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutout());
            }
        }
    }

    private static boolean needsCutout(Model model) {
        return switch (model) {
            case CROSS, CLUSTER, LAMP, SMALL, PEDESTAL, PLATE -> true;
            default -> false;
        };
    }
}
