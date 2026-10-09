package com.lewandivka.core.world.gen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructurePlacement;
import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.core.trade.Wares;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The citizens and the traders: where they stand, what they say, what they sell. */
class PopulaceTest {

    private static final Map<String, Integer> STACK = Map.ofEntries(
            Map.entry("egg", 16), Map.entry("bucket", 16), Map.entry("white_bed", 1), Map.entry("shears", 1), Map.entry("iron_pickaxe", 1),
            Map.entry("iron_axe", 1), Map.entry("iron_shovel", 1), Map.entry("iron_sword", 1), Map.entry("shield", 1), Map.entry("flint_and_steel", 1),
            Map.entry("cake", 1), Map.entry("milk_bucket", 1), Map.entry("beetroot_soup", 1), Map.entry("rabbit_stew", 1), Map.entry("fishing_rod", 1),
            Map.entry("oak_boat", 1), Map.entry("compass", 1), Map.entry("honey_bottle", 16));

    private static int stack(String item) {
        return STACK.getOrDefault(item.substring(item.indexOf(':') + 1), 64);
    }

    @Test
    void everySpotHasACreatureOfTheCatalogAndEveryKindStands() {
        Map<String, Integer> count = new HashMap<>();
        for (Populace.Spot s : Populace.spots()) {
            EntitySpec e = ModEntities.byId(s.entity());
            assertNotNull(e, s.entity());
            assertTrue(e.role == EntitySpec.Role.CITIZEN || e.role == EntitySpec.Role.VENDOR, s.entity());
            count.merge(s.entity(), 1, Integer::sum);
        }
        for (EntitySpec e : ModEntities.ALL) {
            if (e.role == EntitySpec.Role.CITIZEN) {
                assertTrue(count.getOrDefault(e.id, 0) >= 3, e.id + " stands " + count.getOrDefault(e.id, 0) + " times");
            } else if (e.role == EntitySpec.Role.VENDOR) {
                assertEquals(1, count.getOrDefault(e.id, 0), e.id);
            }
        }
        assertTrue(Populace.citizenCount() >= 30);
        assertTrue(Populace.spots().size() >= Populace.citizenCount() + Populace.VENDORS.length + 10, "people indoors too");
    }

    @Test
    void theMarkersOfTheCitizensAndTheStallsExist() {
        DistrictPlan plan = DistrictPlan.get();
        Set<String> planMarkers = new HashSet<>();
        plan.planMarkers().forEach(m -> planMarkers.add(m.id()));
        for (StructurePlacement p : plan.fixedPlacements()) {
            for (Blueprint.Marker m : p.blueprint().markers()) {
                planMarkers.add(p.id() + ":" + m.name());
            }
        }
        for (Populace.Spot s : Populace.spots()) {
            assertTrue(planMarkers.contains(s.marker()), "no marker " + s.marker());
        }
    }

    /** Nobody stands in a wall, on a fence or under a roof: the two cells of the body are free and the ground is under them. */
    @Test
    void theCitizensStandOutdoors() {
        DistrictPlan plan = DistrictPlan.get();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < Populace.citizenCount(); i++) {
            int[] at = Populace.citizenAt(i);
            int ground = plan.groundAt(at[0], at[1]);
            for (StructurePlacement p : plan.fixedPlacements()) {
                for (int y = ground + 1; y <= ground + 3; y++) {
                    if (!p.contains(at[0], y, at[1])) {
                        continue;
                    }
                    String key = p.blueprint().keyAt(at[0] - p.x(), y - p.y(), at[1] - p.z());
                    if (key != null && !key.contains("air") && !key.contains("grass") && !key.contains("flower")) {
                        problems.add(Populace.citizenMarker(i) + " at " + at[0] + "," + at[1] + " is inside " + p.id() + " (" + key + " at y " + y + ")");
                    }
                }
            }
            for (int j = 0; j < i; j++) {
                int[] other = Populace.citizenAt(j);
                if (Math.abs(other[0] - at[0]) + Math.abs(other[1] - at[1]) < 8) {
                    problems.add(Populace.citizenMarker(i) + " is too near to " + Populace.citizenMarker(j));
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyoneHasSomethingToSay() {
        Set<String> speakers = new HashSet<>();
        for (EntitySpec e : ModEntities.ALL) {
            if (e.role != EntitySpec.Role.CITIZEN && e.role != EntitySpec.Role.VENDOR) {
                continue;
            }
            int n = DialogueBook.talks(e.id);
            assertTrue(n >= (e.role == EntitySpec.Role.CITIZEN ? 5 : 3), e.id + " has " + n + " things to say");
            assertTrue(DialogueBook.text().containsKey("speaker.lewandivka." + e.id), "no speaker name for " + e.id);
            assertEquals(e.nameUk, DialogueBook.text().get("speaker.lewandivka." + e.id).uk(), "the name in the chat is the name of the creature");
            assertEquals(e.nameEn, DialogueBook.text().get("speaker.lewandivka." + e.id).en());
            speakers.add(e.id);
            for (int i = 1; i <= n; i++) {
                var script = DialogueBook.get(DialogueBook.talkId(e.id, i));
                assertNotNull(script, e.id + " " + i);
                assertTrue(script.lines().size() >= 1 && script.lines().size() <= 3, "short: " + e.id + " " + i);
                assertTrue(script.choices().isEmpty());
                for (int k = 1; k <= script.lines().size(); k++) {
                    DialogueBook.Line line = DialogueBook.text().get("dialogue.lewandivka." + script.id() + "." + k);
                    assertNotNull(line, script.id() + "." + k);
                    assertTrue(line.uk().length() <= 60 && line.en().length() <= 70, script.id() + "." + k + " is too long: " + line);
                    assertTrue(!line.uk().isBlank() && !line.en().isBlank());
                }
            }
        }
        assertTrue(speakers.size() >= 15, speakers.toString());
    }

    @Test
    void theTradersOfferGoodsThatExistForPricesThatMakeSense() throws IOException {
        Path p = Path.of("..", "tools", "data", "mc1201-registry.json");
        JsonObject registry = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
        Set<String> items = new HashSet<>();
        registry.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
        for (EntitySpec e : ModEntities.ALL) {
            if (e.role != EntitySpec.Role.VENDOR) {
                continue;
            }
            List<Wares.Offer> offers = Wares.of(e.id);
            assertTrue(offers.size() >= 8 && offers.size() <= 14, e.id + " has " + offers.size() + " offers");
            int sells = 0;
            int buys = 0;
            Set<String> seen = new HashSet<>();
            for (Wares.Offer o : offers) {
                assertTrue(o.sells() ^ o.buys(), e.id + ": an offer is either a sale or a purchase: " + o);
                String good = o.sells() ? o.get() : o.give();
                int goodCount = o.sells() ? o.getCount() : o.giveCount();
                int emeralds = o.sells() ? o.giveCount() : o.getCount();
                assertTrue(items.contains(good.substring("minecraft:".length())), e.id + " trades an item that does not exist: " + good);
                assertTrue(goodCount >= 1 && goodCount <= stack(good), e.id + ": " + goodCount + " x " + good + " does not fit a stack");
                assertTrue(emeralds >= 1 && emeralds <= 5, e.id + ": the price of " + good + " is " + emeralds);
                assertTrue(o.maxUses() >= 3 && o.maxUses() <= 32, e.id + " " + good);
                assertTrue(seen.add((o.sells() ? "sell " : "buy ") + good), e.id + " offers " + good + " twice");
                sells += o.sells() ? 1 : 0;
                buys += o.buys() ? 1 : 0;
            }
            assertTrue(sells >= 3 && buys >= 3, e.id + ": " + sells + " sales, " + buys + " purchases");
        }
        assertEquals(7, Wares.vendors().size());
    }

    /** Whatever a first day of survival yields can be sold somewhere, so that the first emerald is not out of reach. */
    @Test
    void theFirstEmeraldIsWithinReach() {
        Set<String> bought = new HashSet<>();
        for (String v : Wares.vendors()) {
            Wares.of(v).stream().filter(Wares.Offer::buys).forEach(o -> bought.add(o.give()));
        }
        for (String basic : List.of("minecraft:cobblestone", "minecraft:oak_log", "minecraft:wheat", "minecraft:coal", "minecraft:potato",
                "minecraft:carrot", "minecraft:string", "minecraft:bone", "minecraft:chicken", "minecraft:cod")) {
            assertTrue(bought.contains(basic), "nobody buys " + basic);
        }
    }
}
