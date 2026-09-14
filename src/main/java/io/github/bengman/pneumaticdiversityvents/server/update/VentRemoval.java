/* Removes one abstract edge and every physical block registered to it. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentManager;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentDropUtil;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class VentRemoval extends VentUpdate {
    private final UUID edgeId;
    private final Collection<BlockPos> affectedBlocks;
    private final boolean dropItems;

    public VentRemoval(ServerWorld world, UUID edgeId) {
        this(world, edgeId, false);
    }

    public VentRemoval(ServerWorld world, UUID edgeId, boolean dropItems) {
        super(world);
        this.edgeId = edgeId;
        this.affectedBlocks = Collections.unmodifiableList(new ArrayList<>(VentSpatialRegistry.get(world).getBlocks(edgeId)));
        this.dropItems = dropItems;
    }

    public boolean shouldDropItems() {
        return dropItems;
    }

    @Override
    public UUID getTargetEdgeId() {
        return edgeId;
    }

    @Override
    public Collection<BlockPos> getAffectedBlocks() {
        return affectedBlocks;
    }

    @Override
    public void execute() {
        if (dropItems) VentDropUtil.dropEdge(world, edgeId);
        WorldVentManager.remove(world, edgeId);
    }
}
