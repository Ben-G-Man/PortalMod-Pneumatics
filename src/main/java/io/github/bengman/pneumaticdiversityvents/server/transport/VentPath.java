/* Converts one physical vent edge into a centerline suitable for deterministic transport and open-end capture. */
package io.github.bengman.pneumaticdiversityvents.server.transport;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.network.VentConnection;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentConnection;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.List;
import java.util.UUID;

public final class VentPath {
    private static final double HALF_PI = Math.PI / 2.0D;

    private final UUID edgeId;
    private final Vector3d a, b;
    private final Direction aDirection, bDirection;
    private final AxisAlignedBB bounds;
    private final boolean bend;
    private final Vector3d bendCenter;
    private final double length;

    private VentPath(UUID edgeId, Vector3d a, Vector3d b, Direction aDirection, Direction bDirection,
            AxisAlignedBB bounds, boolean bend, Vector3d bendCenter, double length) {
        this.edgeId = edgeId;
        this.a = a;
        this.b = b;
        this.aDirection = aDirection;
        this.bDirection = bDirection;
        this.bounds = bounds;
        this.bend = bend;
        this.bendCenter = bendCenter;
        this.length = length;
    }

    public static VentPath of(VentSpatialRegistry registry, VentEdge edge) {
        List<BlockPos> blocks = registry.getBlocks(edge.getId());
        if (blocks.isEmpty()) throw new IllegalArgumentException("Vent edge has no physical blocks: " + edge.getId());

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }

        WorldVentConnection worldA = requireWorldConnection(registry, edge.getA());
        WorldVentConnection worldB = requireWorldConnection(registry, edge.getB());
        Direction dirA = getFaceDirection(worldA, minX, minY, minZ, maxX, maxY, maxZ);
        Direction dirB = getFaceDirection(worldB, minX, minY, minZ, maxX, maxY, maxZ);
        Vector3d pointA = getFaceCenter(dirA, minX, minY, minZ, maxX, maxY, maxZ);
        Vector3d pointB = getFaceCenter(dirB, minX, minY, minZ, maxX, maxY, maxZ);
        AxisAlignedBB bounds = new AxisAlignedBB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);

        boolean bend = worldA.getAxis() != worldB.getAxis();
        if (!bend)
            return new VentPath(edge.getId(), pointA, pointB, dirA, dirB, bounds, false, null, pointA.distanceTo(pointB));

        Vector3d cubeCenter = new Vector3d((minX + maxX + 1) / 2.0D, (minY + maxY + 1) / 2.0D, (minZ + maxZ + 1) / 2.0D);
        Vector3d bendCenter = cubeCenter.add(directionVector(dirA)).add(directionVector(dirB));
        return new VentPath(edge.getId(), pointA, pointB, dirA, dirB, bounds, true, bendCenter, HALF_PI);
    }

    private static WorldVentConnection requireWorldConnection(VentSpatialRegistry registry, VentConnection connection) {
        WorldVentConnection world = registry.getWorldConnection(connection.getId());
        if (world == null) throw new IllegalStateException("Missing world metadata for vent connection " + connection.getId());
        return world;
    }

    private static Direction getFaceDirection(WorldVentConnection connection, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos coordinate = connection.getCoordinate();
        VentAxis axis = connection.getAxis();
        switch (axis) {
            case X:
                if (coordinate.getX() == minX * 2) return Direction.WEST;
                if (coordinate.getX() == (maxX + 1) * 2) return Direction.EAST;
                break;
            case Y:
                if (coordinate.getY() == minY * 2) return Direction.DOWN;
                if (coordinate.getY() == (maxY + 1) * 2) return Direction.UP;
                break;
            case Z:
                if (coordinate.getZ() == minZ * 2) return Direction.NORTH;
                if (coordinate.getZ() == (maxZ + 1) * 2) return Direction.SOUTH;
                break;
        }
        throw new IllegalStateException("Vent connection does not lie on its edge bounds: " + connection.getConnectionId());
    }

    private static Vector3d getFaceCenter(Direction direction, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        double cx = (minX + maxX + 1) / 2.0D, cy = (minY + maxY + 1) / 2.0D, cz = (minZ + maxZ + 1) / 2.0D;
        switch (direction) {
            case WEST: return new Vector3d(minX, cy, cz);
            case EAST: return new Vector3d(maxX + 1.0D, cy, cz);
            case DOWN: return new Vector3d(cx, minY, cz);
            case UP: return new Vector3d(cx, maxY + 1.0D, cz);
            case NORTH: return new Vector3d(cx, cy, minZ);
            case SOUTH: return new Vector3d(cx, cy, maxZ + 1.0D);
            default: throw new IllegalArgumentException("Unsupported vent face: " + direction);
        }
    }

    public UUID getEdgeId() {
        return edgeId;
    }

    public double getLength() {
        return length;
    }

    public AxisAlignedBB getBounds() {
        return bounds;
    }

    public Direction getADirection() {
        return aDirection;
    }

    public Direction getBDirection() {
        return bDirection;
    }

    public Vector3d getA() {
        return a;
    }

    public Vector3d getB() {
        return b;
    }

    public Vector3d sample(double distance) {
        double d = clamp(distance, 0.0D, length);
        if (!bend) {
            double t = length == 0.0D ? 0.0D : d / length;
            return a.add(b.subtract(a).scale(t));
        }

        double theta = d;
        Vector3d da = directionVector(aDirection), db = directionVector(bDirection);
        return bendCenter.subtract(db.scale(Math.cos(theta))).subtract(da.scale(Math.sin(theta)));
    }

    public Vector3d tangent(double distance, boolean aToB) {
        Vector3d tangent;
        if (!bend) tangent = b.subtract(a).normalize();
        else {
            double theta = clamp(distance, 0.0D, length);
            Vector3d da = directionVector(aDirection), db = directionVector(bDirection);
            tangent = db.scale(Math.sin(theta)).subtract(da.scale(Math.cos(theta))).normalize();
        }
        return aToB ? tangent : tangent.scale(-1.0D);
    }

    public Projection project(Vector3d point) {
        double distance;
        if (!bend) {
            Vector3d ab = b.subtract(a);
            double lengthSq = dot(ab, ab);
            double t = lengthSq == 0.0D ? 0.0D : clamp(dot(point.subtract(a), ab) / lengthSq, 0.0D, 1.0D);
            distance = t * length;
        } else {
            Vector3d rel = point.subtract(bendCenter);
            Vector3d da = directionVector(aDirection), db = directionVector(bDirection);
            double cosPart = -dot(rel, db), sinPart = -dot(rel, da);
            distance = clamp(Math.atan2(sinPart, cosPart), 0.0D, HALF_PI);
        }
        Vector3d closest = sample(distance);
        return new Projection(distance, closest, point.distanceToSqr(closest));
    }

    /* Returns the centerline point a given distance outside one open face. */
    public Vector3d outside(boolean atA, double distance) {
        Vector3d endpoint = atA ? a : b;
        return endpoint.add(outwardDirection(atA).scale(Math.max(0.0D, distance)));
    }

    /* Projects a point onto the straight centerline extension outside one face. */
    public OutsideProjection projectOutside(boolean atA, Vector3d point) {
        Vector3d endpoint = atA ? a : b;
        Vector3d outward = outwardDirection(atA);
        double distance = Math.max(0.0D, dot(point.subtract(endpoint), outward));
        Vector3d closest = endpoint.add(outward.scale(distance));
        return new OutsideProjection(distance, closest, point.distanceToSqr(closest));
    }

    /* Conservative query box for entities immediately in front of an open 2x2 vent face. */
    public AxisAlignedBB getOutsideCaptureBounds(boolean atA, double depth, double halfWidth) {
        Vector3d endpoint = atA ? a : b;
        Vector3d end = endpoint.add(outwardDirection(atA).scale(depth));
        Direction.Axis axis = (atA ? aDirection : bDirection).getAxis();
        double minX = Math.min(endpoint.x, end.x), maxX = Math.max(endpoint.x, end.x);
        double minY = Math.min(endpoint.y, end.y), maxY = Math.max(endpoint.y, end.y);
        double minZ = Math.min(endpoint.z, end.z), maxZ = Math.max(endpoint.z, end.z);
        if (axis != Direction.Axis.X) { minX -= halfWidth; maxX += halfWidth; } else { minX -= 0.01D; maxX += 0.01D; }

        if (axis != Direction.Axis.Y) { minY -= halfWidth; maxY += halfWidth; } else { minY -= 0.01D; maxY += 0.01D; }

        if (axis != Direction.Axis.Z) { minZ -= halfWidth; maxZ += halfWidth; } else { minZ -= 0.01D; maxZ += 0.01D; }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Vector3d outwardDirection(boolean atA) {
        return directionVector(atA ? aDirection : bDirection);
    }

    public Vector3d outsideA(double distance) {
        return outside(true, distance);
    }

    public Vector3d outsideB(double distance) {
        return outside(false, distance);
    }

    private static Vector3d directionVector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static double dot(Vector3d a, Vector3d b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Projection {
        private final double distance;
        private final Vector3d point;
        private final double distanceSquared;

        private Projection(double distance, Vector3d point, double distanceSquared) {
            this.distance = distance; this.point = point; this.distanceSquared = distanceSquared;
        }

        public double getDistance() {
            return distance;
        }

        public Vector3d getPoint() {
            return point;
        }

        public double getDistanceSquared() {
            return distanceSquared;
        }
    }

    public static final class OutsideProjection {
        private final double distance;
        private final Vector3d point;
        private final double radialDistanceSquared;

        private OutsideProjection(double distance, Vector3d point, double radialDistanceSquared) {
            this.distance = distance; this.point = point; this.radialDistanceSquared = radialDistanceSquared;
        }

        public double getDistance() {
            return distance;
        }

        public Vector3d getPoint() {
            return point;
        }

        public double getRadialDistanceSquared() {
            return radialDistanceSquared;
        }
    }
}
