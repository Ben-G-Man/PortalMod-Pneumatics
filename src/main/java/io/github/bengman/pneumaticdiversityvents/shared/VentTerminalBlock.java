package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.VentTerminalStateManager;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfile;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfiles;
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
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.portalmod.common.items.WrenchItem;

import java.util.UUID;

public final class VentTerminalBlock extends VentBlock {
    public static final BooleanProperty OUTWARD_POSITIVE = BooleanProperty.create("outward_positive");
    public static final EnumProperty<VentTerminalPart> PART = EnumProperty.create("part", VentTerminalPart.class);
    public static final EnumProperty<VentTerminalVisualState> VISUAL = EnumProperty.create("visual", VentTerminalVisualState.class);

    public VentTerminalBlock(AbstractBlock.Properties properties) {
        super(properties, RotatedVentShapeProvider.ENCASED, false);
        registerDefaultState(defaultBlockState().setValue(MIRRORED, false).setValue(OUTWARD_POSITIVE, true).setValue(PART, VentTerminalPart.BODY).setValue(VISUAL, VentTerminalVisualState.NORMAL));
    }

    public BlockState createState(VentAxis axis, VentCorner corner, VentTerminalPart part, boolean outwardPositive) {
        // Terminal axial layers are body/tip parts, not mirrored copies of one another.
        return super.createState(axis, corner, false).setValue(OUTWARD_POSITIVE, outwardPositive).setValue(PART, part)
                .setValue(VISUAL, VentTerminalVisualState.NORMAL);
    }

    @Override public VentExternalFieldProfile getExternalFieldProfile(boolean intake) {
        return intake ? VentExternalFieldProfiles.TERMINAL_INTAKE : VentExternalFieldProfiles.TERMINAL_EXHAUST;
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!WrenchItem.usedWrench(player, hand)) return super.use(state, world, pos, player, hand, hit);
        if (world.isClientSide) return ActionResultType.SUCCESS;

        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        UUID edgeId = registry.getEdgeId(pos);
        if (edgeId == null) return ActionResultType.FAIL;
        VentTerminalVisualState current = state.getValue(VISUAL);
        VentEdge edge = registry.getEdge(edgeId);
        VentTerminalVisualState next = VentTerminalStateManager.resolve(current.nextMode(), edge == null ? null : edge.getNetwork());
        for (BlockPos block : registry.getBlocks(edgeId)) {
            BlockState blockState = world.getBlockState(block);
            if (blockState.getBlock() == this && blockState.hasProperty(VISUAL)) world.setBlock(block, blockState.setValue(VISUAL, next), 3);
        }
        VentTerminalStateManager.playTransitionSound((net.minecraft.world.server.ServerWorld) world, pos, current, next);
        String key;
        switch (next.getMode()) {
            case FORCE: key = "actionbar.pneumaticdiversityvents.terminal_mode.force"; break;
            case PLAYER: key = "actionbar.pneumaticdiversityvents.terminal_mode.player"; break;
            default: key = "actionbar.pneumaticdiversityvents.terminal_mode.normal"; break;
        }
        player.displayClientMessage(new TranslationTextComponent(key), true);
        WrenchItem.playUseSound(player, world, hit.getLocation());
        return ActionResultType.SUCCESS;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        VoxelShape regular = super.getCollisionShape(state, world, pos, context);
        if (state.getValue(PART) != VentTerminalPart.TIP) return regular;
        boolean outwardPositive = state.getValue(OUTWARD_POSITIVE);

        VentAxis axis = state.getValue(AXIS);
        VoxelShape innerHalf;
        if (outwardPositive) {
            switch (axis) {
                case X: innerHalf = Block.box(0, 0, 0, 8, 16, 16); break;
                case Y: innerHalf = Block.box(0, 0, 0, 16, 8, 16); break;
                case Z: innerHalf = Block.box(0, 0, 0, 16, 16, 8); break;
                default: throw new IllegalStateException("Unsupported terminal axis: " + axis);
            }
        } else {
            switch (axis) {
                case X: innerHalf = Block.box(8, 0, 0, 16, 16, 16); break;
                case Y: innerHalf = Block.box(0, 8, 0, 16, 16, 16); break;
                case Z: innerHalf = Block.box(0, 0, 8, 16, 16, 16); break;
                default: throw new IllegalStateException("Unsupported terminal axis: " + axis);
            }
        }
        return VoxelShapes.join(regular, innerHalf, IBooleanFunction.AND);
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_TERMINAL_ITEM.get());
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OUTWARD_POSITIVE, PART, VISUAL);
    }
}
