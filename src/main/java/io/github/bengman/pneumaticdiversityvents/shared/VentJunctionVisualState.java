package io.github.bengman.pneumaticdiversityvents.shared;

import net.minecraft.util.IStringSerializable;

/** Encodes both externally powered state and the route currently open inside the junction. */
public enum VentJunctionVisualState implements IStringSerializable {
    STRAIGHT_OFF("straight_off", false, false),
    TURN_OFF("turn_off", true, false),
    STRAIGHT_ON("straight_on", false, true),
    TURN_ON("turn_on", true, true);

    private final String name;
    private final boolean turnActive;
    private final boolean powered;

    VentJunctionVisualState(String name, boolean turnActive, boolean powered) {
        this.name = name; this.turnActive = turnActive; this.powered = powered;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean isTurnActive() {
        return turnActive;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isDefaultTurn() {
        return turnActive ^ powered;
    }

    /** Changes external power while preserving which route is configured as the unpowered default. */
    public VentJunctionVisualState withPowered(boolean powered) {
        return of(isDefaultTurn() ^ powered, powered);
    }
    /** Crouch-wrench toggles the configured default; therefore the currently active route also flips. */
    public VentJunctionVisualState toggleDefaultRoute() {
        return of(!turnActive, powered);
    }

    public static VentJunctionVisualState of(boolean turnActive, boolean powered) {
        if (powered) return turnActive ? TURN_ON : STRAIGHT_ON;
        return turnActive ? TURN_OFF : STRAIGHT_OFF;
    }
}
