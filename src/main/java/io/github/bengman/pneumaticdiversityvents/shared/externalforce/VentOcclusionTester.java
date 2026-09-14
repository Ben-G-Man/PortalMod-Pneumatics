/* Tests airflow line-of-sight against real block collision shapes while honoring the force-transparent tag. */
package io.github.bengman.pneumaticdiversityvents.shared.externalforce;

import io.github.bengman.pneumaticdiversityvents.shared.VentTags;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;

import javax.annotation.Nullable;

import java.util.Set;

public final class VentOcclusionTester {
    private static final double EPSILON = 1.0E-9D;
    private static final int MAX_STEPS = 128;

    private VentOcclusionTester() {
    }

    public static boolean isClear(IBlockReader world, @Nullable Entity entity, Vector3d from, Vector3d to, Set<Long> ignoredBlocks) {
        Vector3d delta = to.subtract(from);
        if (delta.lengthSqr() < EPSILON) return true;
        RayTraceContext context = new RayTraceContext(from, to, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, entity);

        int x = MathHelper.floor(from.x), y = MathHelper.floor(from.y), z = MathHelper.floor(from.z);
        int endX = MathHelper.floor(to.x), endY = MathHelper.floor(to.y), endZ = MathHelper.floor(to.z);
        int stepX = sign(delta.x), stepY = sign(delta.y), stepZ = sign(delta.z);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / delta.x);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / delta.y);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / delta.z);
        double tMaxX = initialT(from.x, x, delta.x, stepX);
        double tMaxY = initialT(from.y, y, delta.y, stepY);
        double tMaxZ = initialT(from.z, z, delta.z, stepZ);

        for (int steps = 0; steps < MAX_STEPS; steps++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!ignoredBlocks.contains(pos.asLong())) {
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && !state.is(VentTags.FORCE_TRANSPARENT)) {
                    VoxelShape shape = context.getBlockShape(state, world, pos);
                    if (!shape.isEmpty() && shape.clip(from, to, pos) != null) return false;
                }
            }
            if (x == endX && y == endY && z == endZ) return true;

            double next = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tMaxX <= next + EPSILON) {
                x += stepX;
                tMaxX += tDeltaX;
            }

            if (tMaxY <= next + EPSILON) {
                y += stepY;
                tMaxY += tDeltaY;
            }

            if (tMaxZ <= next + EPSILON) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return true; // Field ranges are short; reaching this guard is safer than creating an invisible hard wall.
    }

    private static int sign(double value) {
        return value > EPSILON ? 1 : value < -EPSILON ? -1 : 0;
    }

    private static double initialT(double coordinate, int block, double delta, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0D : block;
        return Math.max(0.0D, (boundary - coordinate) / delta);
    }
}
