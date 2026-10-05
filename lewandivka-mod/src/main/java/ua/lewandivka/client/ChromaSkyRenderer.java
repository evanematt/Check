package ua.lewandivka.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Небо Хромандівки: рожево-персиковий градієнт, три сонця різних кольорів,
 * веселкове кільце, а вночі — зорі й місяць з велетенським котячим слідом.
 */
public class ChromaSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
    private final float[][] stars = new float[700][4];

    public ChromaSkyRenderer() {
        Random r = Random.create(10842L);
        for (float[] s : stars) {
            double x = r.nextFloat() * 2 - 1, y = r.nextFloat() * 2 - 1, z = r.nextFloat() * 2 - 1;
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 0.01) {
                len = 1;
            }
            s[0] = (float) (x / len * 100);
            s[1] = (float) (y / len * 100);
            s[2] = (float) (z / len * 100);
            s[3] = 0.15f + r.nextFloat() * 0.35f;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float[] mix(int day, int night, float t) {
        return new float[]{
                lerp(((night >> 16) & 255) / 255f, ((day >> 16) & 255) / 255f, t),
                lerp(((night >> 8) & 255) / 255f, ((day >> 8) & 255) / 255f, t),
                lerp((night & 255) / 255f, (day & 255) / 255f, t)};
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float z, float[] c, float a) {
        bb.vertex(m, x, y, z).color(c[0], c[1], c[2], a).next();
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float y, float size, float[] c, float a) {
        v(bb, m, -size, y, -size, c, a);
        v(bb, m, size, y, -size, c, a);
        v(bb, m, size, y, size, c, a);
        v(bb, m, -size, y, size, c, a);
    }

    @Override
    public void render(WorldRenderContext context) {
        ClientWorld world = context.world();
        MatrixStack ms = context.matrixStack();
        float tickDelta = context.tickDelta();
        float angle = world.getSkyAngle(tickDelta);
        float day = MathHelper.clamp(MathHelper.cos(angle * MathHelper.TAU) * 2.0f + 0.5f, 0f, 1f);

        float[] zenith = mix(0xFF5FC8, 0x1A0B3A, day);
        float[] mid = mix(0xFF9AD5, 0x2E1257, day);
        float[] horizon = mix(0xFFD28A, 0x5A2A6E, day);
        float[] below = mix(0xFFB070, 0x24103A, day);

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();

        // Купол-градієнт.
        Matrix4f m = ms.peek().getPositionMatrix();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int seg = 24;
        float[][] rings = {{-100, 60, 0}, {-12, 110, 1}, {35, 95, 2}, {80, 45, 3}, {100, 0, 3}};
        float[][] colors = {below, horizon, mid, zenith};
        for (int i = 0; i < rings.length - 1; i++) {
            float y0 = rings[i][0], r0 = rings[i][1], y1 = rings[i + 1][0], r1 = rings[i + 1][1];
            float[] c0 = colors[(int) rings[i][2]], c1 = colors[(int) rings[i + 1][2]];
            for (int s = 0; s < seg; s++) {
                float a0 = s * MathHelper.TAU / seg, a1 = (s + 1) * MathHelper.TAU / seg;
                v(bb, m, MathHelper.cos(a0) * r0, y0, MathHelper.sin(a0) * r0, c0, 1f);
                v(bb, m, MathHelper.cos(a1) * r0, y0, MathHelper.sin(a1) * r0, c0, 1f);
                v(bb, m, MathHelper.cos(a1) * r1, y1, MathHelper.sin(a1) * r1, c1, 1f);
                v(bb, m, MathHelper.cos(a0) * r1, y1, MathHelper.sin(a0) * r1, c1, 1f);
            }
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());

        // Зорі.
        if (day < 0.9f) {
            bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float[] white = {1f, 0.95f, 1f};
            for (float[] s : stars) {
                float sz = s[3];
                float a = (1f - day) * 0.9f;
                v(bb, m, s[0] - sz, s[1], s[2] - sz, white, a);
                v(bb, m, s[0] + sz, s[1], s[2] - sz, white, a);
                v(bb, m, s[0] + sz, s[1], s[2] + sz, white, a);
                v(bb, m, s[0] - sz, s[1], s[2] + sz, white, a);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }

        // Веселкове кільце, нахилене над світом.
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(22f));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle * 90f));
        Matrix4f rm = ms.peek().getPositionMatrix();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int ringSeg = 64;
        for (int s = 0; s < ringSeg; s++) {
            float a0 = s * MathHelper.TAU / ringSeg, a1 = (s + 1) * MathHelper.TAU / ringSeg;
            int rgb = MathHelper.hsvToRgb(s / (float) ringSeg, 0.6f, 1f);
            float[] c = {((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
            float alpha = 0.28f + 0.2f * (1f - day);
            float in = 70, out = 82, h = 60;
            v(bb, rm, MathHelper.cos(a0) * in, h, MathHelper.sin(a0) * in, c, alpha);
            v(bb, rm, MathHelper.cos(a1) * in, h, MathHelper.sin(a1) * in, c, alpha);
            v(bb, rm, MathHelper.cos(a1) * out, h, MathHelper.sin(a1) * out, c, alpha * 0.4f);
            v(bb, rm, MathHelper.cos(a0) * out, h, MathHelper.sin(a0) * out, c, alpha * 0.4f);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        ms.pop();

        // Три сонця й місяць обертаються з часом доби.
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle * 360f));
        float[][] suns = {{0, 0, 14, 0xFFF4A0}, {28, 12, 7, 0x7FF6FF}, {-34, -8, 5, 0xFF7AF0}};
        for (float[] sun : suns) {
            ms.push();
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sun[0]));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sun[1]));
            Matrix4f sm = ms.peek().getPositionMatrix();
            int col = (int) sun[3];
            float[] c = {((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f};
            bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            quad(bb, sm, 100, sun[2] * 3.2f, c, 0.10f);
            quad(bb, sm, 100, sun[2] * 1.9f, c, 0.22f);
            quad(bb, sm, 100, sun[2], c, 1.0f);
            BufferRenderer.drawWithGlobalProgram(bb.end());
            ms.pop();
        }
        // Місяць з котячим слідом.
        RenderSystem.defaultBlendFunc();
        Matrix4f mm = ms.peek().getPositionMatrix();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float[] moon = {0.91f, 0.88f, 1f};
        float[] pad = {0.55f, 0.45f, 0.7f};
        float moonA = 0.35f + 0.65f * (1f - day);
        quad(bb, mm, -100, 12, moon, moonA);
        float[][] pads = {{0, 3, 4.2f}, {-5, -3, 1.8f}, {-1.8f, -6, 1.8f}, {1.8f, -6, 1.8f}, {5, -3, 1.8f}};
        for (float[] p : pads) {
            float x = p[0], z = p[1], r = p[2];
            v(bb, mm, x - r, -99.5f, z - r, pad, moonA);
            v(bb, mm, x + r, -99.5f, z - r, pad, moonA);
            v(bb, mm, x + r, -99.5f, z + r, pad, moonA);
            v(bb, mm, x - r, -99.5f, z + r, pad, moonA);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
        ms.pop();

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
