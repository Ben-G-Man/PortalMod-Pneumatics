/* Rotates voxel shapes and cardinal directions in quarter turns around the block centre. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

public final class VoxelShapeRotation {
    private VoxelShapeRotation() {
    }

    public static VoxelShape rotate(VoxelShape shape, Direction.Axis axis, int turns) {
        VoxelShape result = shape;
        for (int i = 0; i < (turns & 3); i++) result = rotate90(result, axis);
        return result.optimize();
    }

    public static Direction rotate(Direction direction, Direction.Axis axis, int turns) {
        Direction result = direction;
        for (int i = 0; i < (turns & 3); i++) result = rotate90(result, axis);
        return result;
    }

    private static Direction rotate90(Direction direction, Direction.Axis axis) {
        int x = direction.getStepX(), y = direction.getStepY(), z = direction.getStepZ();
        int rx = x, ry = y, rz = z;
        switch (axis) {
            case X: ry = -z; rz = y; break;
            case Y: rx = -z; rz = x; break;
            case Z: rx = -y; ry = x; break;
        }
        for (Direction candidate : Direction.values())
            if (candidate.getStepX() == rx && candidate.getStepY() == ry && candidate.getStepZ() == rz) return candidate;
        throw new IllegalStateException("Rotated direction was not cardinal.");
    }

    private static VoxelShape rotate90(VoxelShape shape, Direction.Axis axis) {
        VoxelShape result = VoxelShapes.empty();
        for (AxisAlignedBB box : shape.toAabbs()) {
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int xi = 0; xi < 2; xi++) for (int yi = 0; yi < 2; yi++) for (int zi = 0; zi < 2; zi++) {
                double x = xi == 0 ? box.minX : box.maxX;
                double y = yi == 0 ? box.minY : box.maxY;
                double z = zi == 0 ? box.minZ : box.maxZ;
                double rx = x, ry = y, rz = z;
                switch (axis) {
                    case X: ry = 1.0D - z; rz = y; break;
                    case Y: rx = 1.0D - z; rz = x; break;
                    case Z: rx = 1.0D - y; ry = x; break;
                }
                min[0] = Math.min(min[0], rx); min[1] = Math.min(min[1], ry); min[2] = Math.min(min[2], rz);
                max[0] = Math.max(max[0], rx); max[1] = Math.max(max[1], ry); max[2] = Math.max(max[2], rz);
            }
            result = VoxelShapes.or(result, VoxelShapes.box(min[0], min[1], min[2], max[0], max[1], max[2]));
        }
        return result;
    }
}
