package io.github.bengman.pneumaticdiversityvents.shared;

public enum VentImpellerControlMode {
    ON_OFF,
    REVERSE_WHEN_POWERED;

    public VentImpellerControlMode next() {
        return this == ON_OFF ? REVERSE_WHEN_POWERED : ON_OFF;
    }
}
