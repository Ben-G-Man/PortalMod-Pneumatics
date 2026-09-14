/* Singleton queue that reconciles, orders, and exposes executable vent updates. */
package io.github.bengman.pneumaticdiversityvents.server;

import io.github.bengman.pneumaticdiversityvents.server.update.VentAddition;
import io.github.bengman.pneumaticdiversityvents.server.update.VentRemoval;
import io.github.bengman.pneumaticdiversityvents.server.update.VentUpdate;
import io.github.bengman.pneumaticdiversityvents.server.update.VentUpgrade;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public final class VentUpdateQueue {
    private static final VentUpdateQueue INSTANCE = new VentUpdateQueue();
    private final NavigableMap<String, VentRemoval> removals = new TreeMap<>();
    private final NavigableMap<String, VentAddition> additions = new TreeMap<>();
    private final NavigableMap<String, VentUpgrade> upgrades = new TreeMap<>();
    private final NavigableMap<String, VentUpdate> other = new TreeMap<>();

    private VentUpdateQueue() {
    }

    public static VentUpdateQueue get() {
        return INSTANCE;
    }

    public synchronized void add(VentUpdate update) {
        if (update instanceof VentUpgrade) upgrades.put(update.getOrderKey(), (VentUpgrade) update);
        else if (update instanceof VentRemoval) {
            VentRemoval removal = (VentRemoval) update;
            if (!cancelInverse(removal, additions)) {
                VentRemoval existing = removals.get(update.getOrderKey());
                if (existing == null || (!existing.shouldDropItems() && removal.shouldDropItems())) removals.put(update.getOrderKey(), removal);
            }
        } else if (update instanceof VentAddition) {
            VentAddition addition = (VentAddition) update;
            if (!cancelInverse(addition, removals)) additions.put(update.getOrderKey(), addition);
        } else other.put(update.getOrderKey(), update);
    }

    public synchronized VentUpdate pop() {
        VentUpdate update = poll(removals);
        if (update != null) return update;
        update = poll(additions);
        if (update != null) return update;
        update = poll(upgrades);
        return update != null ? update : poll(other);
    }

    public synchronized boolean isEmpty() {
        return removals.isEmpty() && additions.isEmpty() && upgrades.isEmpty() && other.isEmpty();
    }

    public synchronized boolean isEdgePending(ServerWorld world, java.util.UUID edgeId) {
        for (VentUpdate update : removals.values()) if (sameWorld(update.getWorld(), world) && edgeId.equals(update.getTargetEdgeId())) return true;
        for (VentUpdate update : additions.values()) if (sameWorld(update.getWorld(), world) && edgeId.equals(update.getTargetEdgeId())) return true;
        for (VentUpdate update : upgrades.values()) if (sameWorld(update.getWorld(), world) && edgeId.equals(update.getTargetEdgeId())) return true;
        for (VentUpdate update : other.values()) if (sameWorld(update.getWorld(), world) && edgeId.equals(update.getTargetEdgeId())) return true;
        return false;
    }

    public synchronized boolean isBlockPending(ServerWorld world, BlockPos position) {
        for (VentAddition addition : additions.values()) if (sameWorld(addition.getWorld(), world) && addition.getAffectedBlocks().contains(position)) return true;
        for (VentUpgrade upgrade : upgrades.values()) if (sameWorld(upgrade.getWorld(), world) && upgrade.getAffectedBlocks().contains(position)) return true;
        return false;
    }

    private static <T extends VentUpdate> T poll(NavigableMap<String, T> map) {
        Map.Entry<String, T> entry = map.pollFirstEntry();
        return entry == null ? null : entry.getValue();
    }

    private static boolean cancelInverse(VentUpdate incoming, NavigableMap<String, ? extends VentUpdate> opposite) {
        String match = null;
        for (Map.Entry<String, ? extends VentUpdate> entry : opposite.entrySet()) {
            if (sameTarget(incoming, entry.getValue())) {
                match = entry.getKey();
                break;
            }
        }
        if (match == null) return false;
        opposite.remove(match);
        return true;
    }

    private static boolean sameTarget(VentUpdate a, VentUpdate b) {
        if (!sameWorld(a.getWorld(), b.getWorld())) return false;
        if (a.getTargetEdgeId() != null && a.getTargetEdgeId().equals(b.getTargetEdgeId())) return true;
        Collection<BlockPos> aBlocks = a.getAffectedBlocks(), bBlocks = b.getAffectedBlocks();
        if (aBlocks.isEmpty() || bBlocks.isEmpty()) return false;
        Set<BlockPos> aSet = new HashSet<>(aBlocks), bSet = new HashSet<>(bBlocks);
        return aSet.equals(bSet);
    }

    private static boolean sameWorld(ServerWorld a, ServerWorld b) {
        return a == b;
    }
}
