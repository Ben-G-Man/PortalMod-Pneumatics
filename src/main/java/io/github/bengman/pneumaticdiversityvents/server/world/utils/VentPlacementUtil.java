/* Converts vent-item clicks into validated queued additions, extensions, bends, and reroutes. */
package io.github.bengman.pneumaticdiversityvents.server.world.utils;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.VentUpdateQueue;
import io.github.bengman.pneumaticdiversityvents.server.network.VentConnection;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.update.VentAddition;
import io.github.bengman.pneumaticdiversityvents.server.update.VentExtension;
import io.github.bengman.pneumaticdiversityvents.server.update.VentReroute;
import io.github.bengman.pneumaticdiversityvents.server.world.BendVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.JunctionVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.LongVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.ReplaceableVentPlacementCollision;
import io.github.bengman.pneumaticdiversityvents.server.world.ShortVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.TerminalVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.VentJunctionManager;
import io.github.bengman.pneumaticdiversityvents.server.world.VentPlacementCollision;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentBuilder;
import io.github.bengman.pneumaticdiversityvents.server.world.WorldVentConnection;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalBlock;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentCorner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Collection;
import java.util.List;

public final class VentPlacementUtil {
    private static VentPlacementCollision collision = new ReplaceableVentPlacementCollision();
    private VentPlacementUtil() {
    }

    public static ActionResultType place(BlockItemUseContext context, VentBlock placedBlock) {
        World world = context.getLevel();
        if (world.isClientSide) return ActionResultType.SUCCESS;
        if (!(world instanceof ServerWorld)) return ActionResultType.FAIL;
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResultType.FAIL;

        ServerWorld serverWorld = (ServerWorld) world;
        // A terminal's outward face is deliberately a dead-end: clicking it with any vent item must not silently create a disconnected "extension".
        if (isNonConnectableVentFace(context, serverWorld)) return ActionResultType.FAIL;
        if (context.isSecondaryUseActive()) {
            if (placedBlock.canReroute()) {
                ActionResultType reroute = tryReroute(context, serverWorld, placedBlock);
                if (reroute != null) return finishPlacement(context, player, placedBlock, reroute);
            }

            if (placedBlock.isExtendable()) {
                ActionResultType extension = tryExtend(context, serverWorld, placedBlock);
                if (extension != null) return finishPlacement(context, player, placedBlock, extension);
            }
        }

        if (placedBlock instanceof VentTerminalBlock) return placeTerminal(context, serverWorld, player, (VentTerminalBlock) placedBlock);
        if (placedBlock instanceof VentJunctionBlock) return placeJunction(context, serverWorld, player, (VentJunctionBlock) placedBlock);

        AlignedPlacement aligned = getAlignedPlacement(context, serverWorld);
        FreePlacement free = aligned == null ? getFreePlacement(context) : null;
        VentAxis axis = aligned == null ? free.axis : aligned.axis;
        BlockPos origin = aligned == null ? free.origin : aligned.origin;
        ShortVentBuilder builder = new ShortVentBuilder(serverWorld, placedBlock, origin, axis);
        if (!collision.canPlace(context, builder.getPlannedBlocks())) return ActionResultType.FAIL;

        VentUpdateQueue.get().add(new VentAddition(builder));
        return finishPlacement(context, player, placedBlock, ActionResultType.SUCCESS);
    }

    private static ActionResultType finishPlacement(BlockItemUseContext context, PlayerEntity player, VentBlock block, ActionResultType result) {
        if (result == ActionResultType.SUCCESS) {
            if (!player.abilities.instabuild) context.getItemInHand().shrink(1);
            SoundType sound = block.defaultBlockState().getSoundType();
            context.getLevel().playSound(null, context.getClickedPos(), sound.getPlaceSound(), SoundCategory.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
        return result;
    }

    private static ActionResultType placeTerminal(BlockItemUseContext context, ServerWorld world, PlayerEntity player, VentTerminalBlock block) {
        AlignedPlacement aligned = getAlignedPlacement(context, world);
        FreePlacement free = aligned == null ? getFreePlacement(context) : null;
        VentAxis axis = aligned == null ? free.axis : aligned.axis;
        Direction outward = aligned == null ? free.outward : aligned.direction;
        BlockPos nearOrigin = aligned == null ? free.origin : aligned.origin;
        TerminalVentBuilder builder = new TerminalVentBuilder(world, block, nearOrigin, axis, outward);
        if (!collision.canPlace(context, builder.getPlannedBlocks())) return ActionResultType.FAIL;
        VentUpdateQueue.get().add(new VentAddition(builder));
        return finishPlacement(context, player, block, ActionResultType.SUCCESS);
    }

    private static ActionResultType placeJunction(BlockItemUseContext context, ServerWorld world, PlayerEntity player, VentJunctionBlock block) {
        AlignedPlacement aligned = getAlignedPlacement(context, world);
        FreePlacement free = aligned == null ? getFreePlacement(context) : null;
        VentAxis axis = aligned == null ? free.axis : aligned.axis;
        Direction outward = aligned == null ? free.outward : aligned.direction;
        BlockPos nearOrigin = aligned == null ? free.origin : aligned.origin;
        JunctionVentBuilder builder = new JunctionVentBuilder(world, block, nearOrigin, axis, outward);
        if (!collision.canPlace(context, builder.getPlannedBlocks())) return ActionResultType.FAIL;
        VentUpdateQueue.get().add(new VentAddition(builder));
        return finishPlacement(context, player, block, ActionResultType.SUCCESS);
    }

    private static ActionResultType tryExtend(BlockItemUseContext context, ServerWorld world, VentBlock placedBlock) {
        /* Only the ordinary one-long segment item participates in the short -> long upgrade. */
        if (!placedBlock.isExtendable()) return null;

        BlockPos clickedBlock = getActuallyClickedPos(context);
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(clickedBlock);
        if (edge == null || registry.getBlocks(edge.getId()).size() != 4) return null;
        if (VentUpdateQueue.get().isEdgePending(world, edge.getId())) return ActionResultType.FAIL;

        BlockState state = world.getBlockState(clickedBlock);
        if (!(state.getBlock() instanceof VentBlock) || !((VentBlock) state.getBlock()).isExtendable()) return null;

        EdgeBounds bounds = EdgeBounds.of(registry.getBlocks(edge.getId()));
        VentConnection endpoint = getConnectionOnClickedFace(context, registry, edge, bounds);
        if (endpoint == null) return null;
        if (endpoint.isConnected()) return ActionResultType.FAIL;

        WorldVentConnection worldEndpoint = registry.getWorldConnection(endpoint.getId());
        if (worldEndpoint == null) return ActionResultType.FAIL;
        Direction direction = getConnectionDirection(worldEndpoint, bounds);
        if (direction == null) return ActionResultType.FAIL;

        VentAxis axis = worldEndpoint.getAxis();
        BlockPos shortOrigin = bounds.min;
        LongVentBuilder builder = new LongVentBuilder(world, PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get(), shortOrigin, axis, direction.getAxisDirection());
        if (!collision.canPlace(context, builder.getExtensionBlocks())) return ActionResultType.FAIL;

        VentUpdateQueue.get().add(new VentExtension(world, edge.getId(), builder));
        return ActionResultType.SUCCESS;
    }

    private static ActionResultType tryReroute(BlockItemUseContext context, ServerWorld world, VentBlock placedBlock) {
        BlockPos clickedBlock = getActuallyClickedPos(context);
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(clickedBlock);
        if (edge == null) return null;

        List<BlockPos> edgeBlocks = registry.getBlocks(edge.getId());
        if (edgeBlocks.size() != 8) return null;

        if (containsBlock(world, edgeBlocks, PneumaticDiversityVents.VENT_JUNCTION_BLOCK.get())) {
            if (VentUpdateQueue.get().isEdgePending(world, edge.getId())) return ActionResultType.FAIL;
            EdgeBounds junctionBounds = EdgeBounds.of(edgeBlocks);
            Direction target = context.getClickedFace();
            if (!isBlockOnFace(clickedBlock, junctionBounds, target)) return null;
            BlockState junctionState = world.getBlockState(clickedBlock);
            if (!(junctionState.getBlock() instanceof VentJunctionBlock)) {
                for (BlockPos p : edgeBlocks) {
                    BlockState candidate = world.getBlockState(p);
                    if (candidate.getBlock() instanceof VentJunctionBlock) {
                        junctionState = candidate;
                        break;
                    }
                }
            }
            if (!(junctionState.getBlock() instanceof VentJunctionBlock) || target.getAxis() == junctionState.getValue(VentBlock.AXIS).getMinecraftAxis()) return null;
            BlockPos continuationOrigin = getAdjacentSegmentOrigin(junctionBounds, target);
            WorldVentBuilder continuation = createContinuationBuilder(world, placedBlock, continuationOrigin, target);
            if (!collision.canPlace(context, continuation.getPlannedBlocks())) return ActionResultType.FAIL;
            if (!VentJunctionManager.reorientBranch(world, edge.getId(), target)) return ActionResultType.FAIL;
            VentUpdateQueue.get().add(new VentAddition(continuation));
            return ActionResultType.SUCCESS;
        }

        boolean straightLong = allBlocksAre(world, edgeBlocks, PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get());
        boolean bend = containsBlock(world, edgeBlocks, PneumaticDiversityVents.VENT_SEGMENT_TURN_BLOCK.get());
        if (!straightLong && !bend) return null;
        if (VentUpdateQueue.get().isEdgePending(world, edge.getId())) return ActionResultType.FAIL;

        ConnectionState connections = getConnectionState(edge);
        if (connections == null) return null;

        EdgeBounds bounds = EdgeBounds.of(edgeBlocks);
        WorldVentConnection connectedWorld = registry.getWorldConnection(connections.connected.getId());
        WorldVentConnection openWorld = registry.getWorldConnection(connections.open.getId());
        if (connectedWorld == null || openWorld == null) return ActionResultType.FAIL;

        Direction connectedDirection = getConnectionDirection(connectedWorld, bounds);
        Direction openDirection = getConnectionDirection(openWorld, bounds);
        if (connectedDirection == null || openDirection == null) return ActionResultType.FAIL;

        Direction targetDirection = context.getClickedFace();
        if (!isBlockOnFace(clickedBlock, bounds, targetDirection)) return null;

        WorldVentBuilder replacement;
        if (straightLong) {
            /* A straight two-long only bends when one of its four lateral faces is shift-clicked. */
            if (targetDirection.getAxis() == connectedWorld.getAxis().getMinecraftAxis()) return null;
            replacement = new BendVentBuilder(world, PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get(), PneumaticDiversityVents.VENT_SEGMENT_TURN_BLOCK.get(),
                    bounds.min, connectedWorld.getAxis(), connectedDirection, targetDirection);
        } else {
            /* On a bend, the currently connected and currently open faces are not closed sides. */
            if (targetDirection == connectedDirection || targetDirection == openDirection) return null;

            if (targetDirection == connectedDirection.getOpposite()) {
                replacement = LongVentBuilder.fromOrigin(world, PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get(), bounds.min, connectedWorld.getAxis());
            } else {
                if (targetDirection.getAxis() == connectedDirection.getAxis()) return null;
                replacement = new BendVentBuilder(world, PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get(), PneumaticDiversityVents.VENT_SEGMENT_TURN_BLOCK.get(),
                        bounds.min, connectedWorld.getAxis(), connectedDirection, targetDirection);
            }
        }

        BlockPos continuationOrigin = getAdjacentSegmentOrigin(bounds, targetDirection);
        WorldVentBuilder continuation = createContinuationBuilder(world, placedBlock, continuationOrigin, targetDirection);
        if (!collision.canPlace(context, continuation.getPlannedBlocks())) return ActionResultType.FAIL;

        VentUpdateQueue.get().add(new VentReroute(world, edge.getId(), replacement, continuation));
        return ActionResultType.SUCCESS;
    }

    private static WorldVentBuilder createContinuationBuilder(ServerWorld world, VentBlock placedBlock, BlockPos nearOrigin, Direction direction) {
        VentAxis axis = VentAxis.fromDirection(direction);
        if (placedBlock instanceof VentTerminalBlock)
            return new TerminalVentBuilder(world, (VentTerminalBlock) placedBlock, nearOrigin, axis, direction);
        if (placedBlock instanceof VentJunctionBlock)
            return new JunctionVentBuilder(world, (VentJunctionBlock) placedBlock, nearOrigin, axis, direction);
        return new ShortVentBuilder(world, placedBlock, nearOrigin, axis);
    }

    private static boolean isNonConnectableVentFace(BlockItemUseContext context, ServerWorld world) {
        BlockPos clickedBlock = getActuallyClickedPos(context);
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(clickedBlock);
        if (edge == null) return false;
        EdgeBounds bounds = EdgeBounds.of(registry.getBlocks(edge.getId()));
        if (!isBlockOnFace(clickedBlock, bounds, context.getClickedFace())) return false;
        for (VentConnection connection : edge.getConnections()) {
            WorldVentConnection worldConnection = registry.getWorldConnection(connection.getId());
            if (worldConnection != null && !worldConnection.isConnectable()
                    && getConnectionDirection(worldConnection, bounds) == context.getClickedFace()) return true;
        }
        return false;
    }


    /**
     * Free placement follows the face normal while the player's view is within 45 degrees of it.
     * Beyond 45 degrees, the strongest component in the clicked face's tangent plane becomes the vent axis.
     * Existing vent endpoints bypass this entirely through {@link #getAlignedPlacement}.
     */
    private static FreePlacement getFreePlacement(BlockItemUseContext context) {
        PlayerEntity player = context.getPlayer();
        Direction face = context.getClickedFace();
        Vector3d view = player == null ? vector(face.getOpposite()) : player.getViewVector(1.0F);
        VentAxis axis = getPlacementAxis(face, view);
        Direction outward = axis.getMinecraftAxis() == face.getAxis() ? face : outwardFromView(axis, view);
        return new FreePlacement(axis, getPlacementOrigin(context, axis), outward);
    }

    static VentAxis getPlacementAxis(Direction clickedFace, Vector3d view) {
        Direction.Axis normal = clickedFace.getAxis();
        double nx = normal == Direction.Axis.X ? Math.abs(view.x) : 0.0D;
        double ny = normal == Direction.Axis.Y ? Math.abs(view.y) : 0.0D;
        double nz = normal == Direction.Axis.Z ? Math.abs(view.z) : 0.0D;
        double normalMagnitude = nx + ny + nz;
        double tx = normal == Direction.Axis.X ? 0.0D : view.x;
        double ty = normal == Direction.Axis.Y ? 0.0D : view.y;
        double tz = normal == Direction.Axis.Z ? 0.0D : view.z;
        double tangentMagnitude = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tangentMagnitude <= normalMagnitude + 1.0E-7D) return VentAxis.fromDirection(clickedFace);

        double ax = Math.abs(tx), ay = Math.abs(ty), az = Math.abs(tz);
        if (ax >= ay && ax >= az) return VentAxis.X;
        if (ay >= az) return VentAxis.Y;
        return VentAxis.Z;
    }

    private static Direction outwardFromView(VentAxis axis, Vector3d view) {
        double component;
        switch (axis) {
            case X: component = view.x; break;
            case Y: component = view.y; break;
            case Z: component = view.z; break;
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
        return direction(axis, component >= 0.0D ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
    }

    private static BlockPos getPlacementOrigin(BlockItemUseContext context, VentAxis axis) {
        BlockPos clicked = context.getClickedPos();
        Vector3d hit = context.getClickLocation();
        int x = clicked.getX(), y = clicked.getY(), z = clicked.getZ();
        if (axis != VentAxis.X) x += placementOffset(context, Direction.Axis.X, hit.x - clicked.getX());
        if (axis != VentAxis.Y) y += placementOffset(context, Direction.Axis.Y, hit.y - clicked.getY());
        if (axis != VentAxis.Z) z += placementOffset(context, Direction.Axis.Z, hit.z - clicked.getZ());
        return new BlockPos(x, y, z);
    }

    private static int placementOffset(BlockItemUseContext context, Direction.Axis transverseAxis, double localHit) {
        Direction face = context.getClickedFace();
        if (!context.replacingClickedOnBlock() && face.getAxis() == transverseAxis) {
            // Keep both rows of the 2x2 cross-section outside the solid support block.
            return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : -1;
        }
        return offset(localHit);
    }

    private static Vector3d vector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static AlignedPlacement getAlignedPlacement(BlockItemUseContext context, ServerWorld world) {
        BlockPos clickedBlock = getActuallyClickedPos(context);
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(clickedBlock);
        if (edge == null || VentUpdateQueue.get().isEdgePending(world, edge.getId())) return null;

        EdgeBounds bounds = EdgeBounds.of(registry.getBlocks(edge.getId()));
        VentConnection endpoint = getConnectionOnClickedFace(context, registry, edge, bounds);
        if (endpoint == null || endpoint.isConnected()) return null;

        WorldVentConnection worldEndpoint = registry.getWorldConnection(endpoint.getId());
        if (worldEndpoint == null) return null;
        Direction direction = getConnectionDirection(worldEndpoint, bounds);
        if (direction == null) return null;

        return new AlignedPlacement(worldEndpoint.getAxis(), getAdjacentSegmentOrigin(bounds, direction), direction);
    }

    private static VentConnection getConnectionOnClickedFace(BlockItemUseContext context, VentSpatialRegistry registry, VentEdge edge, EdgeBounds bounds) {
        BlockPos clickedBlock = getActuallyClickedPos(context);
        Direction clickedFace = context.getClickedFace();
        if (!isBlockOnFace(clickedBlock, bounds, clickedFace)) return null;

        for (VentConnection connection : edge.getConnections()) {
            WorldVentConnection worldConnection = registry.getWorldConnection(connection.getId());
            if (worldConnection != null && worldConnection.isConnectable() && getConnectionDirection(worldConnection, bounds) == clickedFace) return connection;
        }
        return null;
    }

    private static ConnectionState getConnectionState(VentEdge edge) {
        boolean a = edge.getA().isConnected(), b = edge.getB().isConnected();
        if (a == b) return null;
        return a ? new ConnectionState(edge.getA(), edge.getB()) : new ConnectionState(edge.getB(), edge.getA());
    }

    private static Direction getConnectionDirection(WorldVentConnection connection, EdgeBounds bounds) {
        BlockPos coordinate = connection.getCoordinate();
        VentAxis axis = connection.getAxis();
        int value = getAxisCoordinate(coordinate, axis);
        int minFace = getAxisCoordinate(bounds.min, axis) * 2;
        int maxFace = (getAxisCoordinate(bounds.max, axis) + 1) * 2;
        if (value == minFace) return direction(axis, Direction.AxisDirection.NEGATIVE);
        if (value == maxFace) return direction(axis, Direction.AxisDirection.POSITIVE);
        return null;
    }

    private static boolean isBlockOnFace(BlockPos block, EdgeBounds bounds, Direction face) {
        int value = getAxisCoordinate(block, VentAxis.fromDirection(face));
        int boundary = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? getAxisCoordinate(bounds.min, VentAxis.fromDirection(face))
                : getAxisCoordinate(bounds.max, VentAxis.fromDirection(face));
        return value == boundary;
    }

    private static BlockPos getAdjacentSegmentOrigin(EdgeBounds bounds, Direction direction) {
        int x = bounds.min.getX(), y = bounds.min.getY(), z = bounds.min.getZ();
        switch (direction) {
            case EAST: x = bounds.max.getX() + 1; break;
            case WEST: x = bounds.min.getX() - 1; break;
            case UP: y = bounds.max.getY() + 1; break;
            case DOWN: y = bounds.min.getY() - 1; break;
            case SOUTH: z = bounds.max.getZ() + 1; break;
            case NORTH: z = bounds.min.getZ() - 1; break;
            default: throw new IllegalArgumentException("Unsupported direction: " + direction);
        }
        return new BlockPos(x, y, z);
    }

    private static boolean allBlocksAre(ServerWorld world, Collection<BlockPos> blocks, Block block) {
        for (BlockPos position : blocks) if (world.getBlockState(position).getBlock() != block) return false;
        return true;
    }

    private static boolean containsBlock(ServerWorld world, Collection<BlockPos> blocks, Block block) {
        for (BlockPos position : blocks) if (world.getBlockState(position).getBlock() == block) return true;
        return false;
    }

    private static Direction direction(VentAxis axis, Direction.AxisDirection direction) {
        switch (axis) {
            case X: return direction == Direction.AxisDirection.POSITIVE ? Direction.EAST : Direction.WEST;
            case Y: return direction == Direction.AxisDirection.POSITIVE ? Direction.UP : Direction.DOWN;
            case Z: return direction == Direction.AxisDirection.POSITIVE ? Direction.SOUTH : Direction.NORTH;
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    private static BlockPos getActuallyClickedPos(BlockItemUseContext context) {
        return context.replacingClickedOnBlock() ? context.getClickedPos() : context.getClickedPos().relative(context.getClickedFace().getOpposite());
    }

    public static void setCollision(VentPlacementCollision collision) {
        if (collision == null) throw new IllegalArgumentException("Vent placement collision cannot be null.");
        VentPlacementUtil.collision = collision;
    }

    public static VentAxis getAxis(Direction clickedFace) {
        return VentAxis.fromDirection(clickedFace);
    }

    public static BlockPos getOrigin(BlockPos clickedPos, Vector3d hitLocation, VentAxis axis) {
        int x = clickedPos.getX(), y = clickedPos.getY(), z = clickedPos.getZ();
        switch (axis) {
            case X: y += offset(hitLocation.y - clickedPos.getY()); z += offset(hitLocation.z - clickedPos.getZ()); break;
            case Y: x += offset(hitLocation.x - clickedPos.getX()); z += offset(hitLocation.z - clickedPos.getZ()); break;
            case Z: x += offset(hitLocation.x - clickedPos.getX()); y += offset(hitLocation.y - clickedPos.getY()); break;
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
        return new BlockPos(x, y, z);
    }

    private static int offset(double value) {
        return value < 0.5D ? -1 : 0;
    }

    public static BlockPos offsetAlongAxis(BlockPos origin, VentAxis axis) {
        return offsetAlongAxis(origin, axis, 1);
    }

    public static BlockPos offsetAlongAxis(BlockPos origin, VentAxis axis, Direction.AxisDirection direction) {
        return offsetAlongAxis(origin, axis, direction == Direction.AxisDirection.POSITIVE ? 1 : -1);
    }

    public static BlockPos offsetAlongAxis(BlockPos origin, VentAxis axis, int distance) {
        switch (axis) {
            case X: return origin.offset(distance, 0, 0);
            case Y: return origin.offset(0, distance, 0);
            case Z: return origin.offset(0, 0, distance);
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    public static BlockPos[] getCornerPositions(BlockPos origin, VentAxis axis) {
        switch (axis) {
            case X: return new BlockPos[] {origin, origin.south(), origin.above(), origin.above().south()};
            case Y: return new BlockPos[] {origin, origin.east(), origin.south(), origin.east().south()};
            case Z: return new BlockPos[] {origin, origin.east(), origin.above(), origin.above().east()};
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    public static ConnectionEndpoints getConnectionEndpoints(BlockPos origin, VentAxis axis, int length) {
        int x = origin.getX() * 2, y = origin.getY() * 2, z = origin.getZ() * 2;
        switch (axis) {
            case X: return new ConnectionEndpoints(new BlockPos(x, y + 2, z + 2), new BlockPos(x + length * 2, y + 2, z + 2));
            case Y: return new ConnectionEndpoints(new BlockPos(x + 2, y, z + 2), new BlockPos(x + 2, y + length * 2, z + 2));
            case Z: return new ConnectionEndpoints(new BlockPos(x + 2, y + 2, z), new BlockPos(x + 2, y + 2, z + length * 2));
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    public static BlockPos getFaceConnectionCoordinate(BlockPos origin, Direction face, int size) {
        int x = origin.getX() * 2, y = origin.getY() * 2, z = origin.getZ() * 2;
        switch (face) {
            case WEST: return new BlockPos(x, y + 2, z + 2);
            case EAST: return new BlockPos(x + size * 2, y + 2, z + 2);
            case DOWN: return new BlockPos(x + 2, y, z + 2);
            case UP: return new BlockPos(x + 2, y + size * 2, z + 2);
            case NORTH: return new BlockPos(x + 2, y + 2, z);
            case SOUTH: return new BlockPos(x + 2, y + 2, z + size * 2);
            default: throw new IllegalArgumentException("Unsupported direction: " + face);
        }
    }

    public static VentCorner getCorner(BlockPos origin, BlockPos position, VentAxis axis) {
        int dx = position.getX() - origin.getX(), dy = position.getY() - origin.getY(), dz = position.getZ() - origin.getZ();
        switch (axis) {
            case X: return dy == 0 ? (dz == 0 ? VentCorner.BOTTOM_LEFT : VentCorner.BOTTOM_RIGHT) : (dz == 0 ? VentCorner.TOP_LEFT : VentCorner.TOP_RIGHT);
            case Y: return dz == 0 ? (dx == 0 ? VentCorner.TOP_LEFT : VentCorner.TOP_RIGHT) : (dx == 0 ? VentCorner.BOTTOM_LEFT : VentCorner.BOTTOM_RIGHT);
            case Z: return dy == 0 ? (dx == 0 ? VentCorner.BOTTOM_LEFT : VentCorner.BOTTOM_RIGHT) : (dx == 0 ? VentCorner.TOP_LEFT : VentCorner.TOP_RIGHT);
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    private static int getAxisCoordinate(BlockPos position, VentAxis axis) {
        switch (axis) {
            case X: return position.getX();
            case Y: return position.getY();
            case Z: return position.getZ();
            default: throw new IllegalArgumentException("Unsupported vent axis: " + axis);
        }
    }

    private static final class EdgeBounds {
        private final BlockPos min, max;
        private EdgeBounds(BlockPos min, BlockPos max) {
            this.min = min;
            this.max = max;
        }

        private static EdgeBounds of(Collection<BlockPos> blocks) {
            if (blocks.isEmpty()) throw new IllegalArgumentException("Vent edge has no physical blocks.");
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos block : blocks) {
                minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
                maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
            }
            return new EdgeBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }
    }

    private static final class ConnectionState {
        private final VentConnection connected, open;
        private ConnectionState(VentConnection connected, VentConnection open) {
            this.connected = connected;
            this.open = open;
        }
    }

    private static final class AlignedPlacement {
        private final VentAxis axis;
        private final BlockPos origin;
        private final Direction direction;
        private AlignedPlacement(VentAxis axis, BlockPos origin, Direction direction) {
            this.axis = axis;
            this.origin = origin;
            this.direction = direction;
        }
    }

    private static final class FreePlacement {
        private final VentAxis axis;
        private final BlockPos origin;
        private final Direction outward;
        private FreePlacement(VentAxis axis, BlockPos origin, Direction outward) {
            this.axis = axis;
            this.origin = origin;
            this.outward = outward;
        }
    }

    public static final class ConnectionEndpoints {
        private final BlockPos a, b;
        public ConnectionEndpoints(BlockPos a, BlockPos b) {
            this.a = a;
            this.b = b;
        }

        public BlockPos getA() {
            return a;
        }

        public BlockPos getB() {
            return b;
        }
    }
}
