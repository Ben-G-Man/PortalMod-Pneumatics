/* Builds one-block-long straight vent geometry and its two physical endpoints. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentForceSource;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ShortVentBuilder extends WorldVentBuilder {
    private final VentBlock block;
    private final BlockPos origin;
    private final VentAxis axis;
    private final List<BlockPos> blocks;
    private final List<WorldVentConnection> connections;

    public ShortVentBuilder(ServerWorld world, VentBlock block, BlockPos origin, VentAxis axis) {
        super(world, new VentEdge());
        this.block = block;
        if (block instanceof VentImpellerBlock) edge.setForceSource(new VentForceSource(VentImpellerBlock.getForceUnits(), true));
        this.origin = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        this.axis = axis;
        this.blocks = Collections.unmodifiableList(Arrays.asList(VentPlacementUtil.getCornerPositions(origin, axis)));
        VentPlacementUtil.ConnectionEndpoints endpoints = VentPlacementUtil.getConnectionEndpoints(origin, axis, 1);
        this.connections = Collections.unmodifiableList(Arrays.asList(
                new WorldVentConnection(edge.getA().getId(), endpoints.getA(), axis),
                new WorldVentConnection(edge.getB().getId(), endpoints.getB(), axis)));
    }

    @Override
    public List<BlockPos> getPlannedBlocks() {
        return blocks;
    }

    @Override
    public Collection<WorldVentConnection> getConnections() {
        return connections;
    }

    @Override
    public List<BlockPos> build() {
        for (BlockPos position : blocks) {
            world.setBlock(position, block.createState(axis, VentPlacementUtil.getCorner(origin, position, axis), false), 3);
        }
        return blocks;
    }
}
