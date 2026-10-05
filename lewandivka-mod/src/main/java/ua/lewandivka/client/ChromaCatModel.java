package ua.lewandivka.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;
import ua.lewandivka.entity.ChromaCatEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Пухнаста котяча модель: об'ємні вуха, щічки-пучки, комір на грудях, пухнастий хвіст. */
public class ChromaCatModel extends SinglePartEntityModel<ChromaCatEntity> {
    private final ModelPart root;
    private final ModelPart body, chest, head, tail, tail2, legFL, legFR, legBL, legBR;
    private final Map<ModelPart, ModelTransform> defaults = new LinkedHashMap<>();

    public ChromaCatModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.chest = root.getChild("chest");
        this.head = root.getChild("head");
        this.tail = root.getChild("tail");
        this.tail2 = tail.getChild("tail2");
        this.legFL = root.getChild("leg_fl");
        this.legFR = root.getChild("leg_fr");
        this.legBL = root.getChild("leg_bl");
        this.legBR = root.getChild("leg_br");
        for (ModelPart p : new ModelPart[]{body, chest, head, tail, tail2, legFL, legFR, legBL, legBR}) {
            defaults.put(p, p.getTransform());
        }
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(ChromaCatEntity entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        defaults.forEach(ModelPart::setTransform);
        for (ModelPart leg : new ModelPart[]{legFL, legFR, legBL, legBR}) {
            leg.visible = true;
        }
        int pose = entity.getCatPose();
        head.yaw = headYaw * 0.017453292f;
        head.pitch = headPitch * 0.017453292f;
        tail.yaw = MathHelper.sin(age * 0.09f) * 0.3f;
        tail2.yaw = MathHelper.sin(age * 0.09f - 0.8f) * 0.25f;
        if (pose == ChromaCatEntity.POSE_NORMAL) {
            float swing = limbDistance * 1.2f;
            legFL.pitch = MathHelper.cos(limbAngle * 0.6662f) * swing;
            legBR.pitch = MathHelper.cos(limbAngle * 0.6662f) * swing;
            legFR.pitch = MathHelper.cos(limbAngle * 0.6662f + MathHelper.PI) * swing;
            legBL.pitch = MathHelper.cos(limbAngle * 0.6662f + MathHelper.PI) * swing;
            tail.pitch = (entity.isChinazik() ? 0.95f : 0.55f) - limbDistance * 0.3f;
            tail2.pitch = entity.isChinazik() ? -0.35f : 0.15f;
        } else {
            // Котячий «батон»: лапи підібрані, тіло на підлозі, хвіст обвиває бік.
            float drop = 4.5f;
            body.pivotY += drop;
            chest.pivotY += drop;
            head.pivotY += drop - 0.5f;
            tail.pivotY += drop + 2.5f;
            for (ModelPart leg : new ModelPart[]{legFL, legFR, legBL, legBR}) {
                leg.visible = false;
            }
            tail.pitch = -0.1f;
            tail.yaw = 1.15f + MathHelper.sin(age * 0.05f) * 0.06f;
            tail2.pitch = 0f;
            tail2.yaw = 1.0f;
            if (pose == ChromaCatEntity.POSE_SLEEP) {
                head.pitch = 0.45f;
                head.yaw = 0.35f;
                head.pivotY += 0.6f;
                tail.yaw = 1.4f;
                tail2.yaw = 1.3f;
            }
        }
    }
}
