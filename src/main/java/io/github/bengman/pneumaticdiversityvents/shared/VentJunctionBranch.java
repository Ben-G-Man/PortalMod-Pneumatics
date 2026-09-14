package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.util.Direction;
import net.minecraft.util.IStringSerializable;

/** One of the four directions perpendicular to a junction's straight-through axis. */
public enum VentJunctionBranch implements IStringSerializable {
    TOP("top"), RIGHT("right"), BOTTOM("bottom"), LEFT("left");

    private final String name;
    VentJunctionBranch(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Direction resolve(VentAxis axis) {
        switch (axis) {
            case Z:
                switch (this) {
                    case TOP: return Direction.UP;
                    case RIGHT: return Direction.EAST;
                    case BOTTOM: return Direction.DOWN;
                    default: return Direction.WEST;
                }
            case X:
                switch (this) {
                    case TOP: return Direction.UP;
                    case RIGHT: return Direction.SOUTH;
                    case BOTTOM: return Direction.DOWN;
                    default: return Direction.NORTH;
                }
            case Y:
                switch (this) {
                    case TOP: return Direction.NORTH;
                    case RIGHT: return Direction.EAST;
                    case BOTTOM: return Direction.SOUTH;
                    default: return Direction.WEST;
                }
            default: throw new IllegalArgumentException("Unsupported junction axis: " + axis);
        }
    }

    public static VentJunctionBranch fromDirection(VentAxis axis, Direction direction) {
        for (VentJunctionBranch branch : values()) if (branch.resolve(axis) == direction) return branch;
        throw new IllegalArgumentException("Direction " + direction + " is not perpendicular to junction axis " + axis);
    }
}
