/* Stores one edge-local impeller source: base polarity plus derived PortalMod test-element control state. */
package io.github.bengman.pneumaticdiversityvents.server.network;

import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerControlMode;

public final class VentForceSource {
    private final int units;
    private boolean baseAToB;
    private VentImpellerControlMode controlMode;
    private boolean antlineControlled;
    private boolean powered;

    public VentForceSource(int units, boolean baseAToB) {
        this(units, baseAToB, VentImpellerControlMode.ON_OFF);
    }

    public VentForceSource(int units, boolean baseAToB, VentImpellerControlMode controlMode) {
        if (units <= 0) throw new IllegalArgumentException("Vent force units must be positive.");
        this.units = units;
        this.baseAToB = baseAToB;
        this.controlMode = controlMode == null ? VentImpellerControlMode.ON_OFF : controlMode;
    }

    public int getUnits() {
        return units;
    }

    public boolean isBaseAToB() {
        return baseAToB;
    }

    public boolean isAToB() {
        return baseAToB ^ (antlineControlled && powered && controlMode == VentImpellerControlMode.REVERSE_WHEN_POWERED);
    }

    void invert() {
        baseAToB = !baseAToB;
    }

    public VentImpellerControlMode getControlMode() {
        return controlMode;
    }

    void setControlMode(VentImpellerControlMode controlMode) {
        this.controlMode = controlMode;
    }

    public boolean isAntlineControlled() {
        return antlineControlled;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isEnabled() {
        if (!antlineControlled) return true;
        return controlMode == VentImpellerControlMode.REVERSE_WHEN_POWERED || powered;
    }

    boolean setAntlineState(boolean controlled, boolean powered) {
        boolean changed = antlineControlled != controlled || this.powered != (controlled && powered);
        antlineControlled = controlled;
        this.powered = controlled && powered;
        return changed;
    }
}
