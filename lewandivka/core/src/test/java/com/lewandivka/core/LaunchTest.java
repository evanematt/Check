package com.lewandivka.core;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.Launch;
import com.lewandivka.core.world.gen.Catalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The springs are physics: the islands of the sky ascent must be reachable with the launch the game really applies. */
class LaunchTest {

    @Test
    void apexMatchesTheDesignNumbers() {
        assertEquals(20.0, Launch.apex(Launch.PAD_UP), 0.5);
        assertEquals(31.0, Launch.apex(Launch.HATCH_UP), 1.0);
        assertTrue(Launch.apex(Launch.HOP) < 1.5, "without the insoles a pad is an ordinary jump");
    }

    @Test
    void everyPadOfTheSkyAscentLandsOnTheNextIsland() {
        Blueprint bp = Catalog.namedBlueprints().get("sky_ascent");
        assertNotNull(bp);
        // pad -> next island centre and its radius (the island helper builds r 6, 5, 5 and the bounce island r 6)
        String[][] jumps = {{"pad_0", "isle_1", "5.5"}, {"pad_1", "isle_2", "4.5"}, {"pad_2", "isle_3", "4.5"}, {"pad_3", "tunnel_bottom", "5.5"}};
        for (String[] j : jumps) {
            Blueprint.Marker pad = bp.marker(j[0]);
            Blueprint.Marker target = bp.marker(j[1]);
            assertNotNull(pad, j[0]);
            assertNotNull(target, j[1]);
            // the rider stands on the pad (a low block) and has to come down on the island (marker = standing height)
            double dy = target.y() - (pad.y() + 1.125);
            double along = Launch.reach(Launch.PAD_UP, Launch.PAD_DRIFT, dy);
            assertFalse(Double.isNaN(along), j[0] + " never comes down to the island");
            double landing = pad.x() + along;
            assertEquals(target.x(), landing, Double.parseDouble(j[2]), j[0] + " lands at x=" + landing + ", the island is centred on " + target.x());
            assertEquals(target.z(), pad.z(), 0.01, "the pads and islands share one line");
        }
    }

    @Test
    void theBounceTunnelIsHighEnoughForTheHatch() {
        Blueprint bp = Catalog.namedBlueprints().get("sky_ascent");
        Blueprint.Marker bottom = bp.marker("tunnel_bottom");
        Blueprint.Marker stop = bp.marker("tram_stop_lower");
        assertNotNull(bottom);
        assertNotNull(stop);
        double needed = stop.y() - bottom.y();
        assertTrue(Launch.apex(Launch.HATCH_UP) > needed, "the hatch reaches the platform of the sky tram (" + needed + " blocks up)");
    }

    @Test
    void theGliderCrossesTheChasmOfTheTowerApproachWithEnergyToSpare() {
        Blueprint bp = Catalog.namedBlueprints().get("tower_approach");
        Blueprint.Marker start = bp.marker("glide_start");
        Blueprint.Marker end = bp.marker("glide_end");
        assertNotNull(start);
        assertNotNull(end);
        // the landing platform begins 2 blocks before its marker; the rider runs about 2 blocks to the edge, flies about
        // 3.6 blocks on the jump before the second press starts the glide, and has lost about 0.6 blocks by then
        double distance = Math.abs(start.z() - end.z()) - 2 - 2 - 3.6;
        double drop = start.y() - end.y();
        int ticks = Launch.glideTicks(distance, 0.3, Ability.GLIDER.energyTicks);
        assertTrue(ticks > 0, "the energy of the glider does not last for " + distance + " blocks");
        assertTrue(ticks <= Ability.GLIDER.energyTicks - 15, "less than 0.75 s of energy to spare: " + ticks + " of " + Ability.GLIDER.energyTicks);
        assertTrue(ticks * Launch.GLIDE_SINK + 0.6 <= drop - 1.0, "the glider sinks " + (ticks * Launch.GLIDE_SINK + 0.6) + " blocks, the platform is only " + drop + " lower");
    }
}
