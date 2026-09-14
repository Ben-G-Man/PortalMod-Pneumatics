/* Shared blockstate axis used by vent geometry and world builders. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.util.Direction;
import net.minecraft.util.IStringSerializable;

public enum VentAxis implements IStringSerializable {
    X(Direction.Axis.X), Y(Direction.Axis.Y), Z(Direction.Axis.Z);

    private final Direction.Axis minecraftAxis;
    VentAxis(Direction.Axis minecraftAxis) {
        this.minecraftAxis = minecraftAxis;
    }

    public Direction.Axis getMinecraftAxis() {
        return minecraftAxis;
    }

    public static VentAxis fromDirection(Direction direction) {
        return fromAxis(direction.getAxis());
    }

    public static VentAxis fromAxis(Direction.Axis axis) {
        switch (axis) {
            case X: return X;
            case Y: return Y;
            case Z: return Z;
            default: throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
