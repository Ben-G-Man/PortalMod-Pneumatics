package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.integration.VentAntlineConnections;
import io.github.bengman.pneumaticdiversityvents.server.world.VentJunctionManager;
import io.github.bengman.pneumaticdiversityvents.shared.world.RotatedVentShapeProvider;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.items.WrenchItem;
import net.portalmod.common.sorted.antline.AntlineConnector;

/** Three-opening T-piece rendered from canonical long/inner-bend models plus independent conditional gate overlays. */
public final class VentJunctionBlock extends VentBlock implements AntlineConnector {
    public static final EnumProperty<VentJunctionBranch> BRANCH = EnumProperty.create("branch", VentJunctionBranch.class);
    public static final BooleanProperty FIXED_POSITIVE = BooleanProperty.create("fixed_positive");
    public static final EnumProperty<VentJunctionVisualState> VISUAL = EnumProperty.create("visual", VentJunctionVisualState.class);

    public VentJunctionBlock(AbstractBlock.Properties properties) {
        super(properties, RotatedVentShapeProvider.ENCASED, false);
        registerDefaultState(defaultBlockState().setValue(BRANCH, VentJunctionBranch.TOP)
                .setValue(FIXED_POSITIVE, false).setValue(VISUAL, VentJunctionVisualState.STRAIGHT_OFF));
    }

    public BlockState createState(VentAxis axis, VentCorner corner, boolean mirrored, VentJunctionBranch branch,
            boolean fixedPositive, VentJunctionVisualState visual) {
        return super.createState(axis, corner, mirrored).setValue(BRANCH, branch).setValue(FIXED_POSITIVE, fixedPositive).setValue(VISUAL, visual);
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!WrenchItem.usedWrench(player, hand)) return super.use(state, world, pos, player, hand, hit);
        if (world.isClientSide) return ActionResultType.SUCCESS;
        ServerWorld serverWorld = (ServerWorld) world;
        boolean changed = player.isShiftKeyDown()
                ? VentJunctionManager.toggleDefaultRoute(serverWorld, pos)
                : VentJunctionManager.toggleFixedSide(serverWorld, pos);
        if (!changed) {
            WrenchItem.playFailSound(player, world, hit.getLocation());
            return ActionResultType.FAIL;
        }
        WrenchItem.playUseSound(player, world, hit.getLocation());
        BlockState updated = world.getBlockState(pos);
        if (player.isShiftKeyDown()) {
            boolean turnDefault = updated.getValue(VISUAL).isDefaultTurn();
            player.displayClientMessage(new TranslationTextComponent(turnDefault
                    ? "actionbar.pneumaticdiversityvents.junction_default.turn" : "actionbar.pneumaticdiversityvents.junction_default.straight"), true);
        } else {
            player.displayClientMessage(new TranslationTextComponent(updated.getValue(FIXED_POSITIVE)
                    ? "actionbar.pneumaticdiversityvents.junction_fixed.positive" : "actionbar.pneumaticdiversityvents.junction_fixed.negative"), true);
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        VentAxis axis = state.getValue(AXIS);
        VentCorner corner = state.getValue(CORNER);
        Direction branch = state.getValue(BRANCH).resolve(axis);
        Direction[] radial = radialFaces(axis, corner);
        VoxelShape shape = VoxelShapes.empty();
        for (Direction face : radial) if (face != branch) shape = VoxelShapes.or(shape, faceSlab(face, 4.0D));

        VentJunctionVisualState visual = state.getValue(VISUAL);
        if (!visual.isTurnActive()) {
            for (Direction face : radial) if (face == branch) shape = VoxelShapes.or(shape, faceSlab(face, 4.0D));
        } else {
            boolean fixedPositive = state.getValue(FIXED_POSITIVE);
            boolean positiveLayer = state.getValue(MIRRORED);
            boolean closablePositive = !fixedPositive;
            if (positiveLayer == closablePositive) shape = VoxelShapes.or(shape, faceSlab(axisDirection(axis, closablePositive), 4.0D));
        }
        return shape.optimize();
    }

    @Override public VoxelShape getVisualShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return getCollisionShape(state, world, pos, context);
    }

    private static Direction[] radialFaces(VentAxis axis, VentCorner corner) {
        switch (axis) {
            case Z:
                switch (corner) {
                    case BOTTOM_LEFT: return new Direction[] {Direction.DOWN, Direction.WEST};
                    case BOTTOM_RIGHT: return new Direction[] {Direction.DOWN, Direction.EAST};
                    case TOP_LEFT: return new Direction[] {Direction.UP, Direction.WEST};
                    default: return new Direction[] {Direction.UP, Direction.EAST};
                }
            case X:
                switch (corner) {
                    case BOTTOM_LEFT: return new Direction[] {Direction.DOWN, Direction.NORTH};
                    case BOTTOM_RIGHT: return new Direction[] {Direction.DOWN, Direction.SOUTH};
                    case TOP_LEFT: return new Direction[] {Direction.UP, Direction.NORTH};
                    default: return new Direction[] {Direction.UP, Direction.SOUTH};
                }
            case Y:
                switch (corner) {
                    case TOP_LEFT: return new Direction[] {Direction.NORTH, Direction.WEST};
                    case TOP_RIGHT: return new Direction[] {Direction.NORTH, Direction.EAST};
                    case BOTTOM_LEFT: return new Direction[] {Direction.SOUTH, Direction.WEST};
                    default: return new Direction[] {Direction.SOUTH, Direction.EAST};
                }
            default: throw new IllegalArgumentException("Unsupported junction axis: " + axis);
        }
    }

    private static VoxelShape faceSlab(Direction face, double thickness) {
        switch (face) {
            case WEST: return Block.box(0, 0, 0, thickness, 16, 16);
            case EAST: return Block.box(16 - thickness, 0, 0, 16, 16, 16);
            case DOWN: return Block.box(0, 0, 0, 16, thickness, 16);
            case UP: return Block.box(0, 16 - thickness, 0, 16, 16, 16);
            case NORTH: return Block.box(0, 0, 0, 16, 16, thickness);
            case SOUTH: return Block.box(0, 0, 16 - thickness, 16, 16, 16);
            default: throw new IllegalArgumentException("Unsupported face: " + face);
        }
    }

    private static Direction axisDirection(VentAxis axis, boolean positive) {
        switch (axis) {
            case X: return positive ? Direction.EAST : Direction.WEST;
            case Y: return positive ? Direction.UP : Direction.DOWN;
            case Z: return positive ? Direction.SOUTH : Direction.NORTH;
            default: throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }


    @Override
    public Direction getHorsedOn(BlockState state) {
        return VentAntlineConnections.getHorsedOn(state);
    }

    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return VentAntlineConnections.connectsInDirection(direction, state);
    }

    @Override public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_JUNCTION_ITEM.get());
    }

    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BRANCH, FIXED_POSITIVE, VISUAL);
    }
}
