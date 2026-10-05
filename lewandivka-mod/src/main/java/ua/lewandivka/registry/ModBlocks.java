package ua.lewandivka.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.MushroomBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.block.*;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    public static final Block CRYSTAL_ROSE = register("crystal_rose", new Block(crystal(MapColor.PINK)));
    public static final Block CRYSTAL_AQUA = register("crystal_aqua", new Block(crystal(MapColor.CYAN)));
    public static final Block CRYSTAL_CITRINE = register("crystal_citrine", new Block(crystal(MapColor.YELLOW)));
    public static final Block GLOWSHROOM_CAP = register("glowshroom_cap", new MushroomBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.PURPLE).strength(0.2f).sounds(BlockSoundGroup.WOOD).luminance(s -> 11)));
    public static final Block GLOWSHROOM_STEM = register("glowshroom_stem", new MushroomBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE).strength(0.2f).sounds(BlockSoundGroup.WOOD).luminance(s -> 4)));
    public static final Block RAINBOW_LEAVES = register("rainbow_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES)
            .luminance(s -> 7)));
    public static final Block CHROMA_PORTAL = register("chroma_portal", new ChromaPortalBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.MAGENTA).strength(3f, 1200f).luminance(s -> 15).sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    public static final Block LAUNCH_PAD = register("launch_pad", new LaunchPadBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.LIME).strength(1.5f).sounds(BlockSoundGroup.METAL).nonOpaque().luminance(s -> 6)));
    public static final Block TRAM_STOP = register("tram_stop", new TramStopBlock(unbreakable(MapColor.YELLOW).luminance(s -> 12)));
    public static final Block LIFT_SWITCH = register("lift_switch", new ShieldNodeBlock(node()));
    public static final Block TICKET_VALIDATOR = register("ticket_validator", new ShieldNodeBlock(node()));
    public static final Block DOOR_LEVER = register("door_lever", new ShieldNodeBlock(node()));
    public static final Block BOSS_ALTAR = register("boss_altar", new BossAltarBlock(unbreakable(MapColor.BLACK).luminance(s -> 10)));
    public static final Block CARDBOARD_BOX = register("cardboard_box", new CardboardBoxBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).strength(0.3f).sounds(BlockSoundGroup.WOOL)));
    public static final Block TINY_BOX = register("tiny_box", new TinyBoxBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).breakInstantly().sounds(BlockSoundGroup.WOOL).nonOpaque()));
    public static final Block CHROMA_BARS = register("chroma_bars", new Block(unbreakable(MapColor.PURPLE).nonOpaque().luminance(s -> 5)));
    public static final Block BOX_SWITCH = register("box_switch", new Block(unbreakable(MapColor.BROWN).luminance(s -> 7)));
    public static final Block GUARD_DOOR = register("guard_door", new GuardDoorBlock(unbreakable(MapColor.IRON_GRAY).nonOpaque()));
    public static final Block COLLECTOR_DOOR = register("collector_door", new Block(unbreakable(MapColor.PURPLE).luminance(s -> 3)));
    public static final Block LITTER_BOX = register("litter_box", new ShapedBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BLUE).strength(0.8f).sounds(BlockSoundGroup.STONE).nonOpaque(), 1, 0, 1, 15, 5, 15));
    public static final Block SURPRISE = register("surprise", new SurpriseBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).breakInstantly().noCollision().nonOpaque().sounds(BlockSoundGroup.MUD)));
    public static final Block KIOSK = register("kiosk", new KioskBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.RED).strength(2f).sounds(BlockSoundGroup.WOOD)));
    public static final Block DOOR_MAT = register("door_mat", new ShapedBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).strength(0.1f).sounds(BlockSoundGroup.WOOL).nonOpaque(), 0, 0, 0, 16, 1, 16));
    public static final Block SHELTER_DOOR = register("shelter_door", new ShelterDoorBlock(unbreakable(MapColor.PURPLE).luminance(s -> 9)));
    public static final Block GREY_VOID = register("grey_void", new GreyVoidBlock(unbreakable(MapColor.GRAY)));

    private static AbstractBlock.Settings crystal(MapColor color) {
        return AbstractBlock.Settings.create().mapColor(color).strength(1.5f).requiresTool()
                .sounds(BlockSoundGroup.AMETHYST_BLOCK).luminance(s -> 12);
    }

    private static AbstractBlock.Settings unbreakable(MapColor color) {
        return AbstractBlock.Settings.create().mapColor(color).strength(-1.0f, 3600000.0f).dropsNothing();
    }

    private static AbstractBlock.Settings node() {
        return unbreakable(MapColor.YELLOW).luminance(s -> s.get(ShieldNodeBlock.ACTIVE) ? 15 : 4);
    }

    private static <T extends Block> T register(String name, T block) {
        Registry.register(Registries.BLOCK, Lewandivka.id(name), block);
        Registry.register(Registries.ITEM, Lewandivka.id(name), new BlockItem(block, new Item.Settings()));
        ALL.add(block);
        return block;
    }

    public static void init() {
    }

    private ModBlocks() {
    }
}
