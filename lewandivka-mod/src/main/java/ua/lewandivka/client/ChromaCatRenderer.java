package ua.lewandivka.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.entity.ChromaCatEntity;

public class ChromaCatRenderer extends MobEntityRenderer<ChromaCatEntity, ChromaCatModel> {
    private static final Identifier CHINAZIK = Lewandivka.id("textures/entity/chinazik.png");
    private static final Identifier CHINAZIK_SLEEP = Lewandivka.id("textures/entity/chinazik_sleep.png");
    private static final Identifier METADONNA = Lewandivka.id("textures/entity/metadonna.png");
    private static final Identifier METADONNA_SLEEP = Lewandivka.id("textures/entity/metadonna_sleep.png");

    public ChromaCatRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ChromaCatModel(ctx.getPart(LewandivkaClient.CAT_LAYER)), 0.45f);
    }

    @Override
    public Identifier getTexture(ChromaCatEntity entity) {
        boolean sleep = entity.getCatPose() == ChromaCatEntity.POSE_SLEEP;
        if (entity.isChinazik()) {
            return sleep ? CHINAZIK_SLEEP : CHINAZIK;
        }
        return sleep ? METADONNA_SLEEP : METADONNA;
    }

    @Override
    protected void scale(ChromaCatEntity entity, MatrixStack matrices, float amount) {
        float s = entity.isChinazik() ? 1.3f : 0.78f;
        matrices.scale(s, s, s);
    }
}
