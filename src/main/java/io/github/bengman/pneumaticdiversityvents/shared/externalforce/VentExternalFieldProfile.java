/* Immutable gameplay profile describing one vent endpoint's external force field. */
package io.github.bengman.pneumaticdiversityvents.shared.externalforce;

public final class VentExternalFieldProfile {
    private final double range;
    private final double spreadDegrees;
    private final double baseAcceleration;
    private final double radialSteering;

    public VentExternalFieldProfile(double range, double spreadDegrees, double baseAcceleration, double radialSteering) {
        if (range <= 0.0D) throw new IllegalArgumentException("External vent field range must be positive.");
        if (spreadDegrees < 0.0D || spreadDegrees >= 89.0D) throw new IllegalArgumentException("External vent field spread must be in [0, 89).");
        if (baseAcceleration < 0.0D) throw new IllegalArgumentException("External vent field acceleration cannot be negative.");
        if (radialSteering < 0.0D || radialSteering > 1.0D) throw new IllegalArgumentException("External vent field steering must be in [0, 1].");
        this.range = range;
        this.spreadDegrees = spreadDegrees;
        this.baseAcceleration = baseAcceleration;
        this.radialSteering = radialSteering;
    }

    public double getRange() {
        return range;
    }

    public double getSpreadDegrees() {
        return spreadDegrees;
    }

    public double getBaseAcceleration() {
        return baseAcceleration;
    }

    public double getRadialSteering() {
        return radialSteering;
    }

    public double getSpreadRadians() {
        return Math.toRadians(spreadDegrees);
    }
}
