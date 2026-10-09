package com.lewandivka.core;

import com.lewandivka.core.world.FlightSim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The simulation that proves the level geometry must itself behave like the game's collision and drag. */
class FlightSimTest {

    private static final FlightSim.Solid FLOOR = (x, y, z) -> y < 0;

    @Test
    void aRiderThrownUpComesDownOnTheFloorAtTheSameSpot() {
        FlightSim.Flight f = FlightSim.fly(FLOOR, 0.5, 0, 0.5, 0, 2.0, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 200);
        assertTrue(f.landed);
        assertEquals(0.0, f.landAt[1], 1e-9, "he stands on the floor");
        assertEquals(0.5, f.landAt[0], 1e-9);
        assertTrue(f.sideTick < 0 && f.ceilingTick < 0);
        assertEquals(20.0, f.apex, 0.5, "a throw of 2.0 reaches about twenty blocks");
    }

    @Test
    void aWallStopsTheBoxAtItsFace() {
        FlightSim.Solid wall = (x, y, z) -> y < 0 || x >= 5;
        FlightSim.Flight f = FlightSim.fly(wall, 0.5, 0, 0.5, 1.5, 0.4, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 100);
        assertTrue(f.sideTick > 0, "he runs into the wall");
        assertEquals(5.0 - FlightSim.WIDTH / 2, f.sideAt[0], 1e-6, "the east face rests against the wall");
        assertTrue(f.landed);
    }

    @Test
    void aCeilingStopsTheRise() {
        FlightSim.Solid ceiling = (x, y, z) -> y < 0 || y >= 5;
        FlightSim.Flight f = FlightSim.fly(ceiling, 0.5, 0, 0.5, 0, 2.0, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 100);
        assertTrue(f.ceilingTick > 0, "he hits the ceiling");
        assertEquals(5.0 - FlightSim.HEIGHT, f.ceilingAt[1], 1e-6, "the head rests against the ceiling");
        assertTrue(f.landed);
    }

    @Test
    void theFirstTickOfALaunchIsSlowedByTheGroundTheOthersByTheAir() {
        FlightSim.Flight f = FlightSim.fly((x, y, z) -> false, 0, 0, 0, 1.0, 1.0, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 10);
        assertEquals(1.0, f.path.get(1)[0], 1e-9, "the first move uses the full speed");
        assertEquals(1.0 + 0.546, f.path.get(2)[0], 1e-9, "then the ground friction of the first tick");
        assertEquals(1.0 + 0.546 + 0.546 * 0.91, f.path.get(3)[0], 1e-9, "and the air friction afterwards");
    }

    @Test
    void aRiderWhoNeverTouchesAnythingNeverLands() {
        FlightSim.Flight f = FlightSim.fly((x, y, z) -> false, 0, 0, 0, 1.0, 1.0, 0, FlightSim.WIDTH, FlightSim.HEIGHT, 50);
        assertFalse(f.landed);
        assertTrue(f.clean() == false);
    }
}
