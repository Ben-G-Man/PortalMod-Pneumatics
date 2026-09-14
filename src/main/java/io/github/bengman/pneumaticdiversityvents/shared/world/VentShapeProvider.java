/* Abstracts vent collision/selection geometry from the block implementation. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.shapes.VoxelShape;

public interface VentShapeProvider {
    VoxelShape getShape(BlockState state);
}
