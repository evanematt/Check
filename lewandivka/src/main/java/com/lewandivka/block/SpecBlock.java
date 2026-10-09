package com.lewandivka.block;

import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.BlockSpec.Behaviour;
import com.lewandivka.core.registry.BlockSpec.Model;
import com.lewandivka.core.registry.PropSpec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one block class of the mod. Its properties, light, sound and shape come from the catalog entry
 * ({@link BlockSpec}); what happens on use or touch is decided by the {@link Behaviour} and executed by
 * {@link BlockActions} on the server, so the block itself stays a thin shell.
 */
public class SpecBlock extends Block {

    /** The spec of the block under construction: appendProperties() runs inside the Block constructor. */
    private static BlockSpec constructing;

    public final BlockSpec spec;
    private final Map<String, Property<?>> props = new LinkedHashMap<>();

    public static SpecBlock create(BlockSpec spec) {
        constructing = spec;
        try {
            return new SpecBlock(spec);
        } finally {
            constructing = null;
        }
    }

    private SpecBlock(BlockSpec spec) {
        super(settings(spec));
        this.spec = spec;
        for (Property<?> p : getStateManager().getProperties()) {
            props.put(p.getName(), p);
        }
        setDefaultState(getStateManager().getDefaultState());
    }

    private static AbstractBlock.Settings settings(BlockSpec spec) {
        AbstractBlock.Settings s = AbstractBlock.Settings.create();
        s.sounds(sound(spec.sound));
        if (spec.unbreakable) {
            s.strength(-1.0f, 3_600_000.0f).dropsNothing().pistonBehavior(PistonBehavior.BLOCK);
        } else {
            s.strength(1.5f).pistonBehavior(PistonBehavior.DESTROY);
        }
        if (spec.passable) {
            s.noCollision();
        }
        if (spec.translucent || spec.cutout || spec.model != Model.CUBE && spec.model != Model.ORIENTABLE && spec.model != Model.COLUMN) {
            s.nonOpaque();
        }
        if (spec.light > 0) {
            final String prop = spec.lightProp;
            final int level = spec.light;
            // evaluated while the states are created, i.e. inside the Block constructor: only the state itself is usable
            s.luminance(state -> prop == null || entryIsTrue(state, prop) ? level : 0);
        }
        return s;
    }

    private static boolean entryIsTrue(BlockState state, String name) {
        for (Map.Entry<Property<?>, Comparable<?>> e : state.getEntries().entrySet()) {
            if (e.getKey().getName().equals(name)) {
                return Boolean.TRUE.equals(e.getValue());
            }
        }
        return false;
    }

    private static BlockSoundGroup sound(String name) {
        return switch (name) {
            case "wood" -> BlockSoundGroup.WOOD;
            case "metal" -> BlockSoundGroup.METAL;
            case "wool" -> BlockSoundGroup.WOOL;
            case "lantern" -> BlockSoundGroup.LANTERN;
            case "glass" -> BlockSoundGroup.GLASS;
            case "grass" -> BlockSoundGroup.GRASS;
            case "amethyst" -> BlockSoundGroup.AMETHYST_BLOCK;
            case "slime" -> BlockSoundGroup.SLIME;
            default -> BlockSoundGroup.STONE;
        };
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        if (constructing != null) {
            for (PropSpec p : constructing.props) {
                builder.add(BlockProps.create(p));
            }
        }
    }

    // ------------------------------------------------------------------ property access by catalog name

    public boolean hasProp(String name) {
        return props.containsKey(name);
    }

    public boolean getBool(BlockState state, String name) {
        return props.get(name) instanceof BooleanProperty p && state.get(p);
    }

    public int getInt(BlockState state, String name) {
        return props.get(name) instanceof IntProperty p ? state.get(p) : 0;
    }

    public Direction getFacing(BlockState state) {
        return props.get("facing") instanceof DirectionProperty p ? state.get(p) : Direction.NORTH;
    }

    /** Sets a property using the value name of the catalog ({@code lit=true}, {@code kind=breaker}, {@code note=3}). */
    public BlockState with(BlockState state, String name, String value) {
        PropSpec ps = spec.prop(name);
        Property<?> p = props.get(name);
        if (ps == null || p == null || !ps.allows(value)) {
            return state;
        }
        if (p instanceof BooleanProperty bp) {
            return state.with(bp, Boolean.parseBoolean(value));
        }
        if (p instanceof IntProperty ip) {
            int v = ps.type() == PropSpec.Type.ENUM ? ps.values().indexOf(value) : Integer.parseInt(value);
            return state.with(ip, v);
        }
        if (p instanceof DirectionProperty dp) {
            Direction d = Direction.byName(value);
            return d == null ? state : state.with(dp, d);
        }
        return state;
    }

    public BlockState withBool(BlockState state, String name, boolean value) {
        return with(state, name, Boolean.toString(value));
    }

    // ------------------------------------------------------------------ placement and shape

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState();
        if (props.get("facing") instanceof DirectionProperty dp) {
            Direction d = spec.model == Model.PAD || spec.model == Model.CLUSTER ? Direction.UP : ctx.getHorizontalPlayerFacing().getOpposite();
            if (dp.getValues().contains(d)) {
                state = state.with(dp, d);
            }
        }
        return state;
    }

    private VoxelShape shapeFor(BlockState state) {
        return switch (spec.model) {
            case PLATE -> plate(getFacing(state));
            case PAD -> Block.createCuboidShape(0, 0, 0, 16, 2, 16);
            case CARPET -> Block.createCuboidShape(0, 0, 0, 16, 1, 16);
            case CLUSTER -> Block.createCuboidShape(3, 0, 3, 13, 12, 13);
            case PORTAL -> getInt(state, "axis") == 0 ? Block.createCuboidShape(0, 0, 6, 16, 16, 10) : Block.createCuboidShape(6, 0, 0, 10, 16, 16);
            case PEDESTAL -> Block.createCuboidShape(2, 0, 2, 14, 13, 14);
            case LAMP -> Block.createCuboidShape(4, 0, 4, 12, 16, 12);
            case SMALL -> Block.createCuboidShape(1, 0, 1, 15, 12, 15);
            default -> VoxelShapes.fullCube();
        };
    }

    /** The plate model hangs on the south side for {@code facing=north} and turns with the facing. */
    private static VoxelShape plate(Direction facing) {
        return switch (facing) {
            case EAST -> Block.createCuboidShape(0, 0, 0, 1, 16, 16);
            case SOUTH -> Block.createCuboidShape(0, 0, 0, 16, 16, 1);
            case WEST -> Block.createCuboidShape(15, 0, 0, 16, 16, 16);
            default -> Block.createCuboidShape(0, 0, 15, 16, 16, 16);
        };
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return spec.passable ? VoxelShapes.empty() : shapeFor(state);
    }

    // ------------------------------------------------------------------ behaviour (server side, via BlockActions)

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!clicks()) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            return BlockActions.use(sw, pos, state, this, sp, hand);
        }
        return ActionResult.PASS;
    }

    private boolean clicks() {
        return switch (spec.behaviour) {
            case STATION, NOTE, STASH, KIOSK, PACKAGE, KETTLE, PEDESTAL, CHECKPOINT -> true;
            default -> false;
        };
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && spec.behaviour == Behaviour.SPRING && world instanceof ServerWorld sw) {
            BlockActions.spring(sw, pos, state, this, entity);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (spec.behaviour == Behaviour.SPRING) {
            // a spring never hurts: the launch happens in onSteppedOn
            entity.handleFallDamage(fallDistance, 0.0f, world.getDamageSources().fall());
            return;
        }
        super.onLandedUpon(world, state, pos, entity, fallDistance);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient || !(world instanceof ServerWorld sw)) {
            return;
        }
        switch (spec.behaviour) {
            case PORTAL -> BlockActions.portal(sw, pos, state, this, entity);
            case VOID -> BlockActions.greyVoid(sw, pos, entity);
            // a spring pad is a thin plate without collision: the rider stands in its cell, not on top of it
            case SPRING -> BlockActions.spring(sw, pos, state, this, entity);
            default -> { }
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && spec.behaviour == Behaviour.KIOSK && world instanceof ServerWorld sw && placer instanceof ServerPlayerEntity sp) {
            BlockActions.kioskPlaced(sw, pos, state, this, sp);
        }
    }
}
