/* Identifies whether a bend corner block belongs to the inside or outside of the elbow. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.util.IStringSerializable;

public enum VentBendPiece implements IStringSerializable {
    INNER("inner"),
    OUTER("outer");

    private final String name;

    VentBendPiece(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
