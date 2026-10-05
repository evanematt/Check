package ua.lewandivka.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/** Гуманоїд зі своєю шкіркою і масштабом (боси, Боржник). */
public class SkinnedBipedRenderer<T extends MobEntity> extends MobEntityRenderer<T, BipedEntityModel<T>> {
    private final Identifier texture;
    private final float scale;

    public SkinnedBipedRenderer(EntityRendererFactory.Context ctx, Identifier texture, float scale) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f * scale);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    public Identifier getTexture(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, MatrixStack matrices, float amount) {
        matrices.scale(scale, scale, scale);
    }
}
