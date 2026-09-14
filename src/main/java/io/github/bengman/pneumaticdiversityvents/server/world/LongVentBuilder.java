/* Builds two-block-long straight vent geometry used by short-segment extensions and bend straightening. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class LongVentBuilder extends WorldVentBuilder {
    private final VentBlock block;
    private final BlockPos origin;
    private final VentAxis axis;
    private final List<BlockPos> blocks;
    private final List<BlockPos> extensionBlocks;
    private final List<WorldVentConnection> connections;

    public LongVentBuilder(ServerWorld world, VentBlock block, BlockPos shortOrigin, VentAxis axis, Direction.AxisDirection extensionDirection) {
        super(world, new VentEdge());
        this.block = block;
        this.axis = axis;
        this.origin = extensionDirection == Direction.AxisDirection.NEGATIVE
                ? VentPlacementUtil.offsetAlongAxis(shortOrigin, axis, Direction.AxisDirection.NEGATIVE)
                : new BlockPos(shortOrigin.getX(), shortOrigin.getY(), shortOrigin.getZ());

        BlockPos secondOrigin = VentPlacementUtil.offsetAlongAxis(origin, axis);
        List<BlockPos> first = Arrays.asList(VentPlacementUtil.getCornerPositions(origin, axis));
        List<BlockPos> second = Arrays.asList(VentPlacementUtil.getCornerPositions(secondOrigin, axis));
        List<BlockPos> planned = new ArrayList<>(8);
        planned.addAll(first); planned.addAll(second);
        this.blocks = Collections.unmodifiableList(planned);
        this.extensionBlocks = Collections.unmodifiableList(new ArrayList<>(extensionDirection == Direction.AxisDirection.NEGATIVE ? first : second));

        VentPlacementUtil.ConnectionEndpoints endpoints = VentPlacementUtil.getConnectionEndpoints(origin, axis, 2);
        this.connections = Collections.unmodifiableList(Arrays.asList(
                new WorldVentConnection(edge.getA().getId(), endpoints.getA(), axis),
                new WorldVentConnection(edge.getB().getId(), endpoints.getB(), axis)));
    }

    public static LongVentBuilder fromOrigin(ServerWorld world, VentBlock block, BlockPos origin, VentAxis axis) {
        BlockPos positiveHalf = VentPlacementUtil.offsetAlongAxis(origin, axis);
        return new LongVentBuilder(world, block, positiveHalf, axis, Direction.AxisDirection.NEGATIVE);
    }

    @Override
    public List<BlockPos> getPlannedBlocks() {
        return blocks;
    }

    public List<BlockPos> getExtensionBlocks() {
        return extensionBlocks;
    }

    @Override
    public Collection<WorldVentConnection> getConnections() {
        return connections;
    }

    @Override
    public List<BlockPos> build() {
        BlockPos secondOrigin = VentPlacementUtil.offsetAlongAxis(origin, axis);
        for (BlockPos position : VentPlacementUtil.getCornerPositions(origin, axis))
            world.setBlock(position, block.createState(axis, VentPlacementUtil.getCorner(origin, position, axis), false), 3);
        for (BlockPos position : VentPlacementUtil.getCornerPositions(secondOrigin, axis))
            world.setBlock(position, block.createState(axis, VentPlacementUtil.getCorner(secondOrigin, position, axis), true), 3);
        return blocks;
    }
}
