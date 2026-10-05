package ua.lewandivka.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SlimeEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.entity.boss.LadyWhirlEntity;

/** Пані Вирва: прозора оболонка, всередині — басейн з очима. */
public class LadyWhirlRenderer extends MobEntityRenderer<LadyWhirlEntity, SlimeEntityModel<LadyWhirlEntity>> {
    private static final Identifier TEXTURE = Lewandivka.id("textures/entity/lady_whirl.png");

    public LadyWhirlRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SlimeEntityModel<>(ctx.getPart(EntityModelLayers.SLIME)), 2.5f);
        this.addFeature(new Shell(this, new SlimeEntityModel<>(ctx.getPart(EntityModelLayers.SLIME_OUTER))));
    }

    @Override
    public Identifier getTexture(LadyWhirlEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(LadyWhirlEntity entity, MatrixStack matrices, float amount) {
        float wobble = MathHelper.sin((entity.age + amount) * 0.12f) * 0.25f;
        matrices.scale(6.5f + wobble, 6.5f - wobble, 6.5f + wobble);
    }

    private static class Shell extends FeatureRenderer<LadyWhirlEntity, SlimeEntityModel<LadyWhirlEntity>> {
        private final SlimeEntityModel<LadyWhirlEntity> model;

        Shell(FeatureRendererContext<LadyWhirlEntity, SlimeEntityModel<LadyWhirlEntity>> context, SlimeEntityModel<LadyWhirlEntity> model) {
            super(context);
            this.model = model;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LadyWhirlEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            getContextModel().copyStateTo(model);
            model.animateModel(entity, limbAngle, limbDistance, tickDelta);
            model.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(getTexture(entity)));
            model.render(matrices, vc, light, LivingEntityRenderer.getOverlay(entity, 0.0f), 1f, 1f, 1f, 1f);
        }
    }
}
