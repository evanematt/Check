package com.lewandivka.core.tools;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.gen.Catalog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code gradle render -Pids=garage13,playground -Pslices=9,10 -Pscale=6} writes PNGs of the named
 * blueprints to {@code build/renders/}. Without ids it renders every structure in the catalog (top
 * and front views only).
 */
public final class RenderMain {

    private RenderMain() {
    }

    public static void main(String[] args) throws Exception {
        String ids = System.getProperty("ids", "");
        String slices = System.getProperty("slices", "");
        int scale = Integer.parseInt(System.getProperty("scale", "4"));
        File out = new File(System.getProperty("out", "build/renders"));
        List<Integer> ys = new ArrayList<>();
        for (String s : slices.split(",")) {
            if (!s.isBlank()) {
                ys.add(Integer.parseInt(s.trim()));
            }
        }
        for (java.util.Map.Entry<String, Blueprint> entry : Catalog.namedBlueprints().entrySet()) {
            Blueprint bp = entry.getValue();
            if (!ids.isBlank() && !List.of(ids.split(",")).contains(entry.getKey())) {
                continue;
            }
            String base = entry.getKey().replace(':', '_');
            BlueprintRenderer.save(BlueprintRenderer.top(bp, scale), new File(out, base + "_top.png"));
            BlueprintRenderer.save(BlueprintRenderer.front(bp, scale), new File(out, base + "_front.png"));
            BlueprintRenderer.save(BlueprintRenderer.side(bp, scale), new File(out, base + "_side.png"));
            for (int view = 0; view < 4; view++) {
                BlueprintRenderer.save(BlueprintRenderer.iso(bp, scale, view, -1), new File(out, base + "_iso" + view + ".png"));
            }
            String cut = System.getProperty("cut", "");
            if (!cut.isBlank()) {
                BlueprintRenderer.save(BlueprintRenderer.iso(bp, scale, Integer.parseInt(System.getProperty("cutview", "0")), Integer.parseInt(cut)),
                        new File(out, base + "_cut" + cut + ".png"));
            }
            for (int y : ys) {
                if (y >= 0 && y < bp.sizeY()) {
                    BlueprintRenderer.save(BlueprintRenderer.slice(bp, y, scale), new File(out, base + "_y" + y + ".png"));
                }
            }
            System.out.println("rendered " + entry.getKey() + " " + bp.sizeX() + "x" + bp.sizeY() + "x" + bp.sizeZ());
        }
    }
}
