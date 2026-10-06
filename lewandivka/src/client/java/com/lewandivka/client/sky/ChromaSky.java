package com.lewandivka.client.sky;

import com.lewandivka.client.ClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The sky of Chromandivka: a pink dome, three suns and the giant rainbow ring. Only restored fragments of the ring are
 * solid: the others are faint and broken. Everything is drawn from plain colours plus the vanilla sun texture, so the
 * sky needs no extra assets and keeps working with shader packs (it is a normal sky render).
 */
final class ChromaSky {

    private static final Identifier SUN = new Identifier("textures/environment/sun.png");
    private static final int SEGMENTS = 48;
    private static final float RADIUS = 100.0f;

    private ChromaSky() {
    }

    static void render(WorldRenderContext context) {
        Matrix4f m = context.matrixStack().peek().getPositionMatrix();
        float time = (context.world().getTime() + context.tickDelta()) / 20.0f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        dome(m, time);
        ring(m, time);
        suns(m, time);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static Vector3f dir(double azimuthDeg, double elevationDeg, float radius) {
        double az = Math.toRadians(azimuthDeg);
        double el = Math.toRadians(elevationDeg);
        return new Vector3f((float) (Math.cos(el) * Math.sin(az)) * radius, (float) Math.sin(el) * radius, (float) (Math.cos(el) * -Math.cos(az)) * radius);
    }

    // ------------------------------------------------------------------ dome

    private static void dome(Matrix4f m, float time) {
        float pulse = 0.5f + 0.5f * MathHelper.sin(time * 0.05f);
        float[][] rings = {
                // elevation, r, g, b, a
                {-12, 1.00f, 0.55f, 0.80f, 0.0f},
                {0, 1.00f, 0.58f, 0.82f, 0.95f},
                {18, 0.98f, 0.45f, 0.78f, 0.9f},
                {45, 0.85f, 0.38f, 0.80f, 0.85f + 0.05f * pulse},
                {90, 0.55f, 0.30f, 0.78f, 0.9f}
        };
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int r = 0; r < rings.length - 1; r++) {
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = 360.0 * i / SEGMENTS;
                double a1 = 360.0 * (i + 1) / SEGMENTS;
                vertex(b, m, dir(a0, rings[r][0], RADIUS * 1.1f), rings[r]);
                vertex(b, m, dir(a1, rings[r][0], RADIUS * 1.1f), rings[r]);
                vertex(b, m, dir(a1, rings[r + 1][0], RADIUS * 1.1f), rings[r + 1]);
                vertex(b, m, dir(a0, rings[r + 1][0], RADIUS * 1.1f), rings[r + 1]);
            }
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private static void vertex(BufferBuilder b, Matrix4f m, Vector3f p, float[] c) {
        b.vertex(m, p.x, p.y, p.z).color(c[1], c[2], c[3], c[4]).next();
    }

    // ------------------------------------------------------------------ the rainbow ring

    private static final float[][] BANDS = {
            {0.95f, 0.20f, 0.25f}, {1.00f, 0.55f, 0.15f}, {1.00f, 0.90f, 0.25f}, {0.30f, 0.85f, 0.35f},
            {0.20f, 0.75f, 0.95f}, {0.35f, 0.40f, 0.95f}, {0.70f, 0.35f, 0.90f}
    };

    /** A huge circle tilted against the horizon, seen from inside: its four quarters are the four fragments. */
    private static void ring(Matrix4f m, float time) {
        int restored = Math.max(0, Math.min(4, ClientState.rings));
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float radius = 84.0f;
        float width = 1.6f;
        float tilt = (float) Math.toRadians(62);
        float shimmer = 0.5f + 0.5f * MathHelper.sin(time * 0.8f);
        int steps = 128;
        for (int i = 0; i < steps; i++) {
            int quarter = i * 4 / steps;
            boolean whole = quarter < restored || restored >= 4;
            // damaged quarters: broken into dashes and faint
            if (!whole && (i % 6) >= 4) {
                continue;
            }
            float alpha = whole ? 0.92f : 0.28f;
            float a0 = (float) (2 * Math.PI * i / steps);
            float a1 = (float) (2 * Math.PI * (i + 1) / steps);
            for (int band = 0; band < BANDS.length; band++) {
                float off0 = (band - BANDS.length / 2.0f) * width;
                float off1 = off0 + width;
                float[] c = BANDS[band];
                float r = c[0] * (whole ? 0.85f + 0.15f * shimmer : 1f);
                float g = c[1] * (whole ? 0.85f + 0.15f * shimmer : 1f);
                float bl = c[2] * (whole ? 0.85f + 0.15f * shimmer : 1f);
                if (!whole) {
                    r = 0.5f + r * 0.3f;
                    g = 0.5f + g * 0.3f;
                    bl = 0.5f + bl * 0.3f;
                }
                ringVertex(b, m, a0, radius + off0, tilt, r, g, bl, alpha);
                ringVertex(b, m, a1, radius + off0, tilt, r, g, bl, alpha);
                ringVertex(b, m, a1, radius + off1, tilt, r, g, bl, alpha);
                ringVertex(b, m, a0, radius + off1, tilt, r, g, bl, alpha);
            }
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
    }

    private static void ringVertex(BufferBuilder b, Matrix4f m, float angle, float radius, float tilt, float r, float g, float bl, float a) {
        float x = radius * MathHelper.cos(angle);
        float y0 = radius * MathHelper.sin(angle);
        // the circle lies in a plane tilted around the x axis and shifted so that it arches over the horizon
        float y = y0 * MathHelper.sin(tilt) + 6.0f;
        float z = -y0 * MathHelper.cos(tilt);
        b.vertex(m, x, y, z).color(r, g, bl, a).next();
    }

    // ------------------------------------------------------------------ suns

    private static void suns(Matrix4f m, float time) {
        sun(m, 200 + time * 0.03, 52, 20.0f, 1.0f, 0.95f, 0.75f);
        sun(m, 262 + time * 0.025, 33, 13.0f, 1.0f, 0.62f, 0.30f);
        sun(m, 140 + time * 0.035, 24, 9.0f, 0.75f, 0.90f, 1.0f);
    }

    private static void sun(Matrix4f m, double azimuth, double elevation, float size, float r, float g, float bl) {
        Vector3f center = dir(azimuth, elevation, RADIUS);
        // a quad that faces the origin
        Vector3f n = new Vector3f(center).normalize();
        Vector3f up = Math.abs(n.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(n).cross(up).normalize().mul(size);
        Vector3f top = new Vector3f(right).cross(n).normalize().mul(size);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, SUN);
        RenderSystem.setShaderColor(r, g, bl, 1.0f);
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        b.vertex(m, center.x - right.x - top.x, center.y - right.y - top.y, center.z - right.z - top.z).texture(0, 0).next();
        b.vertex(m, center.x + right.x - top.x, center.y + right.y - top.y, center.z + right.z - top.z).texture(1, 0).next();
        b.vertex(m, center.x + right.x + top.x, center.y + right.y + top.y, center.z + right.z + top.z).texture(1, 1).next();
        b.vertex(m, center.x - right.x + top.x, center.y - right.y + top.y, center.z - right.z + top.z).texture(0, 1).next();
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
