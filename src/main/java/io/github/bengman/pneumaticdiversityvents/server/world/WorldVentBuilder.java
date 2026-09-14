/* Base contract for physical vent builders consumed by addition/upgrade updates. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.List;

public abstract class WorldVentBuilder {
    protected final ServerWorld world;
    protected final VentEdge edge;

    protected WorldVentBuilder(ServerWorld world, VentEdge edge) {
        this.world = world;
        this.edge = edge;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public VentEdge getEdge() {
        return edge;
    }
    public abstract List<BlockPos> getPlannedBlocks();
    public abstract Collection<WorldVentConnection> getConnections();
    public abstract List<BlockPos> build();
}
