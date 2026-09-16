/* PortalMod-compatible wrench/configuration behaviour for one-long impeller segments. */
package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.config.VentCommonConfig;
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
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.items.WrenchItem;
import net.portalmod.common.sorted.antline.AntlineConnector;

public final class VentImpellerBlock extends VentBlock implements AntlineConnector {
    public static int getForceUnits() {
        return VentCommonConfig.forceUnitsPerImpeller();
    }
    /* DIRECTION stores the configured/base physical direction only. Powered reverse changes force + textures, never model geometry. */
    public static final BooleanProperty DIRECTION = BooleanProperty.create("direction");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<VentImpellerVisualState> VISUAL = EnumProperty.create("visual", VentImpellerVisualState.class);

    public VentImpellerBlock(AbstractBlock.Properties properties) {
        super(properties, RotatedVentShapeProvider.ENCASED, false);
        registerDefaultState(defaultBlockState().setValue(DIRECTION, true).setValue(ACTIVE, true).setValue(VISUAL, VentImpellerVisualState.ALWAYS_ON));
    }

    @Override
    public BlockState createState(VentAxis axis, VentCorner corner, boolean mirrored) {
        return super.createState(axis, corner, mirrored).setValue(DIRECTION, true).setValue(ACTIVE, true).setValue(VISUAL, VentImpellerVisualState.ALWAYS_ON);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIRECTION, ACTIVE, VISUAL);
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (!WrenchItem.usedWrench(player, hand)) return super.use(state, world, pos, player, hand, hit);
        if (world.isClientSide) return ActionResultType.SUCCESS;

        ServerWorld serverWorld = (ServerWorld) world;
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        registry.prepareForceState(serverWorld);

        if (player.isShiftKeyDown()) {
            if (!registry.isImpellerAntlineControlled(pos)) return ActionResultType.SUCCESS;
            boolean changed = registry.cycleImpellerControlMode(serverWorld, pos);
            if (changed) {
                VentImpellerControlMode mode = registry.getImpellerControlMode(pos);
                player.displayClientMessage(new TranslationTextComponent(mode == VentImpellerControlMode.ON_OFF
                        ? "actionbar.pneumaticdiversityvents.impeller_mode.on_off"
                        : "actionbar.pneumaticdiversityvents.impeller_mode.reverse_when_powered"), true);
                WrenchItem.playUseSound(player, world, hit.getLocation());
                return ActionResultType.SUCCESS;
            }
            return ActionResultType.FAIL;
        }

        boolean changed = registry.invertImpeller(serverWorld, pos);
        Vector3d center = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (changed) WrenchItem.playUseSound(player, world, center);
        else WrenchItem.playFailSound(player, world, center);
        return changed ? ActionResultType.SUCCESS : ActionResultType.FAIL;
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
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_IMPELLER_ITEM.get());
    }
}
