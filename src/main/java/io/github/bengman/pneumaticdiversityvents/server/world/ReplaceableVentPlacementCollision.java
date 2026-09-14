/* Current placement policy: every target block must be replaceable and not already queued. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.VentUpdateQueue;

import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;

public final class ReplaceableVentPlacementCollision implements VentPlacementCollision {
    @Override
    public boolean canPlace(BlockItemUseContext context, Collection<BlockPos> blocks) {
        World world = context.getLevel();
        if (!(world instanceof ServerWorld)) return false;
        ServerWorld serverWorld = (ServerWorld) world;
        for (BlockPos position : blocks) {
            if (VentUpdateQueue.get().isBlockPending(serverWorld, position) || !world.getBlockState(position).canBeReplaced(context)) return false;
        }
        return true;
    }
}
