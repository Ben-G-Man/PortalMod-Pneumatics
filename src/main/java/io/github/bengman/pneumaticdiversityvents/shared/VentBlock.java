/* Defines vent blockstate/shape behaviour and delegates physical removal handling. */
package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentRemovalUtil;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfile;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfileProvider;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfiles;
import io.github.bengman.pneumaticdiversityvents.shared.world.RotatedVentShapeProvider;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentShapeProvider;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class VentBlock extends Block implements VentExternalFieldProfileProvider {
    public static final EnumProperty<VentAxis> AXIS = EnumProperty.create("axis", VentAxis.class);
    public static final EnumProperty<VentCorner> CORNER = EnumProperty.create("corner", VentCorner.class);
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");

    private final VentShapeProvider shapes;
    private final boolean extendable;

    public VentBlock(AbstractBlock.Properties properties) {
        this(properties, RotatedVentShapeProvider.STRAIGHT, false);
    }

    public VentBlock(AbstractBlock.Properties properties, boolean extendable) {
        this(properties, RotatedVentShapeProvider.STRAIGHT, extendable);
    }

    public VentBlock(AbstractBlock.Properties properties, VentShapeProvider shapes) {
        this(properties, shapes, false);
    }

    public VentBlock(AbstractBlock.Properties properties, VentShapeProvider shapes, boolean extendable) {
        super(properties.noOcclusion());
        this.shapes = shapes;
        this.extendable = extendable;
        BlockState state = defaultBlockState().setValue(AXIS, VentAxis.Z).setValue(CORNER, VentCorner.BOTTOM_RIGHT);
        if (state.hasProperty(MIRRORED)) state = state.setValue(MIRRORED, false);
        registerDefaultState(state);
    }

    public boolean isExtendable() {
        return extendable;
    }

    @Override
    public VentExternalFieldProfile getExternalFieldProfile(boolean intake) {
        return intake ? VentExternalFieldProfiles.STANDARD_INTAKE : VentExternalFieldProfiles.STANDARD_EXHAUST;
    }

    public BlockState createState(VentAxis axis, VentCorner corner, boolean mirrored) {
        BlockState state = defaultBlockState().setValue(AXIS, axis).setValue(CORNER, corner);
        return state.hasProperty(MIRRORED) ? state.setValue(MIRRORED, mirrored) : state;
    }

    /** Whether this item may be used as the continuation when shift-rerouting an existing long vent. */
    public boolean canReroute() {
        return true;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return shapes.getShape(state);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return shapes.getShape(state);
    }

    /* Selection/raycasting intentionally remains a whole block. */
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return VoxelShapes.block();
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_SEGMENT_ITEM.get());
    }

    @Override
    public void playerWillDestroy(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClientSide && !player.abilities.instabuild) VentRemovalUtil.queue(world, pos, true);
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void wasExploded(World world, BlockPos pos, Explosion explosion) {
        if (!world.isClientSide) VentRemovalUtil.queue(world, pos, true);
        super.wasExploded(world, pos, explosion);
    }

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !world.isClientSide) VentRemovalUtil.queue(world, pos);
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(AXIS, CORNER, MIRRORED);
    }
}
