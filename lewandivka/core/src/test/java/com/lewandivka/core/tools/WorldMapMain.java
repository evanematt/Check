package com.lewandivka.core.tools;

import com.lewandivka.core.world.TerrainColumn;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.core.world.gen.WildTerrain;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code gradle worldMap -Pcx=0 -Pcz=0 -Pspan=3000 -Ppx=1000} writes a top-down map of the district dimension (biome colours,
 * shaded relief, water) to {@code build/renders/worldmap.png} and prints a few statistics: how much of the land is water, which
 * biomes occur and how high the land gets. {@code -Pcave=30} draws the caves of that height instead of the surface.
 */
public final class WorldMapMain {

    private WorldMapMain() {
    }

    private static Color biomeColor(String biome) {
        return switch (biome) {
            case "lewandivka:district" -> new Color(150, 150, 150);
            case "minecraft:plains" -> new Color(141, 179, 96);
            case "minecraft:sunflower_plains" -> new Color(180, 190, 70);
            case "minecraft:meadow" -> new Color(120, 190, 120);
            case "minecraft:forest" -> new Color(5, 102, 33);
            case "minecraft:flower_forest" -> new Color(60, 140, 90);
            case "minecraft:birch_forest" -> new Color(48, 116, 68);
            case "minecraft:dark_forest" -> new Color(64, 81, 26);
            case "minecraft:taiga" -> new Color(11, 102, 89);
            case "minecraft:snowy_plains" -> new Color(240, 245, 250);
            case "minecraft:snowy_taiga" -> new Color(196, 220, 230);
            case "minecraft:savanna" -> new Color(189, 178, 95);
            case "minecraft:desert" -> new Color(250, 148, 24);
            case "minecraft:jungle" -> new Color(83, 123, 9);
            case "minecraft:swamp" -> new Color(7, 249, 178);
            case "minecraft:windswept_hills" -> new Color(96, 96, 96);
            case "minecraft:windswept_forest" -> new Color(70, 100, 80);
            case "minecraft:snowy_slopes" -> new Color(210, 220, 235);
            case "minecraft:grove" -> new Color(180, 200, 190);
            case "minecraft:jagged_peaks", "minecraft:frozen_peaks" -> new Color(250, 250, 255);
            case "minecraft:stony_peaks" -> new Color(160, 150, 140);
            case "minecraft:river" -> new Color(0, 0, 255);
            case "minecraft:frozen_river" -> new Color(160, 160, 255);
            case "minecraft:beach" -> new Color(250, 222, 85);
            case "minecraft:snowy_beach" -> new Color(250, 240, 192);
            case "minecraft:stony_shore" -> new Color(162, 162, 132);
            case "minecraft:ocean" -> new Color(0, 0, 112);
            case "minecraft:deep_ocean" -> new Color(0, 0, 48);
            case "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean" -> new Color(112, 112, 214);
            case "minecraft:warm_ocean", "minecraft:lukewarm_ocean" -> new Color(0, 100, 200);
            default -> Color.MAGENTA;
        };
    }

    public static void main(String[] args) throws Exception {
        int cx = Integer.parseInt(System.getProperty("cx", "0"));
        int cz = Integer.parseInt(System.getProperty("cz", "0"));
        int span = Integer.parseInt(System.getProperty("span", "3000"));
        int px = Integer.parseInt(System.getProperty("px", "1000"));
        int caveY = Integer.parseInt(System.getProperty("cave", String.valueOf(Integer.MIN_VALUE)));
        File out = new File(System.getProperty("out", "build/renders/worldmap.png"));
        DistrictPlan plan = DistrictPlan.get();
        double step = (double) span / px;
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        int[][] heights = new int[px + 1][px + 1];
        TerrainColumn col = new TerrainColumn();
        Map<String, Integer> biomes = new TreeMap<>();
        int water = 0;
        int total = 0;
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        for (int j = 0; j <= px; j++) {
            for (int i = 0; i <= px; i++) {
                int x = (int) Math.round(cx - span / 2.0 + i * step);
                int z = (int) Math.round(cz - span / 2.0 + j * step);
                plan.column(x, z, col);
                heights[i][j] = col.height;
            }
        }
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                int x = (int) Math.round(cx - span / 2.0 + i * step);
                int z = (int) Math.round(cz - span / 2.0 + j * step);
                plan.column(x, z, col);
                String biome = plan.biomeAt(x, 64, z);
                biomes.merge(biome, 1, Integer::sum);
                total++;
                minH = Math.min(minH, col.height);
                maxH = Math.max(maxH, col.height);
                boolean wet = col.fluidY > col.height;
                if (wet) {
                    water++;
                }
                Color c = biomeColor(biome);
                if (wet) {
                    int depth = col.fluidY - col.height;
                    double k = Math.max(0.35, 1.0 - depth / 40.0);
                    if (biome.contains("river") || biome.contains("ocean")) {
                        c = new Color((int) (c.getRed() * 0.5 + 20), (int) (c.getGreen() * 0.5 + 40), (int) Math.min(255, 90 + 150 * k));
                    } else {
                        c = new Color(40, 90, (int) Math.min(255, 90 + 150 * k));
                    }
                } else {
                    // shaded relief from the slope to the north-west
                    double dh = (heights[Math.min(i + 1, px)][j] - heights[i][j]) * 0.6 + (heights[i][Math.min(j + 1, px)] - heights[i][j]) * 0.4;
                    double shade = Math.max(0.55, Math.min(1.35, 1.0 - dh * 0.035 + (col.height - 64) * 0.002));
                    c = new Color(clamp(c.getRed() * shade), clamp(c.getGreen() * shade), clamp(c.getBlue() * shade));
                }
                if (caveY != Integer.MIN_VALUE) {
                    boolean cave = plan.carved(x, caveY, z, col.height) && caveY <= col.height;
                    c = cave ? new Color(255, 200, 40) : new Color(40, 40, 50);
                }
                img.setRGB(i, j, c.getRGB());
            }
        }
        // the city square and the end of the blending strip
        outline(img, cx, cz, span, px, DistrictPlan.CITY_EDGE, Color.RED);
        outline(img, cx, cz, span, px, DistrictPlan.CITY_EDGE + DistrictPlan.BLEND, new Color(255, 120, 0));
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.printf("map %s: span %d around (%d, %d), %d px; heights %d..%d, water %.1f%%%n", out.getName(), span, cx, cz, px, minH, maxH, 100.0 * water / total);
        final int all = total;
        biomes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-28s %5.1f%%%n", e.getKey(), 100.0 * e.getValue() / all));
        System.out.println("sea level " + WildTerrain.SEA);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static void outline(BufferedImage img, int cx, int cz, int span, int px, int half, Color c) {
        double scale = (double) px / span;
        int x1 = (int) Math.round((-half - (cx - span / 2.0)) * scale);
        int x2 = (int) Math.round((half - (cx - span / 2.0)) * scale);
        int z1 = (int) Math.round((-half - (cz - span / 2.0)) * scale);
        int z2 = (int) Math.round((half - (cz - span / 2.0)) * scale);
        for (int i = x1; i <= x2; i++) {
            set(img, i, z1, c);
            set(img, i, z2, c);
        }
        for (int j = z1; j <= z2; j++) {
            set(img, x1, j, c);
            set(img, x2, j, c);
        }
    }

    private static void set(BufferedImage img, int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
            img.setRGB(x, y, c.getRGB());
        }
    }
}
