package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalPart;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TerminalVentBuilder extends WorldVentBuilder {
    private final VentTerminalBlock block;
    private final BlockPos origin;
    private final VentAxis axis;
    private final Direction outward;
    private final List<BlockPos> blocks;
    private final List<WorldVentConnection> connections;

    public TerminalVentBuilder(ServerWorld world, VentTerminalBlock block, BlockPos nearOrigin, VentAxis axis, Direction outward) {
        super(world, new VentEdge());
        this.block = block;
        this.axis = axis;
        this.outward = outward;
        this.origin = outward.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? nearOrigin : VentPlacementUtil.offsetAlongAxis(nearOrigin, axis, Direction.AxisDirection.NEGATIVE);
        BlockPos second = VentPlacementUtil.offsetAlongAxis(origin, axis);
        List<BlockPos> all = new ArrayList<>(8);
        all.addAll(Arrays.asList(VentPlacementUtil.getCornerPositions(origin, axis)));
        all.addAll(Arrays.asList(VentPlacementUtil.getCornerPositions(second, axis)));
        this.blocks = Collections.unmodifiableList(all);
        VentPlacementUtil.ConnectionEndpoints endpoints = VentPlacementUtil.getConnectionEndpoints(origin, axis, 2);
        boolean outwardPositive = outward.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // A is the negative-axis face and B the positive-axis face. Exactly the outward face is non-connectable; the rear remains a normal graph endpoint.
        boolean aConnectable = outwardPositive;
        boolean bConnectable = !outwardPositive;
        this.connections = Collections.unmodifiableList(Arrays.asList(
                new WorldVentConnection(edge.getA().getId(), endpoints.getA(), axis, aConnectable),
                new WorldVentConnection(edge.getB().getId(), endpoints.getB(), axis, bConnectable)));
    }

    @Override
    public List<BlockPos> getPlannedBlocks() {
        return blocks;
    }

    @Override
    public Collection<WorldVentConnection> getConnections() {
        return connections;
    }

    @Override public List<BlockPos> build() {
        BlockPos second = VentPlacementUtil.offsetAlongAxis(origin, axis);
        boolean outwardPositive = outward.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        VentTerminalPart firstPart = outwardPositive ? VentTerminalPart.BODY : VentTerminalPart.TIP;
        VentTerminalPart secondPart = outwardPositive ? VentTerminalPart.TIP : VentTerminalPart.BODY;
        for (BlockPos p : VentPlacementUtil.getCornerPositions(origin, axis))
            world.setBlock(p, block.createState(axis, VentPlacementUtil.getCorner(origin, p, axis), firstPart, outwardPositive), 3);
        for (BlockPos p : VentPlacementUtil.getCornerPositions(second, axis))
            world.setBlock(p, block.createState(axis, VentPlacementUtil.getCorner(second, p, axis), secondPart, outwardPositive), 3);
        return blocks;
    }
}
