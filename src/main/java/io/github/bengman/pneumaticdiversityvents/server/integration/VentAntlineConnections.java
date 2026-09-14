package io.github.bengman.pneumaticdiversityvents.server.integration;

import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;

import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared candidate shell used by every vent component that exchanges antline power. */
public final class VentAntlineConnections {
    private VentAntlineConnections() {
    }


    /**
     * PortalMod's AntlineConnector can expose one mounting plane per physical block. The vent's four
     * corner blocks therefore expose one deterministic anchor each, covering all four radial planes
     * around the tube. Logical indicator discovery remains the full one-block shell.
     */
    public static Direction getHorsedOn(BlockState state) {
        VentAxis axis = state.getValue(VentBlock.AXIS);
        VentCorner corner = state.getValue(VentBlock.CORNER);
        switch (axis) {
            case Z:
                switch (corner) {
                    case BOTTOM_LEFT: return Direction.DOWN;
                    case BOTTOM_RIGHT: return Direction.EAST;
                    case TOP_RIGHT: return Direction.UP;
                    case TOP_LEFT: return Direction.WEST;
                    default: throw new IllegalArgumentException("Unsupported vent corner: " + corner);
                }
            case X:
                switch (corner) {
                    case BOTTOM_LEFT: return Direction.DOWN;
                    case BOTTOM_RIGHT: return Direction.SOUTH;
                    case TOP_RIGHT: return Direction.UP;
                    case TOP_LEFT: return Direction.NORTH;
                    default: throw new IllegalArgumentException("Unsupported vent corner: " + corner);
                }
            case Y:
                switch (corner) {
                    case TOP_LEFT: return Direction.NORTH;
                    case TOP_RIGHT: return Direction.EAST;
                    case BOTTOM_RIGHT: return Direction.SOUTH;
                    case BOTTOM_LEFT: return Direction.WEST;
                    default: throw new IllegalArgumentException("Unsupported vent corner: " + corner);
                }
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    /** Matches PortalMod's AntlineDevice convention: connections run within the mounting plane. */
    public static boolean connectsInDirection(Direction direction, BlockState state) {
        Direction horsedOn = getHorsedOn(state);
        return direction != null && direction.getAxis() != horsedOn.getAxis();
    }

    public static List<BlockPos> candidatePositions(Collection<BlockPos> componentBlocks) {
        if (componentBlocks == null || componentBlocks.isEmpty()) return Collections.emptyList();
        Set<BlockPos> occupied = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos block : componentBlocks) {
            BlockPos immutable = block.immutable();
            occupied.add(immutable);
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (int x = minX - 1; x <= maxX + 1; x++) for (int y = minY - 1; y <= maxY + 1; y++) for (int z = minZ - 1; z <= maxZ + 1; z++) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!occupied.contains(candidate)) candidates.add(candidate);
        }
        return Collections.unmodifiableList(new ArrayList<>(candidates));
    }
}
