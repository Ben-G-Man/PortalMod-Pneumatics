package io.github.bengman.pneumaticdiversityvents.shared;

import net.minecraft.util.IStringSerializable;

public enum VentImpellerVisualState implements IStringSerializable {
    ALWAYS_ON("always_on"),
    ON_OFF_OFF("on_off_off"),
    ON_OFF_ON("on_off_on"),
    REVERSE_OFF("reverse_off"),
    REVERSE_ON("reverse_on");

    private final String serializedName;

    VentImpellerVisualState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static VentImpellerVisualState from(boolean controlled, VentImpellerControlMode mode, boolean powered) {
        if (!controlled) return ALWAYS_ON;
        if (mode == VentImpellerControlMode.REVERSE_WHEN_POWERED) return powered ? REVERSE_ON : REVERSE_OFF;
        return powered ? ON_OFF_ON : ON_OFF_OFF;
    }
}
