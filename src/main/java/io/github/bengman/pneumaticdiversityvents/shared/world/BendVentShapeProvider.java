/* Caches inner/outer bend collision shapes for every bend orientation and cross-side variant. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTurnBlock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

import java.util.EnumMap;
import java.util.Map;

public final class BendVentShapeProvider implements VentShapeProvider {
    /* Canonical orientation: connections UP + SOUTH, cross-side EAST. */
    private static final VoxelShape INNER_MASTER = VoxelShapes.or(
            Block.box(12, 0, 0, 14, 16, 16),
            Block.box(0, 12, 12, 12, 14, 16),
            Block.box(0, 12, 12, 12, 16, 14)).optimize();

    private static final VoxelShape OUTER_MASTER = VoxelShapes.or(
            Block.box(12, 2, 2, 14, 16, 16),
            Block.box(0, 2, 4, 12, 4, 16),
            Block.box(0, 4, 2, 12, 16, 4)).optimize();

    public static final BendVentShapeProvider INSTANCE = new BendVentShapeProvider();

    private final Map<VentBendPiece, Map<VentBendOrientation, VoxelShape[]>> shapes = new EnumMap<>(VentBendPiece.class);

    private BendVentShapeProvider() {
        shapes.put(VentBendPiece.INNER, build(INNER_MASTER));
        shapes.put(VentBendPiece.OUTER, build(OUTER_MASTER));
    }

    @Override
    public VoxelShape getShape(BlockState state) {
        VentBendPiece piece = state.getValue(VentTurnBlock.PIECE);
        VentBendOrientation orientation = state.getValue(VentTurnBlock.ORIENTATION);
        return shapes.get(piece).get(orientation)[state.getValue(VentBlock.MIRRORED) ? 1 : 0];
    }

    private static Map<VentBendOrientation, VoxelShape[]> build(VoxelShape master) {
        Map<VentBendOrientation, VoxelShape[]> result = new EnumMap<>(VentBendOrientation.class);
        for (VentBendOrientation orientation : VentBendOrientation.values()) {
            VoxelShape[] pair = new VoxelShape[2];
            pair[0] = orient(master, orientation.getFirst(), orientation.getSecond(), orientation.getCrossDirection());
            pair[1] = orient(master, orientation.getSecond(), orientation.getFirst(), orientation.getCrossDirection().getOpposite());
            result.put(orientation, pair);
        }
        return result;
    }

    private static VoxelShape orient(VoxelShape master, Direction targetY, Direction targetZ, Direction targetX) {
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
            Direction currentX = rotate(Direction.EAST, x, y, z);
            Direction currentY = rotate(Direction.UP, x, y, z);
            Direction currentZ = rotate(Direction.SOUTH, x, y, z);
            if (currentX != targetX || currentY != targetY || currentZ != targetZ) continue;

            VoxelShape shape = VoxelShapeRotation.rotate(master, Direction.Axis.X, x);
            shape = VoxelShapeRotation.rotate(shape, Direction.Axis.Y, y);
            return VoxelShapeRotation.rotate(shape, Direction.Axis.Z, z);
        }
        throw new IllegalStateException("No cube rotation found for bend orientation.");
    }

    private static Direction rotate(Direction direction, int x, int y, int z) {
        direction = VoxelShapeRotation.rotate(direction, Direction.Axis.X, x);
        direction = VoxelShapeRotation.rotate(direction, Direction.Axis.Y, y);
        return VoxelShapeRotation.rotate(direction, Direction.Axis.Z, z);
    }
}
