/* Replaces one short straight vent edge with its two-block-long equivalent. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import io.github.bengman.pneumaticdiversityvents.server.world.LongVentBuilder;

import net.minecraft.world.server.ServerWorld;

import java.util.UUID;

public final class VentExtension extends VentUpgrade {
    public VentExtension(ServerWorld world, UUID existingEdgeId, LongVentBuilder replacement) {
        super(new VentRemoval(world, existingEdgeId), new VentAddition(replacement));
    }
}
