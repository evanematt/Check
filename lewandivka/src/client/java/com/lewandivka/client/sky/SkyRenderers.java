package com.lewandivka.client.sky;

import com.lewandivka.world.dimension.Dimensions;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

public final class SkyRenderers {

    private SkyRenderers() {
    }

    public static void register() {
        DimensionRenderingRegistry.registerSkyRenderer(Dimensions.CHROMA, ChromaSky::render);
    }
}
