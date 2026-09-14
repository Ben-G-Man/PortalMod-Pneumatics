/* Builds one 2x2x2 elbow from four straight long corners plus two inner and two outer bend corners. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTurnBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentBendOrientation;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentBendPiece;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BendVentBuilder extends WorldVentBuilder {
    private final VentBlock longBlock;
    private final VentTurnBlock turnBlock;
    private final BlockPos origin;
    private final Direction connectedDirection;
    private final Direction outgoingDirection;
    private final VentBendOrientation orientation;
    private final List<BlockPos> blocks;
    private final List<WorldVentConnection> connections;

    public BendVentBuilder(ServerWorld world, VentBlock longBlock, VentTurnBlock turnBlock, BlockPos origin,
            VentAxis connectedAxis, Direction connectedDirection, Direction outgoingDirection) {
        super(world, new VentEdge());
        if (connectedDirection.getAxis() != connectedAxis.getMinecraftAxis())
            throw new IllegalArgumentException("Connected direction must use the connected axis.");
        if (outgoingDirection.getAxis() == connectedDirection.getAxis())
            throw new IllegalArgumentException("A 90 degree bend requires perpendicular connection axes.");

        this.longBlock = longBlock;
        this.turnBlock = turnBlock;
        this.origin = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        this.connectedDirection = connectedDirection;
        this.outgoingDirection = outgoingDirection;
        this.orientation = VentBendOrientation.of(connectedDirection, outgoingDirection);

        List<BlockPos> planned = new ArrayList<>(8);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++)
            planned.add(this.origin.offset(x, y, z));
        this.blocks = Collections.unmodifiableList(planned);
        this.connections = Collections.unmodifiableList(Arrays.asList(
                new WorldVentConnection(edge.getA().getId(), VentPlacementUtil.getFaceConnectionCoordinate(this.origin, connectedDirection, 2), VentAxis.fromDirection(connectedDirection)),
                new WorldVentConnection(edge.getB().getId(), VentPlacementUtil.getFaceConnectionCoordinate(this.origin, outgoingDirection, 2), VentAxis.fromDirection(outgoingDirection))));
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
        Direction cross = orientation.getCrossDirection();
        for (BlockPos position : blocks) {
            boolean connectedSide = isOnFace(position, connectedDirection);
            boolean outgoingSide = isOnFace(position, outgoingDirection);

            if (connectedSide == outgoingSide) {
                VentBendPiece piece = connectedSide ? VentBendPiece.INNER : VentBendPiece.OUTER;
                Direction positionCross = isOnFace(position, cross) ? cross : cross.getOpposite();
                boolean mirrored = positionCross != cross;
                world.setBlock(position, turnBlock.createState(piece, orientation, mirrored), 3);
                continue;
            }

            Direction straightDirection = connectedSide ? connectedDirection : outgoingDirection;
            VentAxis axis = VentAxis.fromDirection(straightDirection);
            BlockPos sliceOrigin = sliceOrigin(position, axis);
            VentCorner corner = VentPlacementUtil.getCorner(sliceOrigin, position, axis);
            boolean mirrored = straightDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            world.setBlock(position, longBlock.createState(axis, corner, mirrored), 3);
        }
        return blocks;
    }

    private boolean isOnFace(BlockPos position, Direction direction) {
        switch (direction) {
            case WEST: return position.getX() == origin.getX();
            case EAST: return position.getX() == origin.getX() + 1;
            case DOWN: return position.getY() == origin.getY();
            case UP: return position.getY() == origin.getY() + 1;
            case NORTH: return position.getZ() == origin.getZ();
            case SOUTH: return position.getZ() == origin.getZ() + 1;
            default: throw new IllegalArgumentException("Unsupported direction: " + direction);
        }
    }

    private BlockPos sliceOrigin(BlockPos position, VentAxis axis) {
        switch (axis) {
            case X: return new BlockPos(position.getX(), origin.getY(), origin.getZ());
            case Y: return new BlockPos(origin.getX(), position.getY(), origin.getZ());
            case Z: return new BlockPos(origin.getX(), origin.getY(), position.getZ());
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }
}
