package com.lewandivka.client.render;

import com.lewandivka.core.registry.EntitySpec;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.Entity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** One renderer for every entity of the mod: the catalog says which model, texture and scale it uses. */
public final class LewRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {

    public LewRenderer(EntityRendererFactory.Context context, EntitySpec spec) {
        super(context, new LewModel<>(spec));
        this.shadowRadius = Math.max(0.2f, spec.width * 0.5f);
        withScale(spec.scale);
    }
}
