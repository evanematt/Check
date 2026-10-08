package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructureChecks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of every named blueprint of the game, for tooling (renderer, validators, reports).
 * The generators themselves get their blueprints from the world plans.
 */
public final class Catalog {

    private Catalog() {
    }

    /** Blueprint used for reachability checks (movable parts drawn at every position). */
    public static Blueprint validationBlueprint(String id) {
        if (id.equals("rainbow_garage")) {
            return RainbowGarage.walkVariant();
        }
        return namedBlueprints().get(id);
    }

    /** Validation specifications of the quest structures (see {@link StructureChecks}). */
    public static List<StructureChecks.Spec> specs() {
        List<StructureChecks.Spec> list = new ArrayList<>();
        list.add(StructureChecks.Spec.of("tram_stop", "spawn", 0)
                .require("validator_1", "validator_2", "validator_3", "platform_center")
                .reach("validator_1", "validator_2", "validator_3"));
        list.add(StructureChecks.Spec.of("last_tram", null, -1)
                .require("fare_box", "cabin", "door_n1", "door_n2", "door_n3", "door_s1", "door_s2", "door_s3"));
        list.add(garageSpec());
        list.add(rainbowSpec());
        list.add(shelterSpec());
        list.add(aquaSpec());
        list.add(skySpec());
        list.add(depotSpec());
        list.add(approachSpec());
        list.add(towerSpec());
        list.add(StructureChecks.Spec.of("base", "spawn", 0)
                .require("portal_plane", "checkpoint", "litter", "cat_spot_chinazik", "cat_spot_metadonna")
                .reach("bed_1", "bed_2", "bed_3", "pedestal_kettle", "pedestal_package", "pedestal_composter",
                        "pedestal_token", "portal", "door"));
        return list;
    }

    private static StructureChecks.Spec approachSpec() {
        return StructureChecks.Spec.of("tower_approach", "approach_start", -1)
                .link("shaft_bottom", "glide_start").link("glide_start", "glide_end")
                .require("dash_door_1", "dash_door_2", "dash_door_3", "hidden_wall", "wind_shaft", "chasm", "fall_zone",
                        "cp_start", "cp_dash", "cp_hidden", "cp_shaft", "cp_deck", "cp_landing", "cp_tower", "cat_spot")
                .reach("dash_start", "dash_end", "hidden_front", "hidden_back", "shaft_bottom", "glide_start", "glide_end",
                        "tower_gate", "tower_approach_end")
                .seal("dash_end", "hidden_back", "shaft_bottom");
    }

    private static StructureChecks.Spec towerSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("tower", "entrance", Tower.S)
                .link("hatch_f4_top", "f4_ledge")
                .link("f5_start", "f5_p1").link("f5_p1", "f5_p2").link("f5_p2", "f5_p3").link("f5_p3", "f5_p4").link("f5_p4", "f5_end")
                .link("arena_entry", "arena_center")
                .require("door_main", "hidden_urgent", "gate_f1", "hidden_404", "gate_f2", "grate_f3_1", "grate_f3_2", "grate_f3_3",
                        "wind_f4", "fall_zone_f5", "boss_door", "arena", "fall_zone_arena", "cp_gate", "cp_lobby", "cp_archive",
                        "cp_office", "cp_dept", "cp_shaft", "cp_collapsed", "cp_arena", "cat_spot_urgent", "cat_spot_404",
                        "cat_entry_a", "cat_entry_b", "queue_1", "queue_2", "queue_3", "boss_spawn")
                .reach("lobby", "ticket_machine", "urgent_front", "f0_stairs", "f1_arrive", "lever_1", "lever_2", "lever_3",
                        "lever_hall_f1", "f1_stairs", "f2_arrive", "office_404", "lever_404", "f2_stairs", "f3_arrive",
                        "tap_f3_red", "tap_f3_yellow", "tap_f3_blue", "tap_f3_release", "f3_stairs", "f4_arrive", "f4_door",
                        "hatch_f4_top", "f4_ledge", "f5_start", "f5_p1", "f5_p2", "f5_p3", "f5_p4", "f5_end", "arena_entry",
                        "arena_center", "relay_a", "relay_b")
                .seal("f1_arrive", "f2_arrive", "f3_arrive", "f4_arrive", "arena_entry");
        for (int i = 1; i <= 13; i++) {
            s.link("arena_center", "plat_pt_" + i);
        }
        return s;
    }

    private static StructureChecks.Spec skySpec() {
        return StructureChecks.Spec.of("sky_ascent", "ascent_start", 0)
                .link("ascent_start", "isle_1").link("isle_1", "isle_2").link("isle_2", "isle_3").link("isle_3", "isle_4")
                .link("tunnel_bottom", "tunnel_top")
                .require("wind_tube", "cp_start", "cp_isle3", "cp_stop")
                .reach("ascent_start", "isle_1", "isle_2", "isle_3", "isle_4", "tunnel_bottom", "tunnel_top", "tram_stop_lower", "stop_platform");
    }

    private static StructureChecks.Spec depotSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("sky_depot", "tram_arrive", SkyDepot.S)
                .require("boss_door", "arena", "cp_arrival", "cp_dispatcher", "cp_yard", "cp_office", "cp_arena",
                        "net_start", "net_s0", "net_s1", "net_s2", "net_s3", "net_d0", "net_d1", "net_d2", "net_d3", "net_goal",
                        "lane_1_a", "lane_1_b", "lane_2_a", "lane_2_b", "lane_3_a", "lane_3_b", "podium")
                .reach("dispatcher", "desk", "switch_1", "switch_2", "switch_3", "switch_4", "dispatch", "yard", "ticket_office",
                        "ticket_window", "arena_entry", "arena_center", "boss_spawn", "composter_1", "composter_2", "composter_3",
                        "validator_a", "validator_b", "validator_c", "validator_d")
                .seal("arena_center", "composter_3");
        return s;
    }

    private static StructureChecks.Spec shelterSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("shelter", "entrance", Shelter.S)
                .link("platform_a", "platform_b")
                .require("gate_levers", "gate_warehouse", "boss_door", "pit", "pit_exit", "boxes", "arena",
                        "cp_entrance", "cp_levers", "cp_names", "cp_warehouse", "cp_arena")
                .reach("hall_entrance", "lever_hall", "lever_1", "lever_2", "lever_3", "lamp_1", "lamp_2", "lamp_3",
                        "cat_hall", "cat_spawn", "cat_start", "cat_food_1", "cat_food_2", "cat_food_3", "cat_food_4", "cat_rug",
                        "box_warehouse", "box_small", "arena_entry", "arena_center", "boss_spawn", "anchor_1", "anchor_2", "anchor_3")
                .seal("cat_hall", "box_warehouse", "arena_center");
        for (int i = 1; i <= 8; i++) {
            s.reach("stand_" + i);
        }
        return s;
    }

    private static StructureChecks.Spec aquaSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("aquapark", "lake_entry", Aquapark.S)
                .require("gate_hidden", "hidden_area", "boss_door", "fill_1", "fill_2", "fill_3", "arena",
                        "zone_red_cross", "zone_blue_circle", "zone_yellow_triangle", "zone_green_square",
                        "cp_lobby", "cp_pumps", "cp_arena")
                .reach("area", "hint_spot", "cat_hint_a", "cat_hint_b", "lobby", "corridor_west", "corridor_east", "pump_room",
                        "pump_1", "pump_2", "pump_3", "pool_1", "pool_2", "pool_3", "arena_entry", "arena_center", "boss_spawn",
                        "drain_1", "drain_2", "drain_3", "drain_4", "core_spawn_1", "core_spawn_2", "core_spawn_3", "core_spawn_4")
                .seal("lobby", "pump_room", "arena_center");
        return s;
    }

    private static StructureChecks.Spec rainbowSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("rainbow_garage", "entrance", RainbowGarage.S)
                .require("boss_door", "boss_door_inner", "press_1", "press_2", "press_3", "press_4", "arena",
                        "cp_hub", "cp_entrance", "cp_paint", "cp_lift", "cp_lift_top", "cp_press", "cp_arena",
                        "lift_1", "lift_2", "lift_3", "lift_a", "lift_b", "lift_c", "car_lane_a", "car_lane_b")
                .reach("hub", "paint_hall", "tap_red", "tap_yellow", "tap_blue", "tap_release", "lift_hall", "lift_pool",
                        "lift_1_low", "lift_1_high", "lift_2_low", "lift_2_high", "lift_3_low", "lift_3_high", "lift_top",
                        "battery_wing_p", "battery_wing_l", "battery_wing_i", "socket_1", "socket_2", "socket_3",
                        "press_start", "press_end", "arena_entry", "arena_center", "boss_spawn", "lever_a", "lever_b", "lever_c",
                        "minion_spawn_1", "minion_spawn_2", "minion_spawn_3", "minion_spawn_4")
                .seal("arena_center", "battery_wing_p");
        return s;
    }

    private static StructureChecks.Spec garageSpec() {
        StructureChecks.Spec s = StructureChecks.Spec.of("garage13", "entry", GarageComplex.S)
                .require("gate_main", "gate_garage13", "plate_13", "package_spawn", "hatch", "tunnel_gate", "yard", "alley", "tunnels")
                .reach("yard_center", "alley_center", "panel_a", "panel_b", "panel_c", "sync_1", "sync_2",
                        "lever_1", "lever_2", "lever_3", "confirm_c", "breaker_1", "breaker_2", "breaker_3", "breaker_4",
                        "control_room", "escape_lever", "shaft_top", "shaft_bottom", "tunnel_start", "garage13_inside",
                        "exit", "clue_roof", "roof_access", "stash_a", "stash_b", "cp_0", "cp_1", "cp_2", "cp_3",
                        "indicator_a", "indicator_b", "indicator_c", "tunnel_guard")
                .seal("garage13_inside", "exit");
        for (int i = 1; i <= 6; i++) {
            s.reach("guard_spawn_" + i);
        }
        return s;
    }

    public static Map<String, Blueprint> namedBlueprints() {
        Map<String, Blueprint> m = new LinkedHashMap<>();
        for (int i = 0; i < Props.TREE_VARIANTS; i++) {
            m.put("tree" + i, rename(Props.tree(i), "tree" + i));
        }
        m.put("playground", Props.playground());
        m.put("dumpster", Props.dumpster("junk"));
        m.put("car_blue", Props.car(Props.CAR_BLUE, 0));
        m.put("pole", Props.utilityPole(true));
        m.put("bench", Props.bench());
        m.put("panel_grey", rename(Buildings.panelBlock(48, 5, Buildings.Theme.PANEL_GREY, 0, 1), "panel_grey"));
        m.put("panel_beige", rename(Buildings.panelBlock(36, 5, Buildings.Theme.PANEL_BEIGE, 0, 2), "panel_beige"));
        m.put("house_ochre", rename(Buildings.plasterHouse(12, 9, Buildings.HouseStyle.OCHRE, 0, 3), "house_ochre"));
        m.put("house_orange", rename(Buildings.plasterHouse(14, 9, Buildings.HouseStyle.ORANGE, 0, 4), "house_orange"));
        m.put("house_lilac", rename(Buildings.plasterHouse(12, 9, Buildings.HouseStyle.LILAC, 0, 5), "house_lilac"));
        m.put("old_shop", rename(Buildings.oldShop(0), "old_shop"));
        m.put("tram_depot", rename(Buildings.tramDepot(0), "tram_depot"));
        m.put("tram_stop", TramBuilders.tramStop());
        m.put("last_tram", TramBuilders.lastTram());
        m.put("garage13", GarageComplex.garage13());
        m.put("base", BaseHouse.base());
        m.put("rainbow_garage", RainbowGarage.blueprint());
        m.put("shelter", Shelter.blueprint());
        m.put("aquapark", Aquapark.blueprint());
        m.put("sky_ascent", SkyAscent.blueprint());
        m.put("sky_depot", SkyDepot.blueprint());
        m.put("tower_approach", TowerApproach.blueprint());
        m.put("tower", Tower.blueprint());
        m.put("garage0", Garage0.blueprint());
        m.put("glowshroom3", Nature.glowshroom(3));
        m.put("glowshroom6", Nature.glowshroom(6));
        m.put("rainbow_tree1", Nature.rainbowTree(1));
        m.put("crystals2", Nature.crystals(2));
        m.put("island12", Nature.island(12, 1, 1, 2));
        return m;
    }

    /** Blueprints carry the id they were built with; tooling wants unique keys, so wrap lazily. */
    private static Blueprint rename(Blueprint bp, String id) {
        return bp.withId(id);
    }
}
