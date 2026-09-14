/* Coordinates physical world mutation with spatial/network registration. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class WorldVentManager {
    private WorldVentManager() {
    }

    public static void add(WorldVentBuilder builder) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(builder.getWorld());
        registry.register(builder.getEdge(), builder.getPlannedBlocks(), builder.getConnections());
        try {
            List<BlockPos> built = builder.build();
            if (!new HashSet<>(built).equals(new HashSet<>(builder.getPlannedBlocks()))) throw new IllegalStateException("Vent builder returned blocks different from its registered plan.");
        } catch (RuntimeException exception) {
            List<BlockPos> blocks = new ArrayList<>(registry.unregister(builder.getEdge().getId()));
            for (BlockPos position : blocks) {
                if (builder.getWorld().getBlockState(position).getBlock() instanceof VentBlock) builder.getWorld().setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            }
            throw exception;
        }
    }

    public static void remove(ServerWorld world, UUID edgeId) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        List<BlockPos> blocks = new ArrayList<>(registry.unregister(edgeId));
        for (BlockPos position : blocks) {
            if (world.getBlockState(position).getBlock() instanceof VentBlock) world.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
