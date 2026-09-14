/* Stores the world-space endpoint corresponding to one abstract network connection. */
package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.UUID;

public final class WorldVentConnection {
    private final UUID connectionId;
    private final BlockPos coordinate;
    private final VentAxis axis;
    private final boolean connectable;

    public WorldVentConnection(UUID connectionId, BlockPos coordinate, VentAxis axis) {
        this(connectionId, coordinate, axis, true);
    }

    public WorldVentConnection(UUID connectionId, BlockPos coordinate, VentAxis axis, boolean connectable) {
        this.connectionId = connectionId;
        this.coordinate = new BlockPos(coordinate.getX(), coordinate.getY(), coordinate.getZ());
        this.axis = axis;
        this.connectable = connectable;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public BlockPos getCoordinate() {
        return coordinate;
    }

    public VentAxis getAxis() {
        return axis;
    }

    public boolean isConnectable() {
        return connectable;
    }

    public Vector3d getWorldPosition() {
        return new Vector3d(coordinate.getX() / 2.0D, coordinate.getY() / 2.0D, coordinate.getZ() / 2.0D);
    }
}
