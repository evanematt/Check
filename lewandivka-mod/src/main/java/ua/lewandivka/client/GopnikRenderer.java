package ua.lewandivka.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Гопники, тіньові безквиткові (напівпрозорі) і безбарвники. */
public class GopnikRenderer extends ZombieEntityRenderer {
    private final Identifier[] textures;
    private final boolean translucent;

    public GopnikRenderer(EntityRendererFactory.Context ctx, boolean translucent, Identifier... textures) {
        super(ctx);
        this.textures = textures;
        this.translucent = translucent;
    }

    @Override
    public Identifier getTexture(ZombieEntity entity) {
        return textures[Math.floorMod(entity.getUuid().hashCode(), textures.length)];
    }

    @Override
    @Nullable
    protected RenderLayer getRenderLayer(ZombieEntity entity, boolean showBody, boolean translucent, boolean showOutline) {
        if (this.translucent && showBody) {
            return RenderLayer.getEntityTranslucent(getTexture(entity));
        }
        return super.getRenderLayer(entity, showBody, translucent, showOutline);
    }
}
