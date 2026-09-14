/* Persistent per-dimension bridge between world blocks/endpoints and the pure vent network. */
package io.github.bengman.pneumaticdiversityvents.server;

import io.github.bengman.pneumaticdiversityvents.server.integration.VentAntlineConnections;
import io.github.bengman.pneumaticdiversityvents.server.network.VentConnection;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentForceSource;
import io.github.bengman.pneumaticdiversityvents.server.network.VentNetwork;
import io.github.bengman.pneumaticdiversityvents.server.network.VentNetworkManager;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentConnection;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerControlMode;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerVisualState;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.portalmod.common.sorted.antline.indicator.IndicatorActivated;
import net.portalmod.common.sorted.antline.indicator.IndicatorInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VentSpatialRegistry extends WorldSavedData {
    private static final String DATA_NAME = "diversity_vent_segments";
    private static final int DATA_VERSION = 8;
    private static final String VERSION_TAG = "Version";
    private static final String EDGES_TAG = "Edges";
    private static final String LEGACY_SEGMENTS_TAG = "Segments";
    private static final String ID_TAG = "Id";
    private static final String CONNECTION_ID_TAG = "ConnectionId";
    private static final String BLOCKS_TAG = "Blocks";
    private static final String A_TAG = "A";
    private static final String B_TAG = "B";
    private static final String COORDINATE_TAG = "Coordinate";
    private static final String AXIS_TAG = "Axis";
    private static final String ATOB_TAG = "AToB";
    private static final String FORCE_SOURCE_TAG = "ForceSource";
    private static final String FORCE_UNITS_TAG = "Units";
    private static final String FORCE_ATOB_TAG = "AToB";
    private static final String FORCE_CONTROL_MODE_TAG = "ControlMode";
    private static final String CONNECTABLE_TAG = "Connectable";

    private final VentNetworkManager networkManager = new VentNetworkManager();
    private final Map<UUID, List<BlockPos>> blocksByEdge = new HashMap<>();
    private final Map<Long, UUID> edgeByBlock = new HashMap<>();
    private final Map<UUID, WorldVentConnection> worldConnectionsById = new HashMap<>();
    private final Map<ConnectionKey, Set<UUID>> connectionIdsByKey = new HashMap<>();
    private boolean legacyForceDiscoveryPending;
    private boolean impellerBlockstateSyncPending;
    private long lastImpellerControlTick = Long.MIN_VALUE;
    private long lastBlockageTick = Long.MIN_VALUE;

    public VentSpatialRegistry() {
        super(DATA_NAME);
    }

    public static VentSpatialRegistry get(World world) {
        if (!(world instanceof ServerWorld)) throw new IllegalStateException("VentSpatialRegistry can only be accessed on the server.");
        ServerWorld serverWorld = (ServerWorld) world;
        return serverWorld.getDataStorage().computeIfAbsent(VentSpatialRegistry::new, DATA_NAME);
    }

    /* Must be called from the server thread; performs one-time force metadata migration for old saves. */
    public synchronized void prepareForceState(ServerWorld world) {
        discoverLegacyImpellers(world);
        syncLegacyImpellerBlockstates(world);
        if (lastImpellerControlTick != world.getGameTime()) {
            lastImpellerControlTick = world.getGameTime();
            syncImpellerControls(world);
        }

        if (lastBlockageTick != world.getGameTime()) {
            lastBlockageTick = world.getGameTime();
            refreshBlockages(world);
        }
    }

    /** Recomputes the transient "can air actually leave both ends?" state without changing graph topology. */
    private void refreshBlockages(ServerWorld world) {
        for (VentNetwork network : networkManager.getNetworks()) network.setBlocked(false);
        for (VentEdge edge : networkManager.getEdges()) {
            VentNetwork network = edge.getNetwork();
            if (network == null || network.isBlocked() || network.getRawNetForce() == 0) continue;
            if (edge.getA().getPeer() == null && isEndpointBlocked(world, edge, edge.getA())) network.setBlocked(true);
            if (!network.isBlocked() && edge.getB().getPeer() == null && isEndpointBlocked(world, edge, edge.getB())) network.setBlocked(true);
        }
    }

    private boolean isEndpointBlocked(ServerWorld world, VentEdge edge, VentConnection connection) {
        WorldVentConnection endpoint = requireWorldConnection(connection.getId());
        List<BlockPos> blocks = getBlocks(edge.getId());
        Direction outward = endpointDirection(endpoint, blocks).direction;
        List<BlockPos> face = endpointFaceBlocks(blocks, outward);
        if (face.size() != 4) return false; // Every supported 2x2 mouth should resolve to exactly four physical face cells.

        List<BlockPos> outside = new ArrayList<>(4);
        for (BlockPos block : face) outside.add(block.relative(outward));
        Boolean junctionPortBlocked = junctionPortBlocked(world, endpoint, outside);
        if (junctionPortBlocked != null) return junctionPortBlocked;

        for (BlockPos pos : outside) {
            BlockState state = world.getBlockState(pos);
            if (state.getCollisionShape(world, pos).isEmpty()) return false;
        }
        return true;
    }

    /** Returns null when the four adjacent cells are not one junction port; otherwise returns whether that port is closed. */
    private Boolean junctionPortBlocked(ServerWorld world, WorldVentConnection sourceEndpoint, List<BlockPos> outside) {
        UUID junctionId = null;
        for (BlockPos pos : outside) {
            if (!(world.getBlockState(pos).getBlock() instanceof VentJunctionBlock)) return null;
            UUID edgeId = edgeByBlock.get(pos.asLong());
            if (edgeId == null) return null;
            if (junctionId == null) junctionId = edgeId;
            else if (!junctionId.equals(edgeId)) return null;
        }
        VentEdge junction = junctionId == null ? null : networkManager.getEdge(junctionId);
        if (junction == null) return null;
        // The source coordinate is exactly the coordinate this junction would expose if that conditional port were open.
        for (VentConnection candidate : junction.getConnections()) {
            WorldVentConnection worldConnection = worldConnectionsById.get(candidate.getId());
            if (worldConnection != null && worldConnection.getCoordinate().equals(sourceEndpoint.getCoordinate())
                    && worldConnection.getAxis() == sourceEndpoint.getAxis()) return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private static List<BlockPos> endpointFaceBlocks(List<BlockPos> blocks, Direction outward) {
        int bound = outward.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            int coordinate = outward.getAxis() == Direction.Axis.X ? block.getX()
                    : outward.getAxis() == Direction.Axis.Y ? block.getY() : block.getZ();
            bound = outward.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? Math.min(bound, coordinate) : Math.max(bound, coordinate);
        }
        List<BlockPos> result = new ArrayList<>(4);
        for (BlockPos block : blocks) {
            int coordinate = outward.getAxis() == Direction.Axis.X ? block.getX()
                    : outward.getAxis() == Direction.Axis.Y ? block.getY() : block.getZ();
            if (coordinate == bound) result.add(block);
        }
        return result;
    }

    public VentNetworkManager getNetworkManager() {
        return networkManager;
    }

    public VentEdge getEdge(UUID id) {
        return networkManager.getEdge(id);
    }

    public UUID getEdgeId(BlockPos position) {
        return edgeByBlock.get(position.asLong());
    }

    public VentEdge getEdge(BlockPos position) {
        UUID id = getEdgeId(position);
        return id == null ? null : getEdge(id);
    }

    public List<BlockPos> getBlocks(UUID edgeId) {
        return blocksByEdge.containsKey(edgeId) ? blocksByEdge.get(edgeId) : Collections.emptyList();
    }

    public WorldVentConnection getWorldConnection(UUID connectionId) {
        return worldConnectionsById.get(connectionId);
    }

    public synchronized int getNetworkForce(BlockPos position) {
        VentEdge edge = getEdge(position);
        return edge == null || edge.getNetwork() == null ? 0 : edge.getNetwork().getNetForce();
    }

    /* Returns net force signed in the containing edge's physical A->B direction. */
    public synchronized int getLocalForce(BlockPos position) {
        VentEdge edge = getEdge(position);
        if (edge == null || edge.getNetwork() == null) return 0;
        int force = edge.getNetwork().getNetForce();
        return edge.isAToB() ? force : -force;
    }

    public synchronized int getActiveForceSources(BlockPos position) {
        VentEdge edge = getEdge(position);
        return edge == null || edge.getNetwork() == null ? 0 : edge.getNetwork().getActiveForceSources();
    }

    public synchronized boolean invertImpeller(ServerWorld world, BlockPos position) {
        VentEdge edge = getEdge(position);
        if (edge == null || edge.getForceSource() == null) return false;
        networkManager.invertForceSource(edge);
        syncImpellerBlockstates(world, edge);
        setDirty();
        return true;
    }

    public synchronized boolean isImpellerAToB(BlockPos position) {
        VentEdge edge = getEdge(position);
        return edge != null && edge.getForceSource() != null && edge.getForceSource().isAToB();
    }

    public synchronized boolean isImpellerAntlineControlled(BlockPos position) {
        VentEdge edge = getEdge(position);
        return edge != null && edge.getForceSource() != null && edge.getForceSource().isAntlineControlled();
    }

    public synchronized VentImpellerControlMode getImpellerControlMode(BlockPos position) {
        VentEdge edge = getEdge(position);
        return edge == null || edge.getForceSource() == null ? VentImpellerControlMode.ON_OFF : edge.getForceSource().getControlMode();
    }

    public synchronized boolean cycleImpellerControlMode(ServerWorld world, BlockPos position) {
        VentEdge edge = getEdge(position);
        if (edge == null || edge.getForceSource() == null || !edge.getForceSource().isAntlineControlled()) return false;
        if (!networkManager.cycleForceSourceControlMode(edge)) return false;
        syncImpellerBlockstates(world, edge);
        setDirty();
        return true;
    }


    /* Snapshots powered open endpoints without exposing mutable graph/world metadata to field consumers. */
    public synchronized List<OpenEndpoint> getOpenForceEndpoints() {
        List<OpenEndpoint> result = new ArrayList<>();
        for (VentEdge edge : networkManager.getEdges()) {
            VentNetwork network = edge.getNetwork();
            if (network == null || !network.hasActiveForceField() || network.getRawNetForce() == 0) continue;
            // Preserve endpoint polarity while blocked, but pass effective force (zero) into the runtime field.
            int localForce = edge.isAToB() ? network.getRawNetForce() : -network.getRawNetForce();
            boolean flowAToB = localForce > 0;
            if (!edge.getA().isConnected()) result.add(buildOpenEndpoint(edge, edge.getA(), true, flowAToB, network));
            if (!edge.getB().isConnected()) result.add(buildOpenEndpoint(edge, edge.getB(), false, flowAToB, network));
        }
        result.sort(Comparator.comparing(OpenEndpoint::getConnectionId));
        return Collections.unmodifiableList(result);
    }

    private OpenEndpoint buildOpenEndpoint(VentEdge edge, VentConnection connection, boolean atA, boolean flowAToB, VentNetwork network) {
        WorldVentConnection worldConnection = requireWorldConnection(connection.getId());
        List<BlockPos> blocks = getBlocks(edge.getId());
        DirectionInfo direction = endpointDirection(worldConnection, blocks);
        boolean intake = atA == flowAToB;
        BlockPos profileBlock = endpointProfileBlock(blocks, direction.direction);
        return new OpenEndpoint(connection.getId(), edge.getId(), network.getId(), worldConnection.getWorldPosition(), direction.direction,
                intake, network.getNetForce(), profileBlock, blocks);
    }

    private static DirectionInfo endpointDirection(WorldVentConnection connection, List<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }
        BlockPos coordinate = connection.getCoordinate();
        switch (connection.getAxis()) {
            case X:
                if (coordinate.getX() == minX * 2) return new DirectionInfo(net.minecraft.util.Direction.WEST);
                if (coordinate.getX() == (maxX + 1) * 2) return new DirectionInfo(net.minecraft.util.Direction.EAST);
                break;
            case Y:
                if (coordinate.getY() == minY * 2) return new DirectionInfo(net.minecraft.util.Direction.DOWN);
                if (coordinate.getY() == (maxY + 1) * 2) return new DirectionInfo(net.minecraft.util.Direction.UP);
                break;
            case Z:
                if (coordinate.getZ() == minZ * 2) return new DirectionInfo(net.minecraft.util.Direction.NORTH);
                if (coordinate.getZ() == (maxZ + 1) * 2) return new DirectionInfo(net.minecraft.util.Direction.SOUTH);
                break;
        }
        throw new IllegalStateException("Open vent connection does not lie on its edge bounds: " + connection.getConnectionId());
    }

    private static BlockPos endpointProfileBlock(List<BlockPos> blocks, net.minecraft.util.Direction direction) {
        int bound = direction.getAxisDirection() == net.minecraft.util.Direction.AxisDirection.NEGATIVE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            int coordinate = direction.getAxis() == net.minecraft.util.Direction.Axis.X ? block.getX()
                    : direction.getAxis() == net.minecraft.util.Direction.Axis.Y ? block.getY() : block.getZ();
            bound = direction.getAxisDirection() == net.minecraft.util.Direction.AxisDirection.NEGATIVE
                    ? Math.min(bound, coordinate) : Math.max(bound, coordinate);
        }
        final int face = bound;
        return blocks.stream().filter(block -> {
            switch (direction.getAxis()) {
                case X: return block.getX() == face;
                case Y: return block.getY() == face;
                case Z: return block.getZ() == face;
                default: return false;
            }
        }).min(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ))
                .orElseThrow(() -> new IllegalStateException("Vent endpoint has no physical face block."));
    }


    public synchronized List<ActiveNetworkSound> getActiveNetworkSounds() {
        Map<UUID, List<BlockPos>> blocks = new HashMap<>();
        Map<UUID, Integer> forces = new HashMap<>();
        for (VentEdge edge : networkManager.getEdges()) {
            VentNetwork network = edge.getNetwork();
            if (network == null || !network.hasActiveForceField() || network.getNetForce() == 0) continue;
            blocks.computeIfAbsent(network.getId(), ignored -> new ArrayList<>()).addAll(getBlocks(edge.getId()));
            forces.put(network.getId(), network.getNetForce());
        }
        List<ActiveNetworkSound> result = new ArrayList<>();
        for (Map.Entry<UUID, List<BlockPos>> entry : blocks.entrySet()) {
            double x = 0, y = 0, z = 0;
            for (BlockPos p : entry.getValue()) {
                x += p.getX() + 0.5D;
                y += p.getY() + 0.5D;
                z += p.getZ() + 0.5D;
            }
            int count = Math.max(1, entry.getValue().size());
            List<Vector3d> points = new ArrayList<>();
            for (BlockPos block : entry.getValue()) points.add(new Vector3d(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D));
            result.add(new ActiveNetworkSound(entry.getKey(), new Vector3d(x / count, y / count, z / count), forces.get(entry.getKey()), points));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<ActiveImpellerSound> getActiveImpellerSounds() {
        List<ActiveImpellerSound> result = new ArrayList<>();
        for (VentEdge edge : networkManager.getEdges()) {
            VentForceSource source = edge.getForceSource();
            if (source == null) continue;
            List<BlockPos> blocks = blocksByEdge.get(edge.getId());
            if (blocks == null || blocks.isEmpty()) continue;
            double x = 0.0D, y = 0.0D, z = 0.0D;
            for (BlockPos block : blocks) {
                x += block.getX() + 0.5D;
                y += block.getY() + 0.5D;
                z += block.getZ() + 0.5D;
            }
            double count = blocks.size();
            boolean strained = source.isEnabled() && edge.getNetwork() != null && edge.getNetwork().isBlocked();
            result.add(new ActiveImpellerSound(edge.getId(), new Vector3d(x / count, y / count, z / count), source.isEnabled(), strained));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized void register(VentEdge edge, Collection<BlockPos> blocks, Collection<WorldVentConnection> connections) {
        List<BlockPos> blockList = immutableBlocks(blocks);
        Map<UUID, WorldVentConnection> connectionMap = validate(edge, blockList, connections);
        networkManager.addEdge(edge);
        blocksByEdge.put(edge.getId(), blockList);
        for (BlockPos block : blockList) edgeByBlock.put(block.asLong(), edge.getId());
        for (WorldVentConnection connection : connectionMap.values()) indexConnection(connection);

        Set<VentEdge> affected = new HashSet<>();
        affected.add(edge);
        for (WorldVentConnection connection : connectionMap.values()) {
            VentEdge peer = resolveConnection(connection);
            if (peer != null) affected.add(peer);
        }
        networkManager.rebuildFrom(affected);
        setDirty();
    }

    public synchronized List<BlockPos> unregister(UUID edgeId) {
        VentEdge edge = networkManager.getEdge(edgeId);
        if (edge == null) return Collections.emptyList();
        List<BlockPos> blocks = blocksByEdge.remove(edgeId);
        if (blocks == null) blocks = Collections.emptyList();
        for (BlockPos block : blocks) edgeByBlock.remove(block.asLong(), edgeId);
        for (VentConnection connection : edge.getConnections()) unindexConnection(connection.getId());

        Set<VentEdge> affected = networkManager.removeEdge(edgeId);
        networkManager.rebuildFrom(affected);
        setDirty();
        return blocks;
    }

    private Map<UUID, WorldVentConnection> validate(VentEdge edge, List<BlockPos> blocks, Collection<WorldVentConnection> connections) {
        if (networkManager.getEdge(edge.getId()) != null) throw new IllegalStateException("Vent edge UUID already registered: " + edge.getId());
        for (BlockPos block : blocks) {
            UUID existing = edgeByBlock.get(block.asLong());
            if (existing != null) throw new IllegalStateException("Block position " + block + " already belongs to vent edge " + existing);
        }

        Map<UUID, WorldVentConnection> result = new HashMap<>();
        for (WorldVentConnection connection : connections) {
            if (worldConnectionsById.containsKey(connection.getConnectionId()) || result.put(connection.getConnectionId(), connection) != null)
                throw new IllegalStateException("Vent world connection UUID already registered: " + connection.getConnectionId());
        }
        if (result.size() != 2 || !result.containsKey(edge.getA().getId()) || !result.containsKey(edge.getB().getId()))
            throw new IllegalArgumentException("World vent connections must exactly match the edge's two abstract connections.");
        return result;
    }

    private void indexConnection(WorldVentConnection connection) {
        worldConnectionsById.put(connection.getConnectionId(), connection);
        if (connection.isConnectable()) connectionIdsByKey.computeIfAbsent(new ConnectionKey(connection), ignored -> new LinkedHashSet<>()).add(connection.getConnectionId());
    }

    private void unindexConnection(UUID connectionId) {
        WorldVentConnection connection = worldConnectionsById.remove(connectionId);
        if (connection == null) return;
        ConnectionKey key = new ConnectionKey(connection);
        Set<UUID> ids = connectionIdsByKey.get(key);
        if (ids == null) return;
        ids.remove(connectionId);
        if (ids.isEmpty()) connectionIdsByKey.remove(key);
    }

    private VentEdge resolveConnection(WorldVentConnection worldConnection) {
        if (!worldConnection.isConnectable()) return null;
        VentConnection connection = networkManager.getConnection(worldConnection.getConnectionId());
        if (connection == null || connection.getPeer() != null) return null;
        Set<UUID> candidates = connectionIdsByKey.get(new ConnectionKey(worldConnection));
        if (candidates == null) return null;

        UUID peerId = candidates.stream().filter(id -> !id.equals(connection.getId())).sorted().filter(id -> {
            VentConnection candidate = networkManager.getConnection(id);
            return candidate != null && candidate.getPeer() == null && candidate.getParent() != connection.getParent();
        }).findFirst().orElse(null);
        if (peerId == null) return null;
        VentConnection peer = networkManager.getConnection(peerId);
        networkManager.connect(connection.getId(), peerId);
        return peer.getParent();
    }

    private void resolveAllConnections() {
        List<WorldVentConnection> connections = new ArrayList<>(worldConnectionsById.values());
        connections.sort(Comparator.comparing(WorldVentConnection::getConnectionId));
        for (WorldVentConnection connection : connections) resolveConnection(connection);
    }

    @Override
    public synchronized void load(CompoundNBT nbt) {
        clearRuntime();
        int version = nbt.contains(VERSION_TAG) ? nbt.getInt(VERSION_TAG) : 2;
        if (version < 2 || version > DATA_VERSION) throw new IllegalStateException("Unsupported vent registry version: " + version);
        legacyForceDiscoveryPending = version < 4;
        impellerBlockstateSyncPending = version < 7;
        String listTag = version == 2 ? LEGACY_SEGMENTS_TAG : EDGES_TAG;
        ListNBT edgesNBT = nbt.getList(listTag, 10);

        for (int i = 0; i < edgesNBT.size(); i++) {
            CompoundNBT edgeNBT = edgesNBT.getCompound(i);
            UUID edgeId = edgeNBT.getUUID(ID_TAG);
            CompoundNBT aNBT = edgeNBT.getCompound(A_TAG), bNBT = edgeNBT.getCompound(B_TAG);
            UUID aId = version >= 3 && aNBT.contains(CONNECTION_ID_TAG) ? aNBT.getUUID(CONNECTION_ID_TAG) : UUID.randomUUID();
            UUID bId = version >= 3 && bNBT.contains(CONNECTION_ID_TAG) ? bNBT.getUUID(CONNECTION_ID_TAG) : UUID.randomUUID();
            VentEdge edge = new VentEdge(edgeId, new VentConnection(aId), new VentConnection(bId));
            if (edgeNBT.contains(ATOB_TAG)) edge.setAToB(edgeNBT.getBoolean(ATOB_TAG));
            if (version >= 4 && edgeNBT.contains(FORCE_SOURCE_TAG)) {
                VentForceSource source = readForceSource(edgeNBT.getCompound(FORCE_SOURCE_TAG));
                if (version < 8) source = new VentForceSource(VentImpellerBlock.FORCE_UNITS, source.isBaseAToB(), source.getControlMode());
                edge.setForceSource(source);
            }

            List<BlockPos> blocks = new ArrayList<>();
            ListNBT blocksNBT = edgeNBT.getList(BLOCKS_TAG, 10);
            for (int j = 0; j < blocksNBT.size(); j++) blocks.add(NBTUtil.readBlockPos(blocksNBT.getCompound(j)));
            if (blocks.isEmpty()) continue;

            WorldVentConnection a = readWorldConnection(aId, aNBT);
            WorldVentConnection b = readWorldConnection(bId, bNBT);
            if (version < 5) {
                a = normalizeLegacyConnection(a, blocks);
                b = normalizeLegacyConnection(b, blocks);
            }
            loadRegister(edge, blocks, java.util.Arrays.asList(a, b));
        }
        resolveAllConnections();
        networkManager.rebuildAll();
        if (version < 8) setDirty();
    }

    private void loadRegister(VentEdge edge, Collection<BlockPos> blocks, Collection<WorldVentConnection> connections) {
        List<BlockPos> blockList = immutableBlocks(blocks);
        Map<UUID, WorldVentConnection> connectionMap = validate(edge, blockList, connections);
        networkManager.addEdge(edge);
        blocksByEdge.put(edge.getId(), blockList);
        for (BlockPos block : blockList) edgeByBlock.put(block.asLong(), edge.getId());
        for (WorldVentConnection connection : connectionMap.values()) indexConnection(connection);
    }

    @Override
    public synchronized CompoundNBT save(CompoundNBT nbt) {
        nbt.putInt(VERSION_TAG, DATA_VERSION);
        ListNBT edgesNBT = new ListNBT();
        List<VentEdge> edges = new ArrayList<>(networkManager.getEdges());
        edges.sort(Comparator.comparing(VentEdge::getId));
        for (VentEdge edge : edges) {
            CompoundNBT edgeNBT = new CompoundNBT();
            edgeNBT.putUUID(ID_TAG, edge.getId());
            edgeNBT.put(A_TAG, writeWorldConnection(requireWorldConnection(edge.getA().getId())));
            edgeNBT.put(B_TAG, writeWorldConnection(requireWorldConnection(edge.getB().getId())));
            edgeNBT.putBoolean(ATOB_TAG, edge.isAToB());
            if (edge.getForceSource() != null) edgeNBT.put(FORCE_SOURCE_TAG, writeForceSource(edge.getForceSource()));
            ListNBT blocksNBT = new ListNBT();
            for (BlockPos block : getBlocks(edge.getId())) blocksNBT.add(NBTUtil.writeBlockPos(block));
            edgeNBT.put(BLOCKS_TAG, blocksNBT);
            edgesNBT.add(edgeNBT);
        }
        nbt.put(EDGES_TAG, edgesNBT);
        return nbt;
    }

    private WorldVentConnection requireWorldConnection(UUID id) {
        WorldVentConnection connection = worldConnectionsById.get(id);
        if (connection == null) throw new IllegalStateException("Missing world metadata for vent connection " + id);
        return connection;
    }

    private static WorldVentConnection readWorldConnection(UUID id, CompoundNBT nbt) {
        return new WorldVentConnection(id, NBTUtil.readBlockPos(nbt.getCompound(COORDINATE_TAG)), VentAxis.valueOf(nbt.getString(AXIS_TAG)),
                !nbt.contains(CONNECTABLE_TAG) || nbt.getBoolean(CONNECTABLE_TAG));
    }


    /* v2-v4 stored endpoint cross-coordinates at one corner-block center; normalize them to the 2x2 face center. */
    private static WorldVentConnection normalizeLegacyConnection(WorldVentConnection connection, Collection<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }
        BlockPos old = connection.getCoordinate();
        int centerX = minX + maxX + 1, centerY = minY + maxY + 1, centerZ = minZ + maxZ + 1;
        BlockPos normalized;
        switch (connection.getAxis()) {
            case X: normalized = new BlockPos(old.getX(), centerY, centerZ); break;
            case Y: normalized = new BlockPos(centerX, old.getY(), centerZ); break;
            case Z: normalized = new BlockPos(centerX, centerY, old.getZ()); break;
            default: throw new IllegalArgumentException("Unsupported vent axis: " + connection.getAxis());
        }
        return new WorldVentConnection(connection.getConnectionId(), normalized, connection.getAxis(), connection.isConnectable());
    }

    private static CompoundNBT writeWorldConnection(WorldVentConnection connection) {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putUUID(CONNECTION_ID_TAG, connection.getConnectionId());
        nbt.put(COORDINATE_TAG, NBTUtil.writeBlockPos(connection.getCoordinate()));
        nbt.putString(AXIS_TAG, connection.getAxis().name());
        nbt.putBoolean(CONNECTABLE_TAG, connection.isConnectable());
        return nbt;
    }

    private static VentForceSource readForceSource(CompoundNBT nbt) {
        int units = Math.max(1, nbt.getInt(FORCE_UNITS_TAG));
        VentImpellerControlMode mode = VentImpellerControlMode.ON_OFF;
        if (nbt.contains(FORCE_CONTROL_MODE_TAG)) {
            try { mode = VentImpellerControlMode.valueOf(nbt.getString(FORCE_CONTROL_MODE_TAG)); }

            catch (IllegalArgumentException ignored) {
            }
        }
        return new VentForceSource(units, nbt.getBoolean(FORCE_ATOB_TAG), mode);
    }

    private static CompoundNBT writeForceSource(VentForceSource source) {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putInt(FORCE_UNITS_TAG, source.getUnits());
        nbt.putBoolean(FORCE_ATOB_TAG, source.isBaseAToB());
        nbt.putString(FORCE_CONTROL_MODE_TAG, source.getControlMode().name());
        return nbt;
    }

    /* One-time v2/v3 migration so already-placed impellers gain default force metadata. */
    private synchronized void discoverLegacyImpellers(ServerWorld world) {
        if (!legacyForceDiscoveryPending) return;
        for (VentEdge edge : networkManager.getEdges()) {
            if (edge.getForceSource() != null) continue;
            for (BlockPos block : getBlocks(edge.getId())) {
                if (world.getBlockState(block).getBlock() instanceof VentImpellerBlock) {
                    edge.setForceSource(new VentForceSource(VentImpellerBlock.FORCE_UNITS, true));
                    break;
                }
            }
        }
        legacyForceDiscoveryPending = false;
        networkManager.recalculateAllForces();
        setDirty();
    }


    /* v2-v6 blockstates predate one or more persisted/derived impeller display properties. */
    private synchronized void syncLegacyImpellerBlockstates(ServerWorld world) {
        if (!impellerBlockstateSyncPending) return;
        for (VentEdge edge : networkManager.getEdges()) if (edge.getForceSource() != null) syncImpellerBlockstates(world, edge);
        impellerBlockstateSyncPending = false;
        setDirty();
    }

    /* PortalMod-compatible test-element control. Any external face-adjacent TestElementActivator counts; all present activators must be active.
     * A complete 2x2x1 impeller has 16 unique candidate cells: 8 around its perimeter plus 4 on each axial face. */
    private synchronized void syncImpellerControls(ServerWorld world) {
        boolean dirty = false;
        for (VentEdge edge : networkManager.getEdges()) {
            VentForceSource source = edge.getForceSource();
            if (source == null) continue;
            List<BlockPos> indicatorPositions = getImpellerIndicatorPositions(edge);
            IndicatorInfo info = IndicatorActivated.checkPositions(world, indicatorPositions);
            boolean changed = networkManager.updateForceSourceControl(edge, info.hasIndicators, info.hasIndicators && info.allIndicatorsActivated);
            if (changed) {
                syncImpellerBlockstates(world, edge);
                dirty = true;
            }
        }
        if (dirty) setDirty();
    }

    /* All vent antline devices share the same one-block shell around their complete physical footprint. */
    private List<BlockPos> getImpellerIndicatorPositions(VentEdge edge) {
        return VentAntlineConnections.candidatePositions(getBlocks(edge.getId()));
    }

    private void syncImpellerBlockstates(ServerWorld world, VentEdge edge) {
        VentForceSource source = edge.getForceSource();
        if (source == null) return;
        VentImpellerVisualState visual = VentImpellerVisualState.from(source.isAntlineControlled(), source.getControlMode(), source.isPowered());
        for (BlockPos block : getBlocks(edge.getId())) {
            net.minecraft.block.BlockState state = world.getBlockState(block);
            if (!(state.getBlock() instanceof VentImpellerBlock)) continue;
            net.minecraft.block.BlockState next = state;
            if (next.hasProperty(VentImpellerBlock.DIRECTION)) next = next.setValue(VentImpellerBlock.DIRECTION, source.isBaseAToB());
            if (next.hasProperty(VentImpellerBlock.ACTIVE)) next = next.setValue(VentImpellerBlock.ACTIVE, source.isEnabled());
            if (next.hasProperty(VentImpellerBlock.VISUAL)) next = next.setValue(VentImpellerBlock.VISUAL, visual);
            if (next != state) world.setBlock(block, next, 3);
        }
    }

    private void clearRuntime() {
        blocksByEdge.clear();
        edgeByBlock.clear();
        worldConnectionsById.clear();
        connectionIdsByKey.clear();
        lastImpellerControlTick = Long.MIN_VALUE;
        lastBlockageTick = Long.MIN_VALUE;
        for (VentEdge edge : new ArrayList<>(networkManager.getEdges())) networkManager.removeEdge(edge.getId());
    }

    private static List<BlockPos> immutableBlocks(Collection<BlockPos> blocks) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos block : blocks) result.add(new BlockPos(block.getX(), block.getY(), block.getZ()));
        return Collections.unmodifiableList(result);
    }


    public static final class OpenEndpoint {
        private final UUID connectionId, edgeId, networkId;
        private final Vector3d center;
        private final net.minecraft.util.Direction outwardDirection;
        private final boolean intake;
        private final int netForce;
        private final BlockPos profileBlock;
        private final List<BlockPos> edgeBlocks;

        private OpenEndpoint(UUID connectionId, UUID edgeId, UUID networkId, Vector3d center, net.minecraft.util.Direction outwardDirection,
                boolean intake, int netForce, BlockPos profileBlock, List<BlockPos> edgeBlocks) {
            this.connectionId = connectionId; this.edgeId = edgeId; this.networkId = networkId; this.center = center;
            this.outwardDirection = outwardDirection; this.intake = intake; this.netForce = netForce; this.profileBlock = profileBlock;
            this.edgeBlocks = edgeBlocks;
        }

        public UUID getConnectionId() {
            return connectionId;
        }

        public UUID getEdgeId() {
            return edgeId;
        }

        public UUID getNetworkId() {
            return networkId;
        }

        public Vector3d getCenter() {
            return center;
        }

        public net.minecraft.util.Direction getOutwardDirection() {
            return outwardDirection;
        }

        public boolean isIntake() {
            return intake;
        }

        public int getNetForce() {
            return netForce;
        }

        public BlockPos getProfileBlock() {
            return profileBlock;
        }

        public List<BlockPos> getEdgeBlocks() {
            return edgeBlocks;
        }
    }

    private static final class DirectionInfo {
        private final net.minecraft.util.Direction direction;
        private DirectionInfo(net.minecraft.util.Direction direction) {
            this.direction = direction;
        }
    }


    public static final class ActiveNetworkSound {
        private final UUID networkId;
        private final Vector3d center;
        private final int netForce;
        private final List<Vector3d> sourcePoints;
        private ActiveNetworkSound(UUID networkId, Vector3d center, int netForce, List<Vector3d> sourcePoints) {
            this.networkId = networkId; this.center = center; this.netForce = netForce;
            this.sourcePoints = Collections.unmodifiableList(new ArrayList<>(sourcePoints));
        }

        public UUID getNetworkId() {
            return networkId;
        }

        public Vector3d getCenter() {
            return center;
        }

        public int getNetForce() {
            return netForce;
        }

        public List<Vector3d> getSourcePoints() {
            return sourcePoints;
        }
    }

    public static final class ActiveImpellerSound {
        private final UUID edgeId;
        private final Vector3d center;
        private final boolean enabled;
        private final boolean strained;
        private ActiveImpellerSound(UUID edgeId, Vector3d center, boolean enabled, boolean strained) {
            this.edgeId = edgeId; this.center = center; this.enabled = enabled; this.strained = strained;
        }

        public UUID getEdgeId() {
            return edgeId;
        }

        public Vector3d getCenter() {
            return center;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isStrained() {
            return strained;
        }
    }

    private static final class ConnectionKey {
        private final long coordinate;
        private final VentAxis axis;
        private ConnectionKey(WorldVentConnection connection) {
            this.coordinate = connection.getCoordinate().asLong();
            this.axis = connection.getAxis();
        }

        @Override public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ConnectionKey)) return false;
            ConnectionKey other = (ConnectionKey) object;
            return coordinate == other.coordinate && axis == other.axis;
        }
        @Override
        public int hashCode() {
            return 31 * Long.hashCode(coordinate) + axis.hashCode();
        }
    }
}
