package com.lewandivka.core.tools;

import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.gen.DistrictPlan;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * {@code gradle cityMap -Pcx=0 -Pcz=0 -Pspan=340 -Ppx=1020} draws the ground of the district (coloured by height, with contour
 * lines every two blocks and a hill shading) and the footprints of what stands on it, to {@code build/renders/citymap.png}.
 */
public final class CityMapMain {

    private CityMapMain() {
    }

    private static Color tint(int h) {
        // below the level of the tram line: greenish blue; above it: yellow green, olive, brown, grey
        double t = Math.max(0, Math.min(1, (h - 52) / 26.0));
        int r = (int) (60 + 170 * t);
        int g = (int) (150 + 20 * t - 90 * t * t);
        int b = (int) (130 - 100 * t);
        return new Color(Math.min(255, r), Math.max(0, Math.min(255, g)), Math.max(0, b));
    }

    public static void main(String[] args) throws Exception {
        int cx = Integer.parseInt(System.getProperty("cx", "0"));
        int cz = Integer.parseInt(System.getProperty("cz", "0"));
        int span = Integer.parseInt(System.getProperty("span", "340"));
        int px = Integer.parseInt(System.getProperty("px", "1020"));
        File out = new File(System.getProperty("out", "build/renders/citymap.png"));
        DistrictPlan plan = DistrictPlan.get();
        double step = (double) span / px;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        TerrainColumn col = new TerrainColumn();
        int[][] h = new int[px + 1][px + 1];
        for (int j = 0; j <= px; j++) {
            for (int i = 0; i <= px; i++) {
                int x = (int) Math.round(cx - span / 2.0 + i * step);
                int z = (int) Math.round(cz - span / 2.0 + j * step);
                plan.column(x, z, col);
                h[i][j] = col.height;
            }
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                int x = (int) Math.round(cx - span / 2.0 + i * step);
                int z = (int) Math.round(cz - span / 2.0 + j * step);
                plan.column(x, z, col);
                min = Math.min(min, col.height);
                max = Math.max(max, col.height);
                Color c = tint(col.height);
                double shade = 1.0 - 0.06 * ((h[Math.min(i + 1, px)][j] - h[i][j]) + (h[i][Math.min(j + 1, px)] - h[i][j]));
                shade = Math.max(0.65, Math.min(1.3, shade));
                int rr = (int) Math.min(255, c.getRed() * shade);
                int gg = (int) Math.min(255, c.getGreen() * shade);
                int bb = (int) Math.min(255, c.getBlue() * shade);
                if (col.top.contains("concrete") || col.top.contains("andesite") || col.top.equals("minecraft:stone")) {
                    rr = gg = bb = (int) (70 * shade);
                } else if (col.top.contains("bricks") || col.top.contains("cobble")) {
                    rr = gg = bb = (int) (120 * shade);
                }
                if (h[i][j] != h[Math.min(i + 1, px)][j] && h[i][j] % 2 == 0 || h[i][j] != h[i][Math.min(j + 1, px)] && h[i][j] % 2 == 0) {
                    rr = rr * 3 / 4;
                    gg = gg * 3 / 4;
                    bb = bb * 3 / 4;
                }
                img.setRGB(i, j, new Color(rr, gg, bb).getRGB());
            }
        }
        Graphics2D g = img.createGraphics();
        double scale = 1.0 / step;
        for (StructurePlacement sp : plan.fixedPlacements()) {
            String id = sp.id();
            if (id.equals("tree") || id.equals("forest") || id.startsWith("flowers") || id.equals("pole")) {
                continue;
            }
            int x1 = (int) Math.round((sp.x() - (cx - span / 2.0)) * scale);
            int y1 = (int) Math.round((sp.z() - (cz - span / 2.0)) * scale);
            int w = (int) Math.round((sp.maxX() - sp.x() + 1) * scale);
            int hh = (int) Math.round((sp.maxZ() - sp.z() + 1) * scale);
            boolean building = id.startsWith("block") || id.startsWith("house") && !id.contains("_garden") && !id.contains("_fence") && !id.contains("_bush")
                    || id.equals("kindergarten") || id.equals("old_shop") || id.equals("tram_depot") || id.startsWith("garage") || id.equals("tram_stop")
                    || id.startsWith("tower") || id.startsWith("school") || id.startsWith("boiler") || id.startsWith("sub") || id.startsWith("shed");
            g.setColor(building ? new Color(190, 60, 50, 200) : new Color(40, 40, 160, 150));
            if (building) {
                g.fillRect(x1, y1, Math.max(1, w), Math.max(1, hh));
                g.setColor(Color.BLACK);
                g.drawRect(x1, y1, Math.max(1, w), Math.max(1, hh));
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.setColor(Color.WHITE);
                g.drawString(id, x1 + 3, y1 + 11);
            } else {
                g.fillRect(x1, y1, Math.max(1, w), Math.max(1, hh));
            }
        }
        // the places the quests stand on: cats, groups, the chase
        for (StructurePlacement.MarkerPos m : plan.planMarkers()) {
            String id = m.id().substring(m.id().indexOf(':') + 1);
            Color c = id.startsWith("cat_spot") ? Color.YELLOW : id.startsWith("debtor") ? Color.MAGENTA : id.startsWith("neutral") ? Color.CYAN
                    : id.startsWith("poi_") ? Color.ORANGE : id.startsWith("stash") || id.startsWith("shlahbaum") || id.startsWith("kiosk") ? Color.GREEN : null;
            if (c == null) {
                continue;
            }
            int mx = (int) Math.round((m.x() - (cx - span / 2.0)) * scale);
            int my = (int) Math.round((m.z() - (cz - span / 2.0)) * scale);
            g.setColor(c);
            g.fillOval(mx - 3, my - 3, 7, 7);
            g.setColor(Color.BLACK);
            g.drawOval(mx - 3, my - 3, 7, 7);
        }
        g.setColor(new Color(255, 0, 255, 180));
        for (int[] e : DistrictPlan.DEBTOR_EDGES) {
            int[] a = DistrictPlan.DEBTOR_NODES[e[0]];
            int[] bb = DistrictPlan.DEBTOR_NODES[e[1]];
            g.drawLine((int) Math.round((a[0] - (cx - span / 2.0)) * scale), (int) Math.round((a[1] - (cz - span / 2.0)) * scale),
                    (int) Math.round((bb[0] - (cx - span / 2.0)) * scale), (int) Math.round((bb[1] - (cz - span / 2.0)) * scale));
        }
        g.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.printf("citymap %s: span %d around (%d, %d), heights %d..%d%n", out.getName(), span, cx, cz, min, max);
    }
}
