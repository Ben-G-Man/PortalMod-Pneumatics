/* Represents the inner/outer visual corner pieces used inside a two-connection bend. */
package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.shared.world.BendVentShapeProvider;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentBendOrientation;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentBendPiece;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;

public final class VentTurnBlock extends VentBlock {
    public static final EnumProperty<VentBendPiece> PIECE = EnumProperty.create("piece", VentBendPiece.class);
    public static final EnumProperty<VentBendOrientation> ORIENTATION = EnumProperty.create("orientation", VentBendOrientation.class);

    public VentTurnBlock(AbstractBlock.Properties properties) {
        super(properties, BendVentShapeProvider.INSTANCE, false);
        registerDefaultState(defaultBlockState().setValue(PIECE, VentBendPiece.INNER).setValue(ORIENTATION, VentBendOrientation.UP_SOUTH));
    }

    public BlockState createState(VentBendPiece piece, VentBendOrientation orientation, boolean mirrored) {
        return defaultBlockState().setValue(PIECE, piece).setValue(ORIENTATION, orientation).setValue(MIRRORED, mirrored);
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PIECE, ORIENTATION);
    }
}
