/* Pure connected-component state including cached signed impeller contribution and derived blockage. */
package io.github.bengman.pneumaticdiversityvents.server.network;

import java.util.UUID;

public final class VentNetwork {
    private final UUID id;
    private int rawNetForce;
    private int activeForceSources;
    private boolean blocked;

    public VentNetwork() {
        this(UUID.randomUUID());
    }

    public VentNetwork(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    /* Positive force follows the manager's current traversal orientation; negative force opposes it. */
    public int getRawNetForce() {
        return rawNetForce;
    }

    public int getNetForce() {
        return blocked ? 0 : rawNetForce;
    }

    void setRawNetForce(int rawNetForce) {
        this.rawNetForce = rawNetForce;
    }

    /** Blockage is derived from the current world every server tick and is never persisted. */
    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    /* Counts enabled impellers that are actually connected to at least one other edge. */
    public int getActiveForceSources() {
        return activeForceSources;
    }

    void setActiveForceSources(int activeForceSources) {
        this.activeForceSources = activeForceSources;
    }

    public boolean hasActiveForceField() {
        return activeForceSources > 0;
    }
}
