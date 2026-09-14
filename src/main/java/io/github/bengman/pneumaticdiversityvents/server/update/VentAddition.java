/* Adds one preplanned abstract edge and builds/registers its physical geometry. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentManager;

import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.UUID;

public class VentAddition extends VentUpdate {
    private final WorldVentBuilder builder;

    public VentAddition(WorldVentBuilder builder) {
        super(builder.getWorld());
        this.builder = builder;
    }

    public WorldVentBuilder getBuilder() {
        return builder;
    }

    @Override
    public UUID getTargetEdgeId() {
        return builder.getEdge().getId();
    }

    @Override
    public Collection<BlockPos> getAffectedBlocks() {
        return builder.getPlannedBlocks();
    }

    @Override
    public void execute() {
        WorldVentManager.add(builder);
    }
}
