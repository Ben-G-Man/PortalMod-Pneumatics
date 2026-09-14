/* Enforces the common upgrade lifecycle by executing one removal then one or more additions. */
package io.github.bengman.pneumaticdiversityvents.server.update;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class VentUpgrade extends VentUpdate {
    private final VentRemoval removal;
    private final List<VentAddition> additions;
    private final Collection<BlockPos> affectedBlocks;

    protected VentUpgrade(VentRemoval removal, VentAddition... additions) {
        super(removal.getWorld());
        if (additions == null || additions.length == 0) throw new IllegalArgumentException("Vent upgrade requires at least one addition.");
        for (VentAddition addition : additions)
            if (removal.getWorld() != addition.getWorld()) throw new IllegalArgumentException("Vent upgrade removal/additions must target the same world.");
        this.removal = removal;
        this.additions = Collections.unmodifiableList(Arrays.asList(additions.clone()));

        ArrayList<BlockPos> blocks = new ArrayList<>(removal.getAffectedBlocks());
        for (VentAddition addition : additions)
            for (BlockPos block : addition.getAffectedBlocks()) if (!blocks.contains(block)) blocks.add(block);
        this.affectedBlocks = Collections.unmodifiableList(blocks);
    }

    public VentRemoval getRemoval() {
        return removal;
    }

    public VentAddition getAddition() {
        return additions.get(0);
    }

    public List<VentAddition> getAdditions() {
        return additions;
    }

    @Override
    public UUID getTargetEdgeId() {
        return removal.getTargetEdgeId();
    }

    @Override
    public Collection<BlockPos> getAffectedBlocks() {
        return affectedBlocks;
    }

    @Override
    public final void execute() {
        removal.execute();
        for (VentAddition addition : additions) addition.execute();
    }
}
