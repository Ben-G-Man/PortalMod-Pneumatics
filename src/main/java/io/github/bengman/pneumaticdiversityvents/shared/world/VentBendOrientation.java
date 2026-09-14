/* Names the twelve possible unordered pairs of perpendicular bend connection directions. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.util.Direction;
import net.minecraft.util.IStringSerializable;

public enum VentBendOrientation implements IStringSerializable {
    UP_SOUTH("up_south", Direction.UP, Direction.SOUTH),
    UP_NORTH("up_north", Direction.UP, Direction.NORTH),
    UP_EAST("up_east", Direction.UP, Direction.EAST),
    UP_WEST("up_west", Direction.UP, Direction.WEST),
    DOWN_SOUTH("down_south", Direction.DOWN, Direction.SOUTH),
    DOWN_NORTH("down_north", Direction.DOWN, Direction.NORTH),
    DOWN_EAST("down_east", Direction.DOWN, Direction.EAST),
    DOWN_WEST("down_west", Direction.DOWN, Direction.WEST),
    EAST_SOUTH("east_south", Direction.EAST, Direction.SOUTH),
    EAST_NORTH("east_north", Direction.EAST, Direction.NORTH),
    WEST_SOUTH("west_south", Direction.WEST, Direction.SOUTH),
    WEST_NORTH("west_north", Direction.WEST, Direction.NORTH);

    private final String name;
    private final Direction first;
    private final Direction second;

    VentBendOrientation(String name, Direction first, Direction second) {
        this.name = name;
        this.first = first;
        this.second = second;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Direction getFirst() {
        return first;
    }

    public Direction getSecond() {
        return second;
    }

    public Direction getCrossDirection() {
        return cross(first, second);
    }

    public static VentBendOrientation of(Direction a, Direction b) {
        if (a == null || b == null || a.getAxis() == b.getAxis())
            throw new IllegalArgumentException("Bend directions must be non-null and perpendicular.");

        for (VentBendOrientation orientation : values()) {
            if ((orientation.first == a && orientation.second == b) ||
                    (orientation.first == b && orientation.second == a)) return orientation;
        }
        throw new IllegalArgumentException("Unsupported bend directions: " + a + " / " + b);
    }

    private static Direction cross(Direction a, Direction b) {
        int ax = a.getStepX(), ay = a.getStepY(), az = a.getStepZ();
        int bx = b.getStepX(), by = b.getStepY(), bz = b.getStepZ();
        int x = ay * bz - az * by;
        int y = az * bx - ax * bz;
        int z = ax * by - ay * bx;
        for (Direction direction : Direction.values())
            if (direction.getStepX() == x && direction.getStepY() == y && direction.getStepZ() == z) return direction;
        throw new IllegalArgumentException("Directions do not produce a cardinal cross product: " + a + " / " + b);
    }
}
