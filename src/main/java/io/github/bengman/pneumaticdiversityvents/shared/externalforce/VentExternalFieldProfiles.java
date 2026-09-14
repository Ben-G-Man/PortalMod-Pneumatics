/* Holds endpoint force profiles so ordinary mouths and focused terminals can differ cleanly. */
package io.github.bengman.pneumaticdiversityvents.shared.externalforce;

public final class VentExternalFieldProfiles {
    /* Ordinary mouths are deliberately broad and shorter-ranged so their force cloud is readable and the terminal has a clear niche. */
    public static final VentExternalFieldProfile STANDARD_INTAKE = new VentExternalFieldProfile(5.5D, 25.0D, 0.115D, 0.58D);
    public static final VentExternalFieldProfile STANDARD_EXHAUST = new VentExternalFieldProfile(5.0D, 15.0D, 0.100D, 0.30D);

    /* Diversity Concentration Terminal: focused, longer-ranged, and stronger at the same network force. */
    public static final VentExternalFieldProfile TERMINAL_INTAKE = new VentExternalFieldProfile(10.0D, 8.0D, 0.185D, 0.68D);
    public static final VentExternalFieldProfile TERMINAL_EXHAUST = new VentExternalFieldProfile(8.0D, 6.0D, 0.165D, 0.22D);

    private VentExternalFieldProfiles() {
    }
}
