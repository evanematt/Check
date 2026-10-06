package com.lewandivka.core.world;

import com.lewandivka.core.structure.Dir;
import com.lewandivka.core.structure.Keys;

/**
 * Block-key palette of the campaign. Vanilla blocks give the district its everyday look (the same
 * vanilla-only approach the reference builds use); custom blocks are reserved for gameplay objects.
 *
 * <p>All keys here are plain strings so the world plans stay free of Minecraft classes.</p>
 */
public final class Pal {

    private Pal() {
    }

    // ---- air & utility ----
    public static final String AIR = Keys.AIR;
    public static final String WATER = "minecraft:water";
    public static final String BARRIER = "minecraft:barrier";

    public static String light(int level) {
        return Keys.of("minecraft:light", "level", String.valueOf(level));
    }

    // ---- district materials ----
    public static final String CONCRETE_GREY = "minecraft:light_gray_concrete";
    public static final String CONCRETE_DARK = "minecraft:gray_concrete";
    public static final String CONCRETE_WHITE = "minecraft:white_concrete";
    public static final String CONCRETE_BLACK = "minecraft:black_concrete";
    public static final String STONE = "minecraft:stone";
    public static final String ANDESITE = "minecraft:andesite";
    public static final String POL_ANDESITE = "minecraft:polished_andesite";
    public static final String BRICKS = "minecraft:stone_bricks";
    public static final String BRICKS_CRACKED = "minecraft:cracked_stone_bricks";
    public static final String BRICKS_MOSSY = "minecraft:mossy_stone_bricks";
    public static final String RED_BRICKS = "minecraft:bricks";
    public static final String COBBLE = "minecraft:cobblestone";
    public static final String COBBLE_MOSSY = "minecraft:mossy_cobblestone";
    public static final String GRAVEL = "minecraft:gravel";
    public static final String DIRT = "minecraft:dirt";
    public static final String COARSE_DIRT = "minecraft:coarse_dirt";
    public static final String GRASS = "minecraft:grass_block";
    public static final String PATH = "minecraft:dirt_path";
    public static final String PLASTER_OCHRE = "minecraft:smooth_sandstone";
    public static final String PLASTER_YELLOW = "minecraft:yellow_terracotta";
    public static final String PLASTER_PEEL = "minecraft:sandstone";
    public static final String PLASTER_PEACH = "minecraft:orange_terracotta";
    public static final String PLASTER_WHITE = "minecraft:white_terracotta";
    public static final String PLASTER_LILAC = "minecraft:light_blue_terracotta";
    public static final String PLASTER_CLAY = "minecraft:terracotta";
    public static final String WOOD_DARK = "minecraft:dark_oak_planks";
    public static final String WOOD_SPRUCE = "minecraft:spruce_planks";
    public static final String WOOD_OAK = "minecraft:oak_planks";
    public static final String LOG_DARK = "minecraft:dark_oak_log";
    public static final String LOG_SPRUCE = "minecraft:spruce_log";
    public static final String LOG_OAK = "minecraft:oak_log";
    public static final String GLASS = "minecraft:glass";
    public static final String PANE = "minecraft:glass_pane";
    public static final String BARS = "minecraft:iron_bars";
    public static final String LANTERN = "minecraft:lantern";
    public static final String LEAVES_OAK = Keys.of("minecraft:oak_leaves", "persistent", "true");
    public static final String LEAVES_BIRCH = Keys.of("minecraft:birch_leaves", "persistent", "true");
    public static final String LEAVES_DARK = Keys.of("minecraft:dark_oak_leaves", "persistent", "true");
    public static final String LEAVES_FLOWER = Keys.of("minecraft:flowering_azalea_leaves", "persistent", "true");
    public static final String LEAVES_AZALEA = Keys.of("minecraft:azalea_leaves", "persistent", "true");

    // ---- industrial / garage ----
    public static final String RUST = "minecraft:waxed_oxidized_copper";
    public static final String RUST_CUT = "minecraft:waxed_oxidized_cut_copper";
    public static final String IRON = "minecraft:iron_block";
    public static final String METAL_PLATE = "minecraft:smooth_stone";
    public static final String CHAIN = "minecraft:chain";
    public static final String BLACKSTONE = "minecraft:polished_blackstone";
    public static final String DEEPSLATE_TILES = "minecraft:deepslate_tiles";
    public static final String DEEPSLATE_BRICKS = "minecraft:deepslate_bricks";

    // ---- Chromandivka materials ----
    public static final String TURQ_GRASS = "minecraft:grass_block"; // tinted turquoise by the biome
    public static final String CALCITE = "minecraft:calcite";
    public static final String AMETHYST = "minecraft:amethyst_block";
    public static final String TUFF = "minecraft:tuff";
    public static final String TERRACOTTA_ORANGE = "minecraft:orange_terracotta";
    public static final String TERRACOTTA_RED = "minecraft:red_terracotta";
    public static final String SANDSTONE_RED = "minecraft:red_sandstone";
    public static final String PINK_CONCRETE = "minecraft:pink_concrete";
    public static final String MAGENTA_CONCRETE = "minecraft:magenta_concrete";
    public static final String CYAN_CONCRETE = "minecraft:cyan_concrete";
    public static final String LIME_CONCRETE = "minecraft:lime_concrete";
    public static final String YELLOW_CONCRETE = "minecraft:yellow_concrete";
    public static final String ORANGE_CONCRETE = "minecraft:orange_concrete";
    public static final String PURPLE_CONCRETE = "minecraft:purple_concrete";
    public static final String BLUE_CONCRETE = "minecraft:blue_concrete";
    public static final String RED_CONCRETE = "minecraft:red_concrete";
    public static final String GREEN_CONCRETE = "minecraft:green_concrete";

    // ---- mod blocks ----
    public static String mod(String name, String... kv) {
        return Keys.mod(name, kv);
    }

    public static final String GUARD_DOOR = "lewandivka:guard_door";
    public static final String COLLECTOR_DOOR = "lewandivka:collector_door";
    public static final String SHELTER_DOOR = "lewandivka:shelter_door";
    public static final String GREY_VOID = "lewandivka:grey_void";
    public static final String PORTAL_FRAME = "lewandivka:portal_frame";
    public static final String GLOWSHROOM_STEM = "lewandivka:glowshroom_stem";
    public static final String RAINBOW_LEAVES = Keys.of("lewandivka:rainbow_leaves", "persistent", "true", "distance", "1");
    public static final String OLD_RUG = "lewandivka:old_rug";
    public static final String KIOSK_FOUNDATION = "lewandivka:kiosk_foundation";
    public static final String GARAGE_LIFT = "lewandivka:garage_lift";
    public static final String PRESS_HEAD = "lewandivka:press_head";
    public static final String SPRING_PAD = "lewandivka:spring_pad";
    public static final String WATERFALL = "lewandivka:waterfall";
    public static final String WATERFALL_UP = "lewandivka:waterfall_up";

    public static String cap(String color) {
        return Keys.mod("glowshroom_cap", "color", color);
    }

    public static String crystal(String color) {
        return Keys.mod("chromatic_crystal", "color", color);
    }

    public static String cluster(String color, String facing) {
        return Keys.mod("crystal_cluster", "color", color, "facing", facing);
    }

    public static String grate(String color) {
        return Keys.mod("colored_grate", "color", color);
    }

    public static String note(int note, Dir facing) {
        return Keys.mod("lore_note", "note", String.valueOf(note), "facing", facing.key());
    }

    public static String plate(int plate, Dir facing) {
        return Keys.mod("garage_plate", "plate", String.valueOf(plate), "facing", facing.key());
    }

    public static String stash() {
        return Keys.mod("supply_stash", "looted", "false");
    }

    public static String clue(int kind) {
        return Keys.mod("clue_prop", "kind", String.valueOf(kind));
    }

    public static String checkpoint() {
        return Keys.mod("checkpoint_lamp", "lit", "false");
    }

    public static String panel(String kind, Dir facing) {
        return Keys.mod("garage_power_panel", "kind", kind, "facing", facing.key(), "lit", "false");
    }

    public static String lever(Dir facing) {
        return Keys.mod("heavy_lever", "facing", facing.key(), "powered", "false");
    }

    public static String validator(Dir facing, String symbol) {
        return Keys.mod("ticket_validator", "facing", facing.key(), "symbol", symbol, "active", "false");
    }

    public static String relay(Dir facing) {
        return Keys.mod("chromatic_relay", "facing", facing.key(), "active", "false");
    }

    public static String pump(Dir facing) {
        return Keys.mod("pump_control", "facing", facing.key(), "on", "false");
    }

    public static String drain() {
        return Keys.mod("water_drain", "open", "false");
    }

    public static String tramSwitch(Dir facing) {
        return Keys.mod("tram_switch", "facing", facing.key(), "state", "a");
    }

    public static String tap(String color) {
        return Keys.mod("paint_tap", "color", color, "lit", "false");
    }

    public static String socket(Dir facing) {
        return Keys.mod("battery_socket", "facing", facing.key(), "filled", "false");
    }

    public static String collarStand(Dir facing) {
        return Keys.mod("collar_stand", "facing", facing.key(), "filled", "false");
    }

    public static String box(boolean small) {
        return Keys.mod("cardboard_box", "small", String.valueOf(small), "opened", "false");
    }

    public static String crumbling(int stage) {
        return Keys.mod("crumbling_platform", "stage", String.valueOf(stage));
    }

    public static String springHatch(String facing) {
        return Keys.mod("spring_hatch", "facing", facing);
    }

    public static String pedestal(String kind) {
        return Keys.mod("artifact_pedestal", "kind", kind, "filled", "false");
    }

    public static String portal(char axis) {
        return Keys.mod("colored_portal", "axis", String.valueOf(axis));
    }

    public static String queueDigit(int digit) {
        return Keys.mod("queue_display", "digit", String.valueOf(digit));
    }

    public static String dashDoor() {
        return Keys.mod("dash_door", "open", "false");
    }

    public static String officeDoor() {
        return "lewandivka:office_door";
    }

    public static String seedBowl() {
        return Keys.mod("seed_bowl", "stage", "0");
    }

    public static String ticketMachine(Dir facing) {
        return Keys.mod("ticket_machine", "facing", facing.key());
    }

    public static String kioskBlock(Dir facing) {
        return Keys.mod("abandoned_kiosk", "facing", facing.key());
    }
}
