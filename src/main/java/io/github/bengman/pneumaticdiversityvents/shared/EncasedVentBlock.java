package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.shared.world.RotatedVentShapeProvider;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

/* One-long-only vent with a full 2x2 outer envelope; network/force behaviour is otherwise identical to a normal vent. */
public final class EncasedVentBlock extends VentBlock {
    public EncasedVentBlock(AbstractBlock.Properties properties) {
        super(properties, RotatedVentShapeProvider.ENCASED, false);
    }

    @Override
    public boolean canReroute() {
        return false;
    }

    /* Encased vents are always a simple one-long piece; they have no mirrored/long/bend state family. */
    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(AXIS, CORNER);
    }

    @Override
    public ItemStack getCloneItemStack(IBlockReader world, BlockPos pos, BlockState state) {
        return new ItemStack(PneumaticDiversityVents.VENT_ENCASED_ITEM.get());
    }
}
