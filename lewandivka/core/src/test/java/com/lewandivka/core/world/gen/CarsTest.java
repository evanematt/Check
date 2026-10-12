package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.structure.Materials;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cars: blocks that give way to a car of the mod, and a marker where the car of the mod is made. */
class CarsTest {

    @Test
    void aCarIsMadeOfBlocksThatGiveWayToTheCarsOfTheMod() {
        for (int turns = 0; turns < 4; turns++) {
            Blueprint car = Props.car(Props.CAR_BLUE, turns);
            Set<String> mats = new HashSet<>();
            for (int y = 0; y < car.sizeY(); y++) {
                for (int z = 0; z < car.sizeZ(); z++) {
                    for (int x = 0; x < car.sizeX(); x++) {
                        String key = car.keyAt(x, y, z);
                        if (key == null || key.startsWith("minecraft:air")) {
                            continue;
                        }
                        assertTrue(Keys.isUnlessMod(key), "a block of a car that does not give way: " + key);
                        assertEquals(Cars.MOD, Keys.modOf(key));
                        // the analyses of the core see the car as it stands without the mod
                        assertEquals(Materials.Kind.SOLID, Materials.classify(key), key);
                        mats.add(Keys.blockId(key));
                    }
                }
            }
            assertTrue(mats.contains("minecraft:blue_concrete") && mats.contains("minecraft:black_concrete"), mats.toString());
        }
    }

    @Test
    void theMarkerIsInTheMiddleOfTheCarAndLooksTheWayTheLightsDo() {
        for (int turns = 0; turns < 4; turns++) {
            Blueprint car = Props.car(Props.CAR_BLUE, turns);
            Blueprint.Marker m = car.marker(Cars.MARKER);
            assertEquals(car.sizeX() / 2, m.x(), "turn " + turns);
            assertEquals(car.sizeZ() / 2, m.z(), "turn " + turns);
            assertEquals(0, m.y());
            com.lewandivka.core.structure.Dir front = com.lewandivka.core.structure.Dir.byKey(Cars.data(m.data(), "facing", "?"));
            assertTrue(front != null, m.data());
            // the white lights are the blocks at the corners in front, the red ones at the corners behind
            for (com.lewandivka.core.structure.Dir side : new com.lewandivka.core.structure.Dir[] {front.left(), front.right()}) {
                String ahead = car.keyAt(m.x() + 2 * front.dx + side.dx, 1, m.z() + 2 * front.dz + side.dz);
                String behind = car.keyAt(m.x() - 2 * front.dx + side.dx, 1, m.z() - 2 * front.dz + side.dz);
                assertEquals("minecraft:white_concrete", Keys.blockId(ahead), "turn " + turns + " facing " + front.key());
                assertEquals("minecraft:red_concrete", Keys.blockId(behind), "turn " + turns + " facing " + front.key());
            }
        }
    }

    @Test
    void theFootprintCoversEveryBlockOfTheCar() {
        for (int turns = 0; turns < 4; turns++) {
            Blueprint car = Props.car(Props.CAR_RUST, turns);
            Blueprint.Marker m = car.marker(Cars.MARKER);
            Cars.Spot spot = new Cars.Spot("car", m.x(), m.y(), m.z(), Cars.data(m.data(), "facing", "east"), Cars.colorOf(Props.CAR_RUST));
            int[] box = spot.footprint();
            for (int y = 0; y < car.sizeY(); y++) {
                for (int z = 0; z < car.sizeZ(); z++) {
                    for (int x = 0; x < car.sizeX(); x++) {
                        String key = car.keyAt(x, y, z);
                        if (key != null && !key.startsWith("minecraft:air")) {
                            assertTrue(x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3] && y < Cars.HEIGHT, "turn " + turns + ": a block outside the footprint at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theDistrictHasItsCarsAtMarkersWithKnownColours() {
        List<Cars.Spot> spots = Cars.spots(DistrictPlan.get());
        assertTrue(spots.size() >= 25, "cars in the plan: " + spots.size());
        Set<String> places = new HashSet<>();
        for (Cars.Spot s : spots) {
            assertTrue(Cars.ALL_COLORS.contains(s.color()), s.toString());
            assertTrue(List.of("north", "south", "east", "west").contains(s.facing()), s.toString());
            assertTrue(places.add(s.x() + "," + s.z()), "two cars at one place: " + s);
            int body = Cars.bodyAt(s.x(), s.z());
            assertTrue(body >= 1 && body <= 3);
            assertTrue(Cars.entityId(body, s.color()).matches("trepscars:car_v[123]_w[a-z]+"));
        }
        assertFalse(spots.isEmpty());
    }

    @Test
    void theThreeBodiesAreAllUsed() {
        Set<Integer> bodies = new HashSet<>();
        for (Cars.Spot s : Cars.spots(DistrictPlan.get())) {
            bodies.add(Cars.bodyAt(s.x(), s.z()));
        }
        assertEquals(Set.of(1, 2, 3), bodies);
    }
}
