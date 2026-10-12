package com.lewandivka.core.tools;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.Materials;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Development aid: draws blueprints as PNG images (top view, floor-plan slice, front/side
 * elevation) so structures can be inspected without launching the game. Pure AWT, headless.
 */
public final class BlueprintRenderer {

    private BlueprintRenderer() {
    }

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public static Color colorOf(String key) {
        if (key == null) {
            return null;
        }
        String id = Keys.blockId(key);
        String n = id.substring(id.indexOf(':') + 1);
        boolean mod = id.startsWith("lewandivka:");
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("light") || n.equals("structure_void")) {
            return null;
        }
        if (mod) {
            return switch (n) {
                case "guard_door" -> new Color(60, 60, 70);
                case "collector_door" -> new Color(150, 60, 150);
                case "shelter_door" -> new Color(90, 110, 90);
                case "grey_void" -> new Color(120, 120, 120);
                case "supply_stash" -> new Color(30, 200, 30);
                case "clue_prop" -> new Color(255, 120, 0);
                case "garage_power_panel" -> new Color(255, 220, 0);
                case "heavy_lever" -> new Color(255, 0, 0);
                case "checkpoint_lamp" -> new Color(0, 255, 255);
                case "glowshroom_cap" -> new Color(150, 60, 220);
                case "glowshroom_stem" -> new Color(235, 225, 200);
                case "rainbow_leaves" -> new Color(255, 100, 200);
                case "chromatic_crystal", "crystal_cluster" -> new Color(100, 230, 255);
                case "colored_grate" -> new Color(255, 100, 100);
                case "garage_lift" -> new Color(200, 170, 40);
                case "spring_pad", "spring_hatch" -> new Color(255, 80, 180);
                case "waterfall", "waterfall_up" -> new Color(80, 160, 255);
                default -> new Color(255, 0, 255);
            };
        }
        if (n.contains("water")) {
            return new Color(50, 90, 220);
        }
        if (n.contains("lava")) {
            return new Color(255, 120, 0);
        }
        if (n.contains("glass")) {
            return new Color(170, 210, 235);
        }
        if (n.contains("leaves")) {
            return n.contains("azalea") ? new Color(110, 170, 70) : n.contains("birch") ? new Color(110, 160, 60) : new Color(50, 120, 40);
        }
        if (n.equals("grass_block") || n.contains("grass") || n.contains("fern")) {
            return new Color(90, 160, 60);
        }
        if (n.contains("lantern") || n.contains("glowstone") || n.contains("shroomlight") || n.contains("sea_lantern") || n.contains("torch")) {
            return new Color(255, 220, 90);
        }
        String[][] colors = {
                {"light_gray", "170,170,170"}, {"light_blue", "110,170,230"}, {"gray", "90,90,95"}, {"white", "235,235,235"},
                {"black", "25,25,30"}, {"brown", "110,75,45"}, {"red", "170,40,40"}, {"orange", "230,120,30"},
                {"yellow", "235,210,50"}, {"lime", "130,210,40"}, {"green", "60,120,40"}, {"cyan", "30,150,160"},
                {"blue", "50,70,190"}, {"purple", "130,50,170"}, {"magenta", "190,60,180"}, {"pink", "240,150,180"}
        };
        for (String[] c : colors) {
            if (n.contains(c[0])) {
                String[] p = c[1].split(",");
                return new Color(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
        }
        if (n.contains("sandstone") || n.contains("sand")) {
            return new Color(215, 200, 150);
        }
        if (n.contains("dark_oak") || n.contains("spruce")) {
            return new Color(70, 50, 30);
        }
        if (n.contains("birch")) {
            return new Color(210, 200, 150);
        }
        if (n.contains("oak") || n.contains("planks") || n.contains("log") || n.contains("wood")) {
            return new Color(150, 115, 65);
        }
        if (n.contains("stone_brick") || n.contains("deepslate") || n.contains("mud_brick")) {
            return n.contains("mud") ? new Color(135, 105, 80) : new Color(120, 120, 124);
        }
        if (n.contains("brick")) {
            return new Color(150, 80, 70);
        }
        if (n.contains("terracotta")) {
            return new Color(160, 95, 70);
        }
        if (n.contains("copper")) {
            return new Color(70, 160, 140);
        }
        if (n.contains("iron")) {
            return new Color(200, 200, 205);
        }
        if (n.contains("dirt") || n.contains("path") || n.contains("mud")) {
            return new Color(120, 85, 55);
        }
        if (n.contains("deepslate") || n.contains("blackstone")) {
            return new Color(50, 50, 58);
        }
        if (n.contains("quartz") || n.contains("calcite")) {
            return new Color(235, 230, 220);
        }
        if (n.contains("amethyst")) {
            return new Color(160, 110, 210);
        }
        if (n.contains("rail")) {
            return new Color(130, 110, 90);
        }
        return new Color(130, 130, 135);
    }

    private static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    /** Top-down view; brightness follows the height of the topmost block. */
    public static BufferedImage top(Blueprint bp, int scale) {
        BufferedImage img = new BufferedImage(bp.sizeX() * scale, bp.sizeZ() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 40, 48));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int z = 0; z < bp.sizeZ(); z++) {
            for (int x = 0; x < bp.sizeX(); x++) {
                for (int y = bp.sizeY() - 1; y >= 0; y--) {
                    Color c = colorOf(bp.keyAt(x, y, z));
                    if (c != null) {
                        double f = 0.55 + 0.45 * (y / (double) Math.max(1, bp.sizeY() - 1));
                        g.setColor(shade(c, f));
                        g.fillRect(x * scale, z * scale, scale, scale);
                        break;
                    }
                }
            }
        }
        drawMarkers(g, bp, scale, -1, true);
        g.dispose();
        return img;
    }

    /** Horizontal slice at height y (floor plan). Solid blocks are drawn bright, passable ones dim. */
    public static BufferedImage slice(Blueprint bp, int y, int scale) {
        BufferedImage img = new BufferedImage(bp.sizeX() * scale, bp.sizeZ() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 24, 30));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int z = 0; z < bp.sizeZ(); z++) {
            for (int x = 0; x < bp.sizeX(); x++) {
                String key = bp.keyAt(x, y, z);
                Color c = colorOf(key);
                if (c == null) {
                    // show floor below faintly so rooms read as rooms
                    Color below = y > 0 ? colorOf(bp.keyAt(x, y - 1, z)) : null;
                    if (below != null) {
                        g.setColor(shade(below, 0.28));
                        g.fillRect(x * scale, z * scale, scale, scale);
                    }
                    continue;
                }
                Materials.Kind k = Materials.classify(key);
                g.setColor(k == Materials.Kind.PASS ? shade(c, 0.6) : c);
                g.fillRect(x * scale, z * scale, scale, scale);
            }
        }
        drawMarkers(g, bp, scale, y, false);
        g.dispose();
        return img;
    }

    /** View from the north looking south, i.e. onto the front facade (x to the right, y up). */
    public static BufferedImage front(Blueprint bp, int scale) {
        BufferedImage img = new BufferedImage(bp.sizeX() * scale, bp.sizeY() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(110, 160, 220));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int y = 0; y < bp.sizeY(); y++) {
            for (int x = 0; x < bp.sizeX(); x++) {
                for (int z = 0; z < bp.sizeZ(); z++) {
                    Color c = colorOf(bp.keyAt(x, y, z));
                    if (c != null) {
                        double f = 1.0 - 0.4 * (z / (double) Math.max(1, bp.sizeZ() - 1));
                        g.setColor(shade(c, f));
                        g.fillRect(x * scale, (bp.sizeY() - 1 - y) * scale, scale, scale);
                        break;
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    /** View from the east looking west (z to the left... drawn so north is on the left). */
    public static BufferedImage side(Blueprint bp, int scale) {
        BufferedImage img = new BufferedImage(bp.sizeZ() * scale, bp.sizeY() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(110, 160, 220));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int y = 0; y < bp.sizeY(); y++) {
            for (int z = 0; z < bp.sizeZ(); z++) {
                for (int x = bp.sizeX() - 1; x >= 0; x--) {
                    Color c = colorOf(bp.keyAt(x, y, z));
                    if (c != null) {
                        double f = 0.6 + 0.4 * (x / (double) Math.max(1, bp.sizeX() - 1));
                        g.setColor(shade(c, f));
                        g.fillRect(z * scale, (bp.sizeY() - 1 - y) * scale, scale, scale);
                        break;
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    /**
     * Isometric view (painter's algorithm) of the whole blueprint. {@code view} 0..3 chooses the
     * corner the camera looks from; {@code cutY} (or -1) hides everything above that layer so
     * interiors can be inspected.
     */
    public static BufferedImage iso(Blueprint bp, int scale, int view, int cutY) {
        int sxN = bp.sizeX();
        int szN = bp.sizeZ();
        int shN = cutY >= 0 ? Math.min(cutY + 1, bp.sizeY()) : bp.sizeY();
        int tw = scale;           // half width of a block on screen
        int th = Math.max(1, scale / 2); // half height of the top rhombus
        int hh = scale;           // vertical size of a block
        int w = (sxN + szN) * tw + 8;
        int h = (sxN + szN) * th + shN * hh + 8;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(120, 165, 215));
        g.fillRect(0, 0, w, h);
        int ox = szN * tw + 4;
        int oy = shN * hh + 4;
        for (int sum = 0; sum <= sxN + szN - 2; sum++) {
            for (int px = 0; px < sxN; px++) {
                int pz = sum - px;
                if (pz < 0 || pz >= szN) {
                    continue;
                }
                // map view coordinates back to blueprint coordinates
                int x;
                int z;
                switch (view & 3) {
                    case 1 -> { x = pz; z = sxN - 1 - px; }
                    case 2 -> { x = sxN - 1 - px; z = szN - 1 - pz; }
                    case 3 -> { x = szN - 1 - pz; z = px; }
                    default -> { x = px; z = pz; }
                }
                if (x < 0 || x >= sxN || z < 0 || z >= szN) {
                    continue;
                }
                for (int y = 0; y < shN; y++) {
                    Color c = colorOf(bp.keyAt(x, y, z));
                    if (c == null) {
                        continue;
                    }
                    int cx = ox + (px - pz) * tw;
                    int cy = oy + (px + pz) * th - y * hh;
                    int[] topX = {cx, cx + tw, cx, cx - tw};
                    int[] topY = {cy - hh, cy - hh + th, cy - hh + 2 * th, cy - hh + th};
                    g.setColor(shade(c, 1.0));
                    g.fillPolygon(topX, topY, 4);
                    int[] lx = {cx - tw, cx, cx, cx - tw};
                    int[] ly = {cy - hh + th, cy - hh + 2 * th, cy + 2 * th, cy + th};
                    g.setColor(shade(c, 0.78));
                    g.fillPolygon(lx, ly, 4);
                    int[] rx = {cx, cx + tw, cx + tw, cx};
                    int[] ry = {cy - hh + 2 * th, cy - hh + th, cy + th, cy + 2 * th};
                    g.setColor(shade(c, 0.58));
                    g.fillPolygon(rx, ry, 4);
                }
            }
        }
        g.dispose();
        return img;
    }

    private static void drawMarkers(Graphics2D g, Blueprint bp, int scale, int onlyY, boolean all) {
        try {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(8, scale * 2)));
        } catch (Throwable ignored) {
            // font config missing: markers are drawn without labels
        }
        for (Blueprint.Marker m : bp.markers()) {
            if (onlyY >= 0 && !(onlyY >= m.y() && onlyY < m.y() + m.sy())) {
                continue;
            }
            if (m.isRegion()) {
                if (m.name().equals("body")) {
                    continue;
                }
                g.setColor(new Color(255, 255, 0, m.sx() * m.sz() < 40 ? 140 : 0));
                g.fillRect(m.x() * scale, m.z() * scale, m.sx() * scale, m.sz() * scale);
                g.setColor(new Color(255, 255, 0));
                g.drawRect(m.x() * scale, m.z() * scale, m.sx() * scale - 1, m.sz() * scale - 1);
            } else {
                g.setColor(new Color(255, 0, 255));
                g.fillOval(m.x() * scale - 1, m.z() * scale - 1, scale + 2, scale + 2);
            }
            if (scale >= 4) {
                g.setColor(Color.WHITE);
                try {
                    g.drawString(m.name().toLowerCase(Locale.ROOT), m.x() * scale + scale + 2, m.z() * scale + scale);
                } catch (Throwable ignored) {
                    // headless font failure: skip label
                }
            }
        }
    }

    public static void save(BufferedImage img, File f) throws IOException {
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }
}
