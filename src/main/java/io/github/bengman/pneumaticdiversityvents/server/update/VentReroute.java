/* Replaces one existing long/bent edge and immediately appends the newly placed one-long segment. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentBuilder;

import net.minecraft.world.server.ServerWorld;

import java.util.UUID;

public final class VentReroute extends VentUpgrade {
    public VentReroute(ServerWorld world, UUID existingEdgeId, WorldVentBuilder replacement, WorldVentBuilder continuation) {
        super(new VentRemoval(world, existingEdgeId), new VentAddition(replacement), new VentAddition(continuation));
    }
}
