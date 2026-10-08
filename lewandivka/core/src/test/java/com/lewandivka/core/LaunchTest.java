package com.lewandivka.core;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.world.FlightSim;
import com.lewandivka.core.world.Launch;
import com.lewandivka.core.world.gen.Catalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The springs are physics: the islands of the sky ascent must be reachable with the launch the game really applies, and
 * the rider must get there without touching anything on the way (the flight is simulated tick by tick through the
 * finished blueprint, see {@link FlightSim}).
 */
class LaunchTest {

    /** Blocks the rider passes through: air, flowers, rails and the pad itself. */
    private static final Set<String> PASSABLE = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:allium",
            "minecraft:pink_tulip", "minecraft:rail", "lewandivka:spring_pad", "lewandivka:checkpoint_lamp");

    private static FlightSim.Solid solid(Blueprint bp) {
        return (x, y, z) -> {
            int raw = bp.rawAt(x, y, z);
            if (raw == Blueprint.UNTOUCHED) {
                return false;
            }
            String key = bp.paletteKey(raw);
            int bracket = key.indexOf('[');
            return !PASSABLE.contains(bracket < 0 ? key : key.substring(0, bracket));
        };
    }

    @Test
    void apexMatchesTheDesignNumbers() {
        assertEquals(27.3, Launch.apex(Launch.PAD_UP), 0.5);
        assertEquals(31.3, Launch.apex(Launch.HATCH_UP), 1.0);
        assertTrue(Launch.apex(Launch.HOP) < 1.5, "without the insoles a pad is only an ordinary jump");
    }

    @Test
    void theFlightSimulationAgreesWithTheRecordedFlightsOfTheGame() {
        // measured in the game (CI client test, survival player on the pads of the first design): the pad at -77.5 with
        // a drift of 1.4 carried the rider to x = -70.3 where he hit the underside of the first island
        Blueprint bp = Catalog.namedBlueprints().get("sky_ascent");
        assertNotNull(bp);
        // free flight: the first tick is slowed by the ground (0.546), the following ones by the air (0.91)
        FlightSim.Flight free = FlightSim.fly((x, y, z) -> false, 0, 0, 0, 1.4, 2.0, 0, 0.6, 1.8, 100);
        double sum = 1.4;
        double v = 1.4 * 0.546;
        for (int t = 2; t <= 70; t++) {
            sum += v;
            v *= 0.91;
        }
        assertEquals(sum, free.path.get(70)[0], 1e-6, "ground friction on the first tick, air friction afterwards");
    }

    @Test
    void everyPadOfTheSkyAscentThrowsTheRiderCleanlyOntoTheNextIsland() {
        Blueprint bp = Catalog.namedBlueprints().get("sky_ascent");
        assertNotNull(bp);
        FlightSim.Solid world = solid(bp);
        // pad -> where the rider is meant to come down, how far from that spot is still fine (the radius of the top of the
        // island, or the width of the grass strip in front of the tube)
        Object[][] jumps = {{"pad_0", "isle_1", 6.0}, {"pad_1", "isle_2", 5.0}, {"pad_2", "isle_3", 5.0}, {"pad_3", "isle_4", 1.5}};
        for (Object[] j : jumps) {
            Blueprint.Marker pad = bp.marker((String) j[0]);
            Blueprint.Marker target = bp.marker((String) j[1]);
            assertNotNull(pad, (String) j[0]);
            assertNotNull(target, (String) j[1]);
            assertEquals(target.z(), pad.z(), 0.01, "the pads and islands share one line");
            double room = (Double) j[2];
            // a rider with the width of a player and one twice as wide (the second shows how much room is left)
            for (double width : new double[] {FlightSim.WIDTH, 2 * FlightSim.WIDTH}) {
                FlightSim.Flight f = FlightSim.fly(world, pad.x() + 0.5, pad.y(), pad.z() + 0.5, Launch.PAD_DRIFT, Launch.PAD_UP, 0, width, FlightSim.HEIGHT, 200);
                String what = j[0] + " -> " + j[1] + " (box " + width + ")";
                assertTrue(f.landed, what + " never comes down");
                assertTrue(f.sideTick < 0, what + " runs into a wall at tick " + f.sideTick + " " + java.util.Arrays.toString(f.sideAt));
                assertTrue(f.ceilingTick < 0, what + " hits a ceiling at tick " + f.ceilingTick + " " + java.util.Arrays.toString(f.ceilingAt));
                assertEquals(target.y(), f.landAt[1], 0.01, what + " lands at the wrong height");
                assertEquals(target.x() + 0.5, f.landAt[0], room - 0.8, what + " lands at x=" + f.landAt[0] + ", the spot is " + (target.x() + 0.5));
            }
        }
    }

    @Test
    void theBounceTunnelIsHighEnoughForTheHatchAndItsExitIsNextToIt() {
        Blueprint bp = Catalog.namedBlueprints().get("sky_ascent");
        Blueprint.Marker bottom = bp.marker("tunnel_bottom");
        Blueprint.Marker stop = bp.marker("tram_stop_lower");
        Blueprint.Marker top = bp.marker("tunnel_top");
        Blueprint.Marker hatch = bp.marker("hatch_up");
        assertNotNull(bottom);
        assertNotNull(stop);
        assertNotNull(top);
        assertNotNull(hatch);
        double needed = stop.y() - bottom.y();
        assertTrue(Launch.apex(Launch.HATCH_UP) > needed + 2.0, "the hatch reaches the platform of the sky tram (" + needed + " blocks up) with room to spare");
        // the exit opening is as tall as the rider at the top of the throw needs: feet anywhere from the platform up to 2.5
        // blocks above it (a player is 1.8 tall), over the whole width of the opening
        FlightSim.Solid world = solid(bp);
        int exitX = top.x() - 1;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 0; dy <= 4; dy++) {
                assertFalse(world.at(exitX, top.y() + dy, top.z() + dz), "the exit of the tube is blocked at height +" + dy + " (z " + dz + ")");
            }
        }
        // the hatch stands at the east wall: the throw only has to carry the rider a few steps (the wind does that)
        double boxEast = hatch.x() + 0.5 + FlightSim.WIDTH / 2;
        double gap = exitX - boxEast;
        assertTrue(gap >= 0 && gap <= 0.6, "the hatch is " + gap + " blocks short of the exit cell");
    }

    @Test
    void theGlideSettlesAtTheDesignedSpeedDespiteTheFrictionOfTheAir() {
        assertEquals(Launch.GLIDE_SPEED, Launch.glideSpeed(0.3, 200), 0.005);
        // from a standing start the speed comes up within about a second
        assertTrue(Launch.glideSpeed(0.0, 20) > 0.75 * Launch.GLIDE_SPEED, "too slow to get going: " + Launch.glideSpeed(0.0, 20));
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
