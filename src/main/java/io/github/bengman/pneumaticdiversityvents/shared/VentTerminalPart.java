package io.github.bengman.pneumaticdiversityvents.shared;

import net.minecraft.util.IStringSerializable;

/** Physical axial section of a 1.5-block-long terminal. */
public enum VentTerminalPart implements IStringSerializable {
    BODY("body"),
    TIP("tip");

    private final String name;

    VentTerminalPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
