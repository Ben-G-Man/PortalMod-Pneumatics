package io.github.bengman.pneumaticdiversityvents.shared;

import net.minecraft.util.IStringSerializable;

public enum VentScannerVisualState implements IStringSerializable {
    CUBES_OFF("cubes_off", Filter.CUBES, false),
    CUBES_ON("cubes_on", Filter.CUBES, true),
    PLAYER_OFF("player_off", Filter.PLAYER, false),
    PLAYER_ON("player_on", Filter.PLAYER, true);

    public enum Filter { CUBES, PLAYER }

    private final String name;
    private final Filter filter;
    private final boolean active;

    VentScannerVisualState(String name, Filter filter, boolean active) {
        this.name = name;
        this.filter = filter;
        this.active = active;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Filter getFilter() {
        return filter;
    }

    public boolean isActive() {
        return active;
    }

    public VentScannerVisualState withActive(boolean active) {
        return filter == Filter.CUBES ? (active ? CUBES_ON : CUBES_OFF) : (active ? PLAYER_ON : PLAYER_OFF);
    }

    public VentScannerVisualState nextFilter() {
        return filter == Filter.CUBES ? (active ? PLAYER_ON : PLAYER_OFF) : (active ? CUBES_ON : CUBES_OFF);
    }
}
