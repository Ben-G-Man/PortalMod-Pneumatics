/* Minimal PortalMod cube-dropper integration for downward vent endpoints. */
package io.github.bengman.pneumaticdiversityvents.server.integration;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.network.VentConnection;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentConnection;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.portalmod.common.items.WrenchItem;
import net.portalmod.common.sorted.cubedropper.CubeDropperBlock;
import net.portalmod.common.sorted.cubedropper.CubeDropperTileEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PortalCubeDropperBridge {
    private static final int EMPTY_CLOSE_DELAY_TICKS = 10;
    private static final double FLOOR_HEIGHT = 3.0D / 16.0D;
    private static final double CEILING_HEIGHT = 15.0D / 16.0D;
    private static final double CHAMBER_MIN_OFFSET = 0.5D;
    private static final double CHAMBER_MAX_OFFSET = 1.5D;
    private static final double HANDOFF_FLOOR_EPSILON = 0.01D;
    private static final Map<ServerWorld, WorldState> WORLDS = new WeakHashMap<>();

    private PortalCubeDropperBridge() {
    }

    /** Returns the PortalMod main-block position when this open endpoint is a vertically aligned dropper outlet. */
    public static BlockPos findConnectedDropper(ServerWorld world, VentSpatialRegistry registry, VentEdge edge, VentConnection connection) {
        if (world == null || registry == null || edge == null || connection == null || connection.getPeer() != null) return null;
        WorldVentConnection worldConnection = registry.getWorldConnection(connection.getId());
        if (worldConnection == null || worldConnection.getAxis() != VentAxis.Y) return null;

        List<BlockPos> blocks = registry.getBlocks(edge.getId());
        if (blocks.isEmpty()) return null;
        int minY = Integer.MAX_VALUE;
        for (BlockPos block : blocks) minY = Math.min(minY, block.getY());
        // Only a DOWN-facing endpoint can terminate into a dropper directly below the tube.
        if (worldConnection.getCoordinate().getY() != minY * 2) return null;

        List<BlockPos> face = new ArrayList<>(4);
        for (BlockPos block : blocks) if (block.getY() == minY) face.add(block);
        if (face.size() != 4) return null;

        BlockPos main = null;
        for (BlockPos faceBlock : face) {
            BlockPos dropperPos = faceBlock.below();
            BlockState state = world.getBlockState(dropperPos);
            if (!(state.getBlock() instanceof CubeDropperBlock)) return null;
            CubeDropperBlock dropper = (CubeDropperBlock) state.getBlock();
            BlockPos candidate = dropper.getMainPosition(state, dropperPos);
            if (main == null) main = new BlockPos(candidate.getX(), candidate.getY(), candidate.getZ());
            else if (!main.equals(candidate)) return null;
        }
        return getDropper(world, main) == null ? null : main;
    }

    public static boolean isConnectedEndpoint(ServerWorld world, VentSpatialRegistry registry, VentEdge edge, VentConnection connection) {
        return findConnectedDropper(world, registry, edge, connection) != null;
    }

    public static boolean isConnectedEndpoint(ServerWorld world, VentSpatialRegistry registry, UUID edgeId, UUID connectionId) {
        VentEdge edge = registry.getEdge(edgeId);
        if (edge == null) return false;
        VentConnection connection = edge.getA().getId().equals(connectionId) ? edge.getA()
                : edge.getB().getId().equals(connectionId) ? edge.getB() : null;
        return connection != null && isConnectedEndpoint(world, registry, edge, connection);
    }

    /** Takes over every currently connected dropper, updates its door, and releases stale runtime state. */
    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        WorldState state = WORLDS.computeIfAbsent(world, ignored -> new WorldState());
        Map<BlockPos, DropperRef> connected = findConnectedDroppers(world, registry);

        for (Map.Entry<BlockPos, DropperRef> entry : connected.entrySet()) {
            DropperState dropperState = state.droppers.computeIfAbsent(entry.getKey(), ignored -> new DropperState());
            maintainTakeover(entry.getValue());
            updateDoor(world, entry.getValue(), dropperState);
        }

        for (BlockPos previous : new HashSet<>(state.droppers.keySet())) {
            if (connected.containsKey(previous)) continue;
            DropperRef ref = getDropper(world, previous);
            if (ref != null) setOpen(ref, false);
            state.droppers.remove(previous);
        }

        restorePlayersOutsideManagedChambers(world, state, connected.keySet());
        if (state.droppers.isEmpty() && state.forcedPlayerPoses.isEmpty()) WORLDS.remove(world);
    }

    /** Releases one transported entity into the dropper chamber. */
    public static boolean handoff(ServerWorld world, BlockPos mainPos, Entity entity) {
        DropperRef ref = getDropper(world, mainPos);
        if (ref == null || entity == null || entity.removed) return false;

        WorldState worldState = WORLDS.computeIfAbsent(world, ignored -> new WorldState());
        DropperState dropperState = worldState.droppers.computeIfAbsent(ref.mainPos, ignored -> new DropperState());
        maintainTakeover(ref);

        if (entity instanceof ServerPlayerEntity) forceCrouching(worldState, (ServerPlayerEntity) entity);
        double x = ref.mainPos.getX() + 1.0D;
        double y = ref.mainPos.getY() - 1.0D + FLOOR_HEIGHT + HANDOFF_FLOOR_EPSILON;
        double z = ref.mainPos.getZ() + 1.0D;
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            player.connection.teleport(x, y, z, player.yRot, player.xRot);
        } else entity.setPos(x, y, z);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;

        // Occupancy is deliberately evaluated independently from transport handoff.
        updateDoor(world, ref, dropperState);
        return true;
    }

    /** Blocks PortalMod spawn-egg/wrench configuration while a vent owns the dropper. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getWorld() instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.getWorld();
        BlockState state = world.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof CubeDropperBlock)) return;

        CubeDropperBlock block = (CubeDropperBlock) state.getBlock();
        BlockPos main = block.getMainPosition(state, event.getPos());
        if (!isManagedDropper(world, main)) return;
        if (!(event.getItemStack().getItem() instanceof SpawnEggItem) && !(event.getItemStack().getItem() instanceof WrenchItem)) return;

        event.setCanceled(true);
        event.setCancellationResult(ActionResultType.SUCCESS);
    }

    public static void onEntityLeaving(ServerWorld world, Entity entity) {
        if (!(entity instanceof ServerPlayerEntity)) return;
        WorldState state = WORLDS.get(world);
        if (state == null || !state.forcedPlayerPoses.containsKey(entity.getUUID())) return;
        Pose previous = state.forcedPlayerPoses.remove(entity.getUUID());
        restorePlayerPose((ServerPlayerEntity) entity, previous);
        if (state.droppers.isEmpty() && state.forcedPlayerPoses.isEmpty()) WORLDS.remove(world);
    }

    public static void onWorldUnload(ServerWorld world) {
        WorldState state = WORLDS.remove(world);
        if (state == null) return;
        for (Map.Entry<UUID, Pose> entry : state.forcedPlayerPoses.entrySet()) {
            Entity entity = world.getEntity(entry.getKey());
            if (entity instanceof ServerPlayerEntity) restorePlayerPose((ServerPlayerEntity) entity, entry.getValue());
        }
    }

    private static Map<BlockPos, DropperRef> findConnectedDroppers(ServerWorld world, VentSpatialRegistry registry) {
        Map<BlockPos, DropperRef> result = new HashMap<>();
        for (VentEdge edge : registry.getNetworkManager().getEdges()) {
            addDropper(world, registry, edge, edge.getA(), result);
            addDropper(world, registry, edge, edge.getB(), result);
        }
        return result;
    }

    private static void addDropper(ServerWorld world, VentSpatialRegistry registry, VentEdge edge, VentConnection connection,
            Map<BlockPos, DropperRef> result) {
        BlockPos main = findConnectedDropper(world, registry, edge, connection);
        if (main == null) return;
        DropperRef ref = getDropper(world, main);
        if (ref != null) result.put(ref.mainPos, ref);
    }

    private static boolean isManagedDropper(ServerWorld world, BlockPos mainPos) {
        WorldState state = WORLDS.get(world);
        if (state != null && state.droppers.containsKey(mainPos)) return true;
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        for (VentEdge edge : registry.getNetworkManager().getEdges()) {
            BlockPos a = findConnectedDropper(world, registry, edge, edge.getA());
            if (mainPos.equals(a)) return true;
            BlockPos b = findConnectedDropper(world, registry, edge, edge.getB());
            if (mainPos.equals(b)) return true;
        }
        return false;
    }

    private static DropperRef getDropper(ServerWorld world, BlockPos mainPos) {
        if (mainPos == null) return null;
        BlockState state = world.getBlockState(mainPos);
        if (!(state.getBlock() instanceof CubeDropperBlock)) return null;
        TileEntity tile = world.getBlockEntity(mainPos);
        if (!(tile instanceof CubeDropperTileEntity)) return null;
        return new DropperRef(new BlockPos(mainPos.getX(), mainPos.getY(), mainPos.getZ()),
                (CubeDropperBlock) state.getBlock(), (CubeDropperTileEntity) tile);
    }

    private static void maintainTakeover(DropperRef ref) {
        CubeDropperTileEntity tile = ref.tile;
        boolean dirty = false;
        if (tile.hasEntityNBT()) {
            tile.removeAllEntities();
            tile.removeEntityNBT();
            dirty = true;
        } else if (!tile.entityUUIDs.isEmpty()) {
            // UUIDs here can only be PortalMod-owned dropper entities; vent-delivered entities are never added to this list.
            tile.removeAllEntities();
            dirty = true;
        }
        if (tile.openTicks != 0) {
            tile.openTicks = 0;
            dirty = true;
        }
        if (tile.wasActive) {
            tile.wasActive = false;
            dirty = true;
        }
        if (dirty) tile.setChanged();
    }

    private static void updateDoor(ServerWorld world, DropperRef ref, DropperState state) {
        boolean occupied = isChamberOccupied(world, ref.mainPos);
        long now = world.getGameTime();
        if (occupied) {
            state.emptySince = Long.MIN_VALUE;
            setOpen(ref, true);
            return;
        }

        if (state.emptySince == Long.MIN_VALUE) {
            if (!isOpen(ref)) {
                state.emptySince = now;
                return;
            }
            state.emptySince = now;
        }
        if (now - state.emptySince >= EMPTY_CLOSE_DELAY_TICKS) setOpen(ref, false);
    }

    private static boolean isChamberOccupied(ServerWorld world, BlockPos mainPos) {
        AxisAlignedBB chamber = chamberBounds(mainPos);
        List<Entity> entities = world.getEntities((Entity) null, chamber, entity -> entity != null && !entity.removed);
        for (Entity entity : entities) {
            AxisAlignedBB box = entity.getBoundingBox();
            double centerX = (box.minX + box.maxX) * 0.5D;
            double centerZ = (box.minZ + box.maxZ) * 0.5D;
            double feetY = entity.position().y;
            if (centerX > chamber.minX && centerX < chamber.maxX
                    && centerZ > chamber.minZ && centerZ < chamber.maxZ
                    && feetY >= chamber.minY && feetY < chamber.maxY) return true;
        }
        return false;
    }

    private static AxisAlignedBB chamberBounds(BlockPos mainPos) {
        double floor = mainPos.getY() - 1.0D + FLOOR_HEIGHT;
        double ceiling = mainPos.getY() + CEILING_HEIGHT;
        return new AxisAlignedBB(mainPos.getX() + CHAMBER_MIN_OFFSET, floor, mainPos.getZ() + CHAMBER_MIN_OFFSET,
                mainPos.getX() + CHAMBER_MAX_OFFSET, ceiling, mainPos.getZ() + CHAMBER_MAX_OFFSET);
    }

    private static boolean isOpen(DropperRef ref) {
        BlockState state = ref.tile.getBlockState();
        return state.hasProperty(CubeDropperBlock.OPEN) && state.getValue(CubeDropperBlock.OPEN);
    }

    private static void setOpen(DropperRef ref, boolean open) {
        BlockState state = ref.tile.getBlockState();
        if (!state.hasProperty(CubeDropperBlock.OPEN) || state.getValue(CubeDropperBlock.OPEN) == open) return;
        ref.block.setOpen(open, state, ref.tile.getLevel(), ref.mainPos);
    }

    private static void forceCrouching(WorldState state, ServerPlayerEntity player) {
        state.forcedPlayerPoses.putIfAbsent(player.getUUID(), player.getForcedPose());
        player.setForcedPose(Pose.CROUCHING);
        player.refreshDimensions();
    }

    private static void restorePlayersOutsideManagedChambers(ServerWorld world, WorldState state, Set<BlockPos> managedDroppers) {
        for (UUID playerId : new HashSet<>(state.forcedPlayerPoses.keySet())) {
            Entity entity = world.getEntity(playerId);
            if (!(entity instanceof ServerPlayerEntity)) {
                state.forcedPlayerPoses.remove(playerId);
                continue;
            }
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            boolean inManagedChamber = false;
            for (BlockPos main : managedDroppers) {
                AxisAlignedBB chamber = chamberBounds(main);
                AxisAlignedBB box = player.getBoundingBox();
                double centerX = (box.minX + box.maxX) * 0.5D;
                double centerZ = (box.minZ + box.maxZ) * 0.5D;
                boolean verticalOverlap = box.maxY > chamber.minY && box.minY < chamber.maxY;
                if (centerX > chamber.minX && centerX < chamber.maxX && centerZ > chamber.minZ && centerZ < chamber.maxZ
                        && verticalOverlap) {
                    inManagedChamber = true;
                    break;
                }
            }
            if (inManagedChamber) continue;
            Pose previous = state.forcedPlayerPoses.remove(playerId);
            restorePlayerPose(player, previous);
        }
    }

    private static void restorePlayerPose(ServerPlayerEntity player, Pose previous) {
        player.setForcedPose(previous);
        player.refreshDimensions();
    }

    private static final class DropperRef {
        private final BlockPos mainPos;
        private final CubeDropperBlock block;
        private final CubeDropperTileEntity tile;

        private DropperRef(BlockPos mainPos, CubeDropperBlock block, CubeDropperTileEntity tile) {
            this.mainPos = mainPos;
            this.block = block;
            this.tile = tile;
        }
    }

    private static final class DropperState {
        private long emptySince = Long.MIN_VALUE;
    }

    private static final class WorldState {
        private final Map<BlockPos, DropperState> droppers = new HashMap<>();
        private final Map<UUID, Pose> forcedPlayerPoses = new HashMap<>();
    }
}
