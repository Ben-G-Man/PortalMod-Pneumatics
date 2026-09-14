/* Pure network endpoint; physical position is intentionally stored elsewhere. */
package io.github.bengman.pneumaticdiversityvents.server.network;

import java.util.UUID;

public final class VentConnection {
    private final UUID id;
    private VentEdge parent;
    private VentConnection peer;

    public VentConnection() {
        this(UUID.randomUUID());
    }

    public VentConnection(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public VentEdge getParent() {
        return parent;
    }

    void setParent(VentEdge parent) {
        this.parent = parent;
    }

    public VentConnection getPeer() {
        return peer;
    }

    void setPeer(VentConnection peer) {
        this.peer = peer;
    }

    public boolean isConnected() {
        return peer != null;
    }
}
