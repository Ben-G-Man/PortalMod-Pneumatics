/* Shared blockstate corner identifying one quarter of a vent cross-section. */
package io.github.bengman.pneumaticdiversityvents.shared.world;

import net.minecraft.util.IStringSerializable;

public enum VentCorner implements IStringSerializable {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;
    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
