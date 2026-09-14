package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.integration.VentAntlineConnections;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.world.utils.VentPlacementUtil;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBranch;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionVisualState;
import io.github.bengman.pneumaticdiversityvents.shared.world.VentAxis;

import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.antline.indicator.IndicatorActivated;
import net.portalmod.common.sorted.antline.indicator.IndicatorInfo;
import net.portalmod.core.init.SoundInit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Owns junction control state and performs route changes as deliberately-naive graph replacement events. */
public final class VentJunctionManager {
    private VentJunctionManager() {
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        List<UUID> ids = new ArrayList<>();
        for (VentEdge edge : registry.getNetworkManager().getEdges()) if (sample(world, registry.getBlocks(edge.getId())) != null) ids.add(edge.getId());
        for (UUID edgeId : ids) syncPower(world, edgeId);
    }

    public static boolean toggleDefaultRoute(ServerWorld world, BlockPos pos) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(pos);
        if (edge == null) return false;
        BlockState state = sample(world, registry.getBlocks(edge.getId()));
        if (state == null) return false;
        List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edge.getId()));
        VentJunctionVisualState next = state.getValue(VentJunctionBlock.VISUAL).toggleDefaultRoute();
        setVisual(world, blocks, next);
        replaceTopology(world, edge.getId(), next.isTurnActive());
        playRouteSound(world, blocks, next.isTurnActive());
        return true;
    }

    public static boolean toggleFixedSide(ServerWorld world, BlockPos pos) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(pos);
        if (edge == null) return false;
        List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edge.getId()));
        BlockState sample = sample(world, blocks);
        if (sample == null) return false;
        boolean nextFixed = !sample.getValue(VentJunctionBlock.FIXED_POSITIVE);
        for (BlockPos block : blocks) {
            BlockState state = world.getBlockState(block);
            if (state.getBlock() instanceof VentJunctionBlock) world.setBlock(block, state.setValue(VentJunctionBlock.FIXED_POSITIVE, nextFixed), 3);
        }

        if (sample.getValue(VentJunctionBlock.VISUAL).isTurnActive()) {
            replaceTopology(world, edge.getId(), true);
            playRouteSound(world, blocks, true);
        }
        return true;
    }

    /** Used by shift-placement on an empty alternate face. */
    public static boolean reorientBranch(ServerWorld world, UUID edgeId, Direction targetDirection) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(edgeId);
        if (edge == null) return false;
        List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edgeId));
        BlockState state = sample(world, blocks);
        if (state == null) return false;
        VentAxis axis = state.getValue(VentBlock.AXIS);
        if (targetDirection.getAxis() == axis.getMinecraftAxis()) return false;
        VentJunctionVisualState visual = state.getValue(VentJunctionBlock.VISUAL);
        if (visual.isTurnActive() && edge.getB().isConnected()) return false;
        VentJunctionBranch next;
        try { next = VentJunctionBranch.fromDirection(axis, targetDirection); }

        catch (IllegalArgumentException ignored) { return false; }
        if (next == state.getValue(VentJunctionBlock.BRANCH)) return true;
        for (BlockPos block : blocks) {
            BlockState blockState = world.getBlockState(block);
            if (blockState.getBlock() instanceof VentJunctionBlock) world.setBlock(block, blockState.setValue(VentJunctionBlock.BRANCH, next), 3);
        }

        if (visual.isTurnActive()) {
            replaceTopology(world, edgeId, true);
            playRouteSound(world, blocks, true);
        }
        return true;
    }

    private static void syncPower(ServerWorld world, UUID edgeId) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge edge = registry.getEdge(edgeId);
        if (edge == null) return;
        List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edgeId));
        BlockState sample = sample(world, blocks);
        if (sample == null) return;
        IndicatorInfo info = IndicatorActivated.checkPositions(world, VentAntlineConnections.candidatePositions(blocks));
        boolean powered = info.hasIndicators && info.allIndicatorsActivated;
        VentJunctionVisualState current = sample.getValue(VentJunctionBlock.VISUAL);
        if (current.isPowered() == powered) return;
        VentJunctionVisualState next = current.withPowered(powered);
        setVisual(world, blocks, next);
        replaceTopology(world, edgeId, next.isTurnActive());
        playRouteSound(world, blocks, next.isTurnActive());
    }

    private static UUID replaceTopology(ServerWorld world, UUID edgeId, boolean turnActive) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentEdge old = registry.getEdge(edgeId);
        if (old == null) return null;
        List<BlockPos> blocks = new ArrayList<>(registry.getBlocks(edgeId));
        BlockState state = sample(world, blocks);
        if (state == null) return null;
        BlockPos origin = min(blocks);
        VentAxis axis = state.getValue(VentBlock.AXIS);
        VentJunctionBranch branch = state.getValue(VentJunctionBlock.BRANCH);
        boolean fixedPositive = state.getValue(VentJunctionBlock.FIXED_POSITIVE);
        registry.unregister(edgeId);
        VentEdge replacement = new VentEdge();
        registry.register(replacement, blocks, connectionsFor(replacement, origin, axis, branch, fixedPositive, turnActive));
        return replacement.getId();
    }

    static List<WorldVentConnection> connectionsFor(VentEdge edge, BlockPos origin, VentAxis axis, VentJunctionBranch branch,
            boolean fixedPositive, boolean turnActive) {
        Direction fixed = axisDirection(axis, fixedPositive);
        Direction alternate = turnActive ? branch.resolve(axis) : fixed.getOpposite();
        return Arrays.asList(
                new WorldVentConnection(edge.getA().getId(), VentPlacementUtil.getFaceConnectionCoordinate(origin, fixed, 2), VentAxis.fromDirection(fixed)),
                new WorldVentConnection(edge.getB().getId(), VentPlacementUtil.getFaceConnectionCoordinate(origin, alternate, 2), VentAxis.fromDirection(alternate)));
    }

    private static void setVisual(ServerWorld world, Collection<BlockPos> blocks, VentJunctionVisualState visual) {
        for (BlockPos block : blocks) {
            BlockState state = world.getBlockState(block);
            if (state.getBlock() instanceof VentJunctionBlock && state.getValue(VentJunctionBlock.VISUAL) != visual)
                world.setBlock(block, state.setValue(VentJunctionBlock.VISUAL, visual), 3);
        }
    }

    private static BlockState sample(ServerWorld world, Collection<BlockPos> blocks) {
        for (BlockPos block : blocks) {
            BlockState state = world.getBlockState(block);
            if (state.getBlock() instanceof VentJunctionBlock) return state;
        }
        return null;
    }

    private static BlockPos min(Collection<BlockPos> blocks) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            x = Math.min(x, p.getX());
            y = Math.min(y, p.getY());
            z = Math.min(z, p.getZ());
        }
        return new BlockPos(x, y, z);
    }

    private static Direction axisDirection(VentAxis axis, boolean positive) {
        switch (axis) {
            case X: return positive ? Direction.EAST : Direction.WEST;
            case Y: return positive ? Direction.UP : Direction.DOWN;
            case Z: return positive ? Direction.SOUTH : Direction.NORTH;
            default: throw new IllegalArgumentException("Unsupported axis: " + axis);
        }
    }

    private static void playRouteSound(ServerWorld world, Collection<BlockPos> blocks, boolean turnActive) {
        if (blocks == null || blocks.isEmpty()) return;
        world.playSound(null, blocks.iterator().next(), (turnActive ? SoundInit.CUBE_DROPPER_OPEN : SoundInit.CUBE_DROPPER_CLOSE).get(),
                SoundCategory.BLOCKS, 0.55F, 1.0F);
    }


}
