/* Base polymorphic server update executed by the end-of-tick vent queue. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public abstract class VentUpdate {
    private final UUID updateId = UUID.randomUUID();
    protected final ServerWorld world;

    protected VentUpdate(ServerWorld world) {
        this.world = world;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public UUID getUpdateId() {
        return updateId;
    }

    public UUID getTargetEdgeId() {
        return null;
    }

    public Collection<BlockPos> getAffectedBlocks() {
        return Collections.emptyList();
    }

    public String getOrderKey() {
        UUID target = getTargetEdgeId();
        return (target == null ? updateId : target).toString();
    }
    public abstract void execute();
}
