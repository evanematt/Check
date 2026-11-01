package com.lewandivka.core.tools;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.gen.Catalog;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** {@code gradle dumpMarkers} writes every marker of the quest structures to {@code build/markers.txt}. */
public final class MarkerDump {

    private MarkerDump() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(System.getProperty("out", "build/markers.txt"));
        out.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            for (var entry : Catalog.namedBlueprints().entrySet()) {
                Blueprint bp = entry.getValue();
                if (bp.markers().isEmpty()) {
                    continue;
                }
                w.println("## " + entry.getKey() + " " + bp.sizeX() + "x" + bp.sizeY() + "x" + bp.sizeZ());
                for (Blueprint.Marker m : bp.markers()) {
                    String size = m.isRegion() ? " [" + m.sx() + "x" + m.sy() + "x" + m.sz() + "]" : "";
                    w.println(String.format("%-28s @%d,%d,%d%s %s", m.name(), m.x(), m.y(), m.z(), size, m.data()));
                }
            }
        }
        System.out.println("wrote " + out);
    }
}
