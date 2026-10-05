package ua.lewandivka.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

/** Згенеровано tools/gen_textures.py — геометрія збігається з текстурами котів. */
public final class CatModelData {
    public static TexturedModelData create() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-3.5f, -3.5f, -7.0f, 7.0f, 7.0f, 14.0f), ModelTransform.pivot(0.0f, 15.0f, 0.0f));
        ModelPartData chest = root.addChild("chest", ModelPartBuilder.create().uv(0, 22).cuboid(-3.0f, -0.5f, -3.0f, 6.0f, 6.0f, 3.0f), ModelTransform.pivot(0.0f, 15.0f, -5.0f));
        ModelPartData head = root.addChild("head", ModelPartBuilder.create().uv(0, 32).cuboid(-3.0f, -3.0f, -5.0f, 6.0f, 6.0f, 5.0f), ModelTransform.pivot(0.0f, 13.0f, -7.0f));
        ModelPartData muzzle = head.addChild("muzzle", ModelPartBuilder.create().uv(24, 32).cuboid(-1.5f, 0.5f, -6.0f, 3.0f, 2.0f, 1.0f), ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData ear_l = head.addChild("ear_l", ModelPartBuilder.create().uv(24, 36).cuboid(0.8f, -5.0f, -2.5f, 2.0f, 2.0f, 1.0f).uv(36, 32).cuboid(1.3f, -6.0f, -2.5f, 1.0f, 1.0f, 1.0f), ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData ear_r = head.addChild("ear_r", ModelPartBuilder.create().uv(30, 36).cuboid(-2.8f, -5.0f, -2.5f, 2.0f, 2.0f, 1.0f).uv(40, 32).cuboid(-2.3f, -6.0f, -2.5f, 1.0f, 1.0f, 1.0f), ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData cheek_l = head.addChild("cheek_l", ModelPartBuilder.create().uv(44, 0).cuboid(3.0f, -0.5f, -3.5f, 1.0f, 3.0f, 3.0f), ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData cheek_r = head.addChild("cheek_r", ModelPartBuilder.create().uv(44, 6).cuboid(-4.0f, -0.5f, -3.5f, 1.0f, 3.0f, 3.0f), ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        ModelPartData tail = root.addChild("tail", ModelPartBuilder.create().uv(0, 46).cuboid(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f), ModelTransform.pivot(0.0f, 13.0f, 6.5f));
        ModelPartData tail2 = tail.addChild("tail2", ModelPartBuilder.create().uv(20, 46).cuboid(-1.5f, -1.5f, 0.0f, 3.0f, 3.0f, 6.0f), ModelTransform.pivot(0.0f, 0.0f, 6.0f));
        ModelPartData leg_fl = root.addChild("leg_fl", ModelPartBuilder.create().uv(44, 12).cuboid(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), ModelTransform.pivot(1.8f, 18.0f, -5.0f));
        ModelPartData leg_fr = root.addChild("leg_fr", ModelPartBuilder.create().uv(52, 12).cuboid(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), ModelTransform.pivot(-1.8f, 18.0f, -5.0f));
        ModelPartData leg_bl = root.addChild("leg_bl", ModelPartBuilder.create().uv(44, 20).cuboid(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), ModelTransform.pivot(1.8f, 18.0f, 5.0f));
        ModelPartData leg_br = root.addChild("leg_br", ModelPartBuilder.create().uv(52, 20).cuboid(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f), ModelTransform.pivot(-1.8f, 18.0f, 5.0f));
        return TexturedModelData.of(data, 64, 64);
    }

    private CatModelData() {
    }
}
