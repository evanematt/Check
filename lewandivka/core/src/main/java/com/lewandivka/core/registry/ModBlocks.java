package com.lewandivka.core.registry;

import com.lewandivka.core.registry.BlockSpec.Behaviour;
import com.lewandivka.core.registry.BlockSpec.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog of every block of the mod. Registry ids are stable (see docs/ARCHITECTURE.md, "save compatibility"):
 * never rename a released id, only add new ones.
 */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final List<String> GATE_COLORS = List.of("orange", "green", "purple");
    public static final List<String> CRYSTAL_COLORS = List.of("cyan", "green", "orange", "pink", "violet", "yellow");
    public static final List<String> CAP_COLORS = List.of("cyan", "orange", "pink", "violet");
    public static final List<String> TAP_COLORS = List.of("red", "yellow", "blue", "release");
    public static final List<String> SYMBOLS = List.of("none", "cross", "circle", "triangle", "square");

    public static final List<BlockSpec> ALL;
    private static final Map<String, BlockSpec> BY_ID = new LinkedHashMap<>();

    static {
        List<BlockSpec> l = new ArrayList<>();

        // ---- Act 1 : the district ----
        l.add(BlockSpec.of("abandoned_kiosk", Behaviour.KIOSK, Model.ORIENTABLE)
                .name("Закинутий кіоск", "Abandoned Kiosk")
                .prop(PropSpec.horizontalFacing()).sound("wood"));
        l.add(BlockSpec.of("kiosk_foundation", Behaviour.DECOR, Model.CARPET)
                .name("Фундамент кіоска", "Kiosk Foundation").passable().sound("stone"));
        l.add(BlockSpec.of("supply_stash", Behaviour.STASH, Model.SMALL)
                .name("Схованка", "Supply Stash")
                .prop(PropSpec.bool("looted")).visual("looted").sound("wood"));
        l.add(BlockSpec.of("clue_prop", Behaviour.STATION, Model.SMALL)
                .name("Підказка", "Clue")
                .prop(PropSpec.integer("kind", 0, 4)).visual("kind").passable().sound("wool"));
        l.add(BlockSpec.of("lore_note", Behaviour.NOTE, Model.PLATE)
                .name("Записка", "Note")
                .prop(PropSpec.integer("note", 0, 90)).prop(PropSpec.horizontalFacing()).passable().sound("wool"));
        l.add(BlockSpec.of("garage_plate", Behaviour.DECOR, Model.PLATE)
                .name("Номер гаража", "Garage Number Plate")
                .prop(PropSpec.integer("plate", 0, 40)).prop(PropSpec.horizontalFacing()).visual("plate").passable().sound("metal"));
        l.add(BlockSpec.of("garage_power_panel", Behaviour.STATION, Model.ORIENTABLE)
                .name("Електрощиток", "Power Panel")
                .prop(PropSpec.named("kind", "breaker", "console", "switchbox")).prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("lit"))
                .visual("kind", "lit").light(7, "lit").sound("metal"));
        l.add(BlockSpec.of("heavy_lever", Behaviour.STATION, Model.ORIENTABLE)
                .name("Важіль", "Heavy Lever")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("powered")).visual("powered").light(4, "powered").sound("metal"));
        l.add(BlockSpec.of("ticket_validator", Behaviour.STATION, Model.ORIENTABLE)
                .name("Валідатор квитків", "Ticket Validator")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.named("symbol", "none", "cross", "circle", "triangle", "square")).prop(PropSpec.bool("active"))
                .visual("symbol", "active").light(8, "active").sound("metal"));
        l.add(BlockSpec.of("artifact_pedestal", Behaviour.PEDESTAL, Model.PEDESTAL)
                .name("Постамент артефакта", "Artifact Pedestal")
                .prop(PropSpec.named("kind", "kettle", "package", "composter", "token")).prop(PropSpec.bool("filled"))
                .visual("kind", "filled").light(9, "filled").sound("stone"));
        l.add(BlockSpec.of("kettle_block", Behaviour.KETTLE, Model.SMALL)
                .name("Магічний чайник (на землі)", "Magic Kettle (placed)").sound("metal").hiddenInTab());
        l.add(BlockSpec.of("package_block", Behaviour.PACKAGE, Model.SMALL)
                .name("Посилка (на землі)", "Package (placed)").sound("wool").hiddenInTab());
        l.add(BlockSpec.of("checkpoint_lamp", Behaviour.CHECKPOINT, Model.LAMP)
                .name("Лампа-чекпоінт", "Checkpoint Lamp")
                .prop(PropSpec.bool("lit")).visual("lit").light(13, "lit").passable().sound("lantern"));
        l.add(BlockSpec.of("guard_door", Behaviour.GATE, Model.CUBE)
                .name("Двері охорони", "Guard Door").sound("metal"));
        l.add(BlockSpec.of("office_door", Behaviour.DECOR, Model.CUBE)
                .name("Двері кабінету", "Office Door").sound("wood"));
        l.add(BlockSpec.of("ticket_machine", Behaviour.STATION, Model.ORIENTABLE)
                .name("Автомат талонів", "Ticket Machine")
                .prop(PropSpec.horizontalFacing()).light(5).sound("metal"));
        l.add(BlockSpec.of("queue_display", Behaviour.DECOR, Model.PLATE)
                .name("Табло черги", "Queue Display")
                .prop(PropSpec.integer("digit", 0, 9)).prop(PropSpec.horizontalFacing()).visual("digit").light(8).passable().sound("metal"));
        l.add(BlockSpec.of("cardboard_box", Behaviour.STATION, Model.SMALL)
                .name("Картонна коробка", "Cardboard Box")
                .prop(PropSpec.bool("small")).prop(PropSpec.bool("opened")).visual("small", "opened").sound("wool"));
        l.add(BlockSpec.of("old_rug", Behaviour.DECOR, Model.CARPET)
                .name("Старий килимок", "Old Rug").passable().sound("wool"));
        l.add(BlockSpec.of("seed_bowl", Behaviour.STATION, Model.SMALL)
                .name("Миска для сємок", "Seed Bowl")
                .prop(PropSpec.integer("stage", 0, 2)).visual("stage").sound("stone"));

        // ---- portal and base ----
        l.add(BlockSpec.of("colored_portal", Behaviour.PORTAL, Model.PORTAL)
                .name("Кольоровий портал", "Colored Portal")
                .prop(PropSpec.named("axis", "x", "z")).light(11).passable().translucent().sound("glass"));
        l.add(BlockSpec.of("portal_frame", Behaviour.DECOR, Model.CUBE)
                .name("Рама порталу", "Portal Frame").light(5).sound("stone"));

        // ---- Chromandivka : nature ----
        l.add(BlockSpec.of("chromatic_crystal", Behaviour.DECOR, Model.CUBE)
                .name("Хроматичний кристал", "Chromatic Crystal")
                .prop(PropSpec.named("color", CRYSTAL_COLORS.toArray(new String[0]))).visual("color").light(10).translucent().sound("amethyst"));
        l.add(BlockSpec.of("crystal_cluster", Behaviour.DECOR, Model.CLUSTER)
                .name("Друза кристалів", "Crystal Cluster")
                .prop(PropSpec.named("color", CRYSTAL_COLORS.toArray(new String[0]))).prop(PropSpec.facing("up", "down", "north", "south", "west", "east"))
                .visual("color").light(6).passable().cutout().sound("amethyst"));
        l.add(BlockSpec.of("glowshroom_cap", Behaviour.DECOR, Model.CUBE)
                .name("Капелюх світлогриба", "Glowshroom Cap")
                .prop(PropSpec.named("color", CAP_COLORS.toArray(new String[0]))).visual("color").light(12).sound("wood"));
        l.add(BlockSpec.of("glowshroom_stem", Behaviour.DECOR, Model.COLUMN)
                .name("Ніжка світлогриба", "Glowshroom Stem").sound("wood"));
        l.add(BlockSpec.of("rainbow_leaves", Behaviour.DECOR, Model.CUBE)
                .name("Веселкове листя", "Rainbow Leaves").cutout().sound("grass"));
        l.add(BlockSpec.of("waterfall", Behaviour.FLOW, Model.ANIMATED)
                .name("Водоспад", "Waterfall").translucent().passable().light(3).sound("glass").hiddenInTab());
        l.add(BlockSpec.of("waterfall_up", Behaviour.FLOW, Model.ANIMATED)
                .name("Водоспад угору", "Rising Waterfall").translucent().passable().light(5).sound("glass").hiddenInTab());

        // ---- Rainbow Garage ----
        l.add(BlockSpec.of("garage_lift", Behaviour.DECOR, Model.CUBE)
                .name("Підйомник гаража", "Garage Lift").sound("metal"));
        l.add(BlockSpec.of("paint_tap", Behaviour.STATION, Model.ORIENTABLE)
                .name("Кран із фарбою", "Paint Tap")
                .prop(PropSpec.named("color", TAP_COLORS.toArray(new String[0]))).prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("lit")).visual("color", "lit").light(8, "lit").sound("metal"));
        l.add(BlockSpec.of("battery_socket", Behaviour.STATION, Model.ORIENTABLE)
                .name("Гніздо батареї", "Battery Socket")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("filled")).visual("filled").light(7, "filled").sound("metal"));
        l.add(BlockSpec.of("press_head", Behaviour.DECOR, Model.CUBE)
                .name("Голова преса", "Press Head").sound("metal"));
        l.add(BlockSpec.of("colored_grate", Behaviour.GATE, Model.CUBE)
                .name("Кольорова решітка", "Colored Grate")
                .prop(PropSpec.named("color", GATE_COLORS.toArray(new String[0]))).visual("color").cutout().sound("metal"));
        l.add(BlockSpec.of("spring_pad", Behaviour.SPRING, Model.PAD)
                .name("Пружинна плита", "Spring Pad")
                .prop(PropSpec.facing("up", "north", "south", "west", "east")).passable().sound("slime"));
        l.add(BlockSpec.of("spring_hatch", Behaviour.SPRING, Model.CUBE)
                .name("Пружинний люк", "Spring Hatch").sound("metal"));

        // ---- Shelter ----
        l.add(BlockSpec.of("shelter_door", Behaviour.GATE, Model.CUBE)
                .name("Двері укриття", "Shelter Door").sound("metal"));
        l.add(BlockSpec.of("collector_door", Behaviour.GATE, Model.CUBE)
                .name("Двері Колекціонера", "Collector Door").sound("metal"));
        l.add(BlockSpec.of("collar_stand", Behaviour.STATION, Model.ORIENTABLE)
                .name("Підставка для нашийника", "Collar Stand")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("filled")).visual("filled").sound("wood"));
        l.add(BlockSpec.of("dash_door", Behaviour.DASH_GATE, Model.CUBE)
                .name("Двері ривка", "Dash Door").sound("metal"));

        // ---- Aquapark ----
        l.add(BlockSpec.of("pump_control", Behaviour.STATION, Model.ORIENTABLE)
                .name("Пульт насоса", "Pump Control")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("on")).visual("on").light(7, "on").sound("metal"));
        l.add(BlockSpec.of("water_drain", Behaviour.STATION, Model.CUBE)
                .name("Зливний люк", "Water Drain")
                .prop(PropSpec.bool("open")).visual("open").sound("metal"));

        // ---- Sky depot ----
        l.add(BlockSpec.of("tram_switch", Behaviour.STATION, Model.ORIENTABLE)
                .name("Трамвайна стрілка", "Tram Switch")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.named("state", "a", "b")).visual("state").light(4).sound("metal"));

        // ---- Tower ----
        l.add(BlockSpec.of("chromatic_relay", Behaviour.STATION, Model.ORIENTABLE)
                .name("Хроматичне реле", "Chromatic Relay")
                .prop(PropSpec.horizontalFacing()).prop(PropSpec.bool("active")).visual("active").light(10, "active").sound("metal"));
        l.add(BlockSpec.of("crumbling_platform", Behaviour.CRUMBLING, Model.CUBE)
                .name("Платформа, що кришиться", "Crumbling Platform")
                .prop(PropSpec.integer("stage", 0, 3)).visual("stage").sound("stone"));
        l.add(BlockSpec.of("grey_void", Behaviour.VOID, Model.CUBE)
                .name("Сіра порожнеча", "Grey Void").sound("stone"));

        ALL = List.copyOf(l);
        for (BlockSpec b : ALL) {
            if (BY_ID.put(b.id, b) != null) {
                throw new IllegalStateException("duplicate block id " + b.id);
            }
        }
    }

    public static BlockSpec byId(String id) {
        return BY_ID.get(id);
    }

    /** Spec for a full block id ({@code lewandivka:garage_plate}) or null for foreign blocks. */
    public static BlockSpec forKey(String blockId) {
        if (!blockId.startsWith("lewandivka:")) {
            return null;
        }
        return BY_ID.get(blockId.substring("lewandivka:".length()));
    }
}
