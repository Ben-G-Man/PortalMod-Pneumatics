package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBranch;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionVisualState;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Places eight junction-owned cells; multipart rendering reuses long + inner-bend segment models to form the 2x2x2 T. */
public final class JunctionVentBuilder extends WorldVentBuilder {
    private final VentJunctionBlock block;
    private final BlockPos origin;
    private final VentAxis axis;
    private final VentJunctionBranch branch;
    private final boolean fixedPositive;
    private final VentJunctionVisualState visual;
    private final List<BlockPos> blocks;
    private final List<WorldVentConnection> connections;

    public JunctionVentBuilder(ServerWorld world, VentJunctionBlock block, BlockPos nearOrigin, VentAxis axis, Direction outward) {
        super(world, new VentEdge());
        if (outward.getAxis() != axis.getMinecraftAxis()) throw new IllegalArgumentException("Junction placement direction must follow its straight axis.");
        this.block = block;
        this.axis = axis;
        this.origin = outward.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? nearOrigin : VentPlacementUtil.offsetAlongAxis(nearOrigin, axis, Direction.AxisDirection.NEGATIVE);
        this.branch = VentJunctionBranch.TOP;
        // The face already touching the existing tube is the permanent/fixed side.
        this.fixedPositive = outward.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        this.visual = VentJunctionVisualState.STRAIGHT_OFF;

        List<BlockPos> all = new ArrayList<>(8);
        BlockPos second = VentPlacementUtil.offsetAlongAxis(origin, axis);
        Collections.addAll(all, VentPlacementUtil.getCornerPositions(origin, axis));
        Collections.addAll(all, VentPlacementUtil.getCornerPositions(second, axis));
        this.blocks = Collections.unmodifiableList(all);
        this.connections = Collections.unmodifiableList(VentJunctionManager.connectionsFor(edge, origin, axis, branch, fixedPositive, visual.isTurnActive()));
    }

    @Override
    public List<BlockPos> getPlannedBlocks() {
        return blocks;
    }

    @Override
    public Collection<WorldVentConnection> getConnections() {
        return connections;
    }

    /** MIRRORED remains the axial-layer discriminator used by the junction multipart assembly. */
    @Override
    public List<BlockPos> build() {
        BlockPos second = VentPlacementUtil.offsetAlongAxis(origin, axis);
        for (BlockPos p : VentPlacementUtil.getCornerPositions(origin, axis))
            world.setBlock(p, block.createState(axis, VentPlacementUtil.getCorner(origin, p, axis), false, branch, fixedPositive, visual), 3);
        for (BlockPos p : VentPlacementUtil.getCornerPositions(second, axis))
            world.setBlock(p, block.createState(axis, VentPlacementUtil.getCorner(second, p, axis), true, branch, fixedPositive, visual), 3);
        return blocks;
    }
}
