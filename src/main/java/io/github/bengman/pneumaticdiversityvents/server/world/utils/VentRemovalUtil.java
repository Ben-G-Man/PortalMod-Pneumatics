/* Converts physical vent-block removal callbacks into queued edge removals. */
package io.github.bengman.pneumaticdiversityvents.server.world.utils;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.VentUpdateQueue;
import io.github.bengman.pneumaticdiversityvents.server.update.VentRemoval;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.UUID;

public final class VentRemovalUtil {
    private VentRemovalUtil() {
    }

    public static void queue(World world, BlockPos position) {
        queue(world, position, false);
    }

    public static void queue(World world, BlockPos position, boolean dropItems) {
        if (!(world instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) world;
        UUID edgeId = VentSpatialRegistry.get(serverWorld).getEdgeId(position);
        if (edgeId != null) VentUpdateQueue.get().add(new VentRemoval(serverWorld, edgeId, dropItems));
    }
}
