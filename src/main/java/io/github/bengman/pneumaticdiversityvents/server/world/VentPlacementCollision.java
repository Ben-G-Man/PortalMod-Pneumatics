/* Replaceable policy for deciding whether planned vent blocks can be placed. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

public interface VentPlacementCollision {
    boolean canPlace(BlockItemUseContext context, Collection<BlockPos> blocks);
}
