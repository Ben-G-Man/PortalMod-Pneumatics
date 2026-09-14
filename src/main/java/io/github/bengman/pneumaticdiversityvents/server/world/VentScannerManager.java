package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.integration.PortalAntlineBridge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.transport.VentPath;
import io.github.bengman.pneumaticdiversityvents.shared.VentScannerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentScannerVisualState;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.cube.Cube;
import net.portalmod.core.init.SoundInit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class VentScannerManager {
    public static final int PULSE_TICKS = 20;
    private static final double DETECTION_RADIUS = 0.85D;
    private static final Map<ServerWorld, WorldState> WORLDS = new WeakHashMap<>();

    private VentScannerManager() {
    }

    public static void observeTraversal(ServerWorld world, VentSpatialRegistry registry, VentEdge edge, Entity entity) {
        if (edge == null || entity == null) return;
        BlockState scannerState = scannerState(world, registry.getBlocks(edge.getId()));
        if (scannerState == null || !matches(scannerState.getValue(VentScannerBlock.VISUAL).getFilter(), entity)) return;
        WORLDS.computeIfAbsent(world, ignored -> new WorldState()).markTraversal(edge.getId(), entity.getUUID());
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        WORLDS.computeIfAbsent(world, ignored -> new WorldState()).tick(world, registry);
    }

    public static void onWorldUnload(ServerWorld world) {
        WORLDS.remove(world);
    }

    private static boolean matches(VentScannerVisualState.Filter filter, Entity entity) {
        return filter == VentScannerVisualState.Filter.PLAYER ? entity instanceof ServerPlayerEntity : entity instanceof Cube;
    }

    private static BlockState scannerState(ServerWorld world, List<BlockPos> blocks) {
        for (BlockPos block : blocks) {
            BlockState state = world.getBlockState(block);
            if (state.getBlock() instanceof VentScannerBlock) return state;
        }
        return null;
    }

    private static Set<UUID> physicalOccupants(ServerWorld world, VentSpatialRegistry registry, VentEdge edge, VentScannerVisualState.Filter filter) {
        VentPath path;
        try {
            path = VentPath.of(registry, edge);
        } catch (RuntimeException ignored) {
            return Collections.emptySet();
        }

        AxisAlignedBB bounds = path.getBounds();
        Collection<Entity> entities = world.getEntities((Entity) null, bounds, entity -> entity != null && !entity.removed && matches(filter, entity));
        Set<UUID> result = new HashSet<>();
        for (Entity entity : entities) {
            Vector3d center = entityCenter(entity);
            VentPath.Projection projection = path.project(center);
            if (projection.getDistanceSquared() <= DETECTION_RADIUS * DETECTION_RADIUS) result.add(entity.getUUID());
        }
        return result;
    }

    private static Vector3d entityCenter(Entity entity) {
        AxisAlignedBB box = entity.getBoundingBox();
        return new Vector3d((box.minX + box.maxX) / 2.0D, (box.minY + box.maxY) / 2.0D, (box.minZ + box.maxZ) / 2.0D);
    }

    private static void setEdgeActive(ServerWorld world, UUID edgeId, List<BlockPos> blocks, boolean active) {
        BlockPos soundPos = null;
        for (BlockPos block : blocks) {
            BlockState state = world.getBlockState(block);
            if (!(state.getBlock() instanceof VentScannerBlock) || !state.hasProperty(VentScannerBlock.VISUAL)) continue;

            VentScannerVisualState current = state.getValue(VentScannerBlock.VISUAL);
            VentScannerVisualState next = current.withActive(active);
            if (next == current) continue;

            world.setBlock(block, state.setValue(VentScannerBlock.VISUAL, next), 3);
            if (soundPos == null) soundPos = block;
        }

        PortalAntlineBridge.setActive(world, edgeId, blocks, active);
        if (soundPos != null) {
            world.playSound(null, soundPos,
                    (active ? SoundInit.ANTLINE_INDICATOR_ACTIVATE : SoundInit.ANTLINE_INDICATOR_DEACTIVATE).get(),
                    SoundCategory.BLOCKS, 0.55F, 1.0F);
        }
    }

    private static final class WorldState {
        private final Map<UUID, Pulse> pulses = new HashMap<>();
        private final Map<UUID, Set<UUID>> previousOccupants = new HashMap<>();
        private final Map<UUID, Set<UUID>> traversedThisTick = new HashMap<>();

        private void markTraversal(UUID edgeId, UUID entityId) {
            traversedThisTick.computeIfAbsent(edgeId, ignored -> new HashSet<>()).add(entityId);
        }

        private void trigger(ServerWorld world, VentSpatialRegistry registry, UUID edgeId) {
            long until = world.getGameTime() + PULSE_TICKS;
            Pulse pulse = pulses.get(edgeId);
            if (pulse == null) {
                List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edgeId));
                pulse = new Pulse(until, blocks);
                pulses.put(edgeId, pulse);
                setEdgeActive(world, edgeId, blocks, true);
            } else {
                pulse.until = Math.max(pulse.until, until);
            }
        }

        private void tick(ServerWorld world, VentSpatialRegistry registry) {
            Set<UUID> liveScanners = new HashSet<>();
            for (VentEdge edge : registry.getNetworkManager().getEdges()) {
                List<BlockPos> blocks = registry.getBlocks(edge.getId());
                BlockState scannerState = scannerState(world, blocks);
                if (scannerState == null) continue;
                UUID edgeId = edge.getId();
                liveScanners.add(edgeId);

                VentScannerVisualState.Filter filter = scannerState.getValue(VentScannerBlock.VISUAL).getFilter();
                Set<UUID> physical = physicalOccupants(world, registry, edge, filter);
                Set<UUID> observed = new HashSet<>(physical);
                observed.addAll(traversedThisTick.getOrDefault(edgeId, Collections.emptySet()));
                Set<UUID> previous = previousOccupants.getOrDefault(edgeId, Collections.emptySet());
                for (UUID entityId : observed) {
                    if (!previous.contains(entityId)) {
                        trigger(world, registry, edgeId);
                        break;
                    }
                }

                if (!pulses.containsKey(edgeId) && scannerState.getValue(VentScannerBlock.VISUAL).isActive()) {
                    setEdgeActive(world, edgeId, blocks, false);
                }
                previousOccupants.put(edgeId, physical);
            }

            previousOccupants.keySet().removeIf(id -> !liveScanners.contains(id));
            traversedThisTick.clear();

            long now = world.getGameTime();
            Iterator<Map.Entry<UUID, Pulse>> iterator = pulses.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Pulse> entry = iterator.next();
                UUID edgeId = entry.getKey();
                Pulse pulse = entry.getValue();
                boolean edgeStillExists = registry.getEdge(edgeId) != null;
                if (edgeStillExists && now < pulse.until) continue;
                List<BlockPos> blocks = edgeStillExists ? new ArrayList<>(registry.getBlocks(edgeId)) : pulse.blocks;
                setEdgeActive(world, edgeId, blocks, false);
                iterator.remove();
            }
        }
    }

    private static final class Pulse {
        private long until;
        private final List<BlockPos> blocks;

        private Pulse(long until, List<BlockPos> blocks) {
            this.until = until;
            this.blocks = blocks;
        }
    }
}
