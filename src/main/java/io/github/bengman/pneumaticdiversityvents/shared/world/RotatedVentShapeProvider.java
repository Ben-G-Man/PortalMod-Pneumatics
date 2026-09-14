/* Caches every axis/corner rotation of one canonical vent shape for reuse by vent blocks. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

import java.util.EnumMap;
import java.util.Map;

public final class RotatedVentShapeProvider implements VentShapeProvider {
    /* Canonical straight-vent orientation: Z axis, bottom-right corner. */
    public static final VentShapeProvider STRAIGHT = new RotatedVentShapeProvider(VoxelShapes.or(
            Block.box(12, 2, 0, 14, 16, 16),
            Block.box(0, 2, 0, 12, 4, 16)).optimize());
    /* Same inner opening as the regular vent, but its outer shell reaches the full 2x2 footprint. */
    public static final VentShapeProvider ENCASED = new RotatedVentShapeProvider(VoxelShapes.or(
            Block.box(12, 2, 0, 14, 16, 16),
            Block.box(0, 2, 0, 12, 4, 16),
            Block.box(14, 0, 0, 16, 16, 16),
            Block.box(0, 0, 0, 14, 2, 16)).optimize());

    private final Map<VentAxis, Map<VentCorner, VoxelShape>> shapes;

    public RotatedVentShapeProvider(VoxelShape master) {
        if (master == null) throw new IllegalArgumentException("Master vent shape cannot be null.");
        this.shapes = buildShapes(master);
    }

    @Override
    public VoxelShape getShape(BlockState state) {
        return shapes.get(state.getValue(VentBlock.AXIS)).get(state.getValue(VentBlock.CORNER));
    }

    private static Map<VentAxis, Map<VentCorner, VoxelShape>> buildShapes(VoxelShape master) {
        Map<VentAxis, Map<VentCorner, VoxelShape>> shapes = new EnumMap<>(VentAxis.class);
        for (VentAxis axis : VentAxis.values()) {
            VoxelShape aligned = alignAxis(master, axis);
            Map<VentCorner, VoxelShape> corners = new EnumMap<>(VentCorner.class);
            for (VentCorner corner : VentCorner.values()) {
                VoxelShape shape = rotateCorner(aligned, axis, corner);
                /* The Y-axis placement convention swaps these two quarter-orientations. */
                if (axis == VentAxis.Y && (corner == VentCorner.TOP_RIGHT || corner == VentCorner.BOTTOM_LEFT))
                    shape = VoxelShapeRotation.rotate(shape, Direction.Axis.Y, 2);
                corners.put(corner, shape);
            }
            shapes.put(axis, corners);
        }
        return shapes;
    }

    private static VoxelShape alignAxis(VoxelShape shape, VentAxis axis) {
        switch (axis) {
            case X: return VoxelShapeRotation.rotate(shape, Direction.Axis.Y, 1);
            case Y: return VoxelShapeRotation.rotate(shape, Direction.Axis.X, 3);
            case Z: return shape;
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    private static VoxelShape rotateCorner(VoxelShape shape, VentAxis axis, VentCorner corner) {
        int turns;
        switch (corner) {
            case BOTTOM_RIGHT: turns = 0; break;
            case TOP_RIGHT: turns = 1; break;
            case TOP_LEFT: turns = 2; break;
            case BOTTOM_LEFT: turns = 3; break;
            default: throw new IllegalArgumentException("Unsupported vent corner: " + corner);
        }
        if (axis == VentAxis.X) turns = (4 - turns) & 3;
        return VoxelShapeRotation.rotate(shape, axis.getMinecraftAxis(), turns);
    }
}
