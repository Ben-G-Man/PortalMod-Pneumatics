/* Pure abstract vent edge joining exactly two network connections. */
package io.github.bengman.pneumaticdiversityvents.server.network;

import java.util.UUID;

public final class VentEdge {
    private final UUID id;
    private final VentConnection a;
    private final VentConnection b;
    private boolean aToB = true;
    private VentNetwork network;
    private VentForceSource forceSource;

    public VentEdge() {
        this(UUID.randomUUID(), new VentConnection(), new VentConnection());
    }

    public VentEdge(UUID id, VentConnection a, VentConnection b) {
        this.id = id;
        this.a = a;
        this.b = b;
        a.setParent(this);
        b.setParent(this);
    }

    public UUID getId() {
        return id;
    }

    public VentConnection getA() {
        return a;
    }

    public VentConnection getB() {
        return b;
    }

    public VentConnection[] getConnections() { return new VentConnection[] {a, b}; }

    public boolean isAToB() {
        return aToB;
    }

    public void setAToB(boolean aToB) {
        this.aToB = aToB;
    }

    public VentConnection getForwardConnection() {
        return aToB ? a : b;
    }

    public VentConnection getBackwardConnection() {
        return aToB ? b : a;
    }

    public VentConnection getTraversalEntry() {
        return aToB ? a : b;
    }

    public VentConnection getTraversalExit() {
        return aToB ? b : a;
    }

    public VentNetwork getNetwork() {
        return network;
    }

    void setNetwork(VentNetwork network) {
        this.network = network;
    }

    public VentForceSource getForceSource() {
        return forceSource;
    }

    public void setForceSource(VentForceSource forceSource) {
        this.forceSource = forceSource;
    }

    public VentConnection getOther(VentConnection connection) {
        if (connection == a) return b;
        if (connection == b) return a;
        throw new IllegalArgumentException("Connection does not belong to VentEdge " + id);
    }
}
