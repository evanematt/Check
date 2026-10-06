package com.lewandivka.core.world.gen;

import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.StructureChecks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of every named blueprint of the game, for tooling (renderer, validators, reports).
 * The generators themselves get their blueprints from the world plans.
 */
public final class Catalog {

    private Catalog() {
    }

    /** Every block id of the mod. Structure builders may only use these (plus vanilla blocks). */
    public static final Set<String> MOD_BLOCKS = Set.of(
            "lewandivka:abandoned_kiosk", "lewandivka:kiosk_foundation", "lewandivka:artifact_pedestal",
            "lewandivka:garage_lift", "lewandivka:garage_power_panel", "lewandivka:ticket_validator",
            "lewandivka:colored_portal", "lewandivka:portal_frame", "lewandivka:spring_pad", "lewandivka:spring_hatch",
            "lewandivka:chromatic_crystal", "lewandivka:crystal_cluster", "lewandivka:glowshroom_cap",
            "lewandivka:glowshroom_stem", "lewandivka:rainbow_leaves", "lewandivka:colored_grate",
            "lewandivka:guard_door", "lewandivka:collector_door", "lewandivka:shelter_door", "lewandivka:dash_door",
            "lewandivka:office_door", "lewandivka:grey_void", "lewandivka:crumbling_platform",
            "lewandivka:chromatic_relay", "lewandivka:water_drain", "lewandivka:pump_control",
            "lewandivka:tram_switch", "lewandivka:garage_plate", "lewandivka:lore_note", "lewandivka:supply_stash",
            "lewandivka:clue_prop", "lewandivka:cardboard_box", "lewandivka:package_block", "lewandivka:kettle_block",
            "lewandivka:old_rug", "lewandivka:waterfall", "lewandivka:waterfall_up", "lewandivka:heavy_lever",
            "lewandivka:battery_socket", "lewandivka:collar_stand", "lewandivka:paint_tap", "lewandivka:press_head",
            "lewandivka:checkpoint_lamp", "lewandivka:seed_bowl", "lewandivka:queue_display", "lewandivka:ticket_machine");

    /** Validation specifications of the quest structures (see {@link StructureChecks}). */
    public static List<StructureChecks.Spec> specs() {
        List<StructureChecks.Spec> list = new ArrayList<>();
        list.add(StructureChecks.Spec.of("tram_stop", "spawn", 0)
                .require("validator_1", "validator_2", "validator_3", "platform_center")
                .reach("validator_1", "validator_2", "validator_3"));
        list.add(StructureChecks.Spec.of("last_tram", null, -1)
                .require("fare_box", "cabin", "door_n1", "door_n2", "door_n3", "door_s1", "door_s2", "door_s3"));
        list.add(garageSpec());
        return list;
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
        return m;
    }

    /** Blueprints carry the id they were built with; tooling wants unique keys, so wrap lazily. */
    private static Blueprint rename(Blueprint bp, String id) {
        return bp.withId(id);
    }
}
