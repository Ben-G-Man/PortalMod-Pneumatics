package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.integration.VentAntlineConnections;
import io.github.bengman.pneumaticdiversityvents.shared.world.RotatedVentShapeProvider;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.portalmod.common.items.WrenchItem;
import net.portalmod.common.sorted.antline.AntlineActivator;

import java.util.UUID;

public final class VentScannerBlock extends VentBlock implements AntlineActivator {
    public static final EnumProperty<VentScannerVisualState> VISUAL = EnumProperty.create("visual", VentScannerVisualState.class);

    public VentScannerBlock(AbstractBlock.Properties properties) {
        super(properties, RotatedVentShapeProvider.ENCASED, false);
        registerDefaultState(defaultBlockState().setValue(VISUAL, VentScannerVisualState.CUBES_OFF));
    }

    @Override
    public BlockState createState(VentAxis axis, VentCorner corner, boolean mirrored) {
        return super.createState(axis, corner, mirrored).setValue(VISUAL, VentScannerVisualState.CUBES_OFF);
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!WrenchItem.usedWrench(player, hand)) return super.use(state, world, pos, player, hand, hit);
        if (world.isClientSide) return ActionResultType.SUCCESS;

        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        UUID edgeId = registry.getEdgeId(pos);
        if (edgeId == null) return ActionResultType.FAIL;
        VentScannerVisualState next = state.getValue(VISUAL).nextFilter();
        for (BlockPos block : registry.getBlocks(edgeId)) {
            BlockState blockState = world.getBlockState(block);
            if (blockState.getBlock() == this && blockState.hasProperty(VISUAL)) world.setBlock(block, blockState.setValue(VISUAL, next), 3);
        }
        player.displayClientMessage(new TranslationTextComponent(next.getFilter() == VentScannerVisualState.Filter.CUBES
                ? "actionbar.pneumaticdiversityvents.scanner_mode.cubes" : "actionbar.pneumaticdiversityvents.scanner_mode.player"), true);
        WrenchItem.playUseSound(player, world, hit.getLocation());
        return ActionResultType.SUCCESS;
    }

    @Override
    public Direction getHorsedOn(BlockState state) {
        return VentAntlineConnections.getHorsedOn(state);
    }

    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return VentAntlineConnections.connectsInDirection(direction, state);
    }

    @Override
    public boolean isAntlineActive(BlockState state) {
        return state.hasProperty(VISUAL) && state.getValue(VISUAL).isActive();
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_SCANNER_ITEM.get());
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VISUAL);
    }
}
