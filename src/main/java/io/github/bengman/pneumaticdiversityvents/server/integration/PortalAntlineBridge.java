package io.github.bengman.pneumaticdiversityvents.server.integration;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.antline.AntlineBlock;
import net.portalmod.common.sorted.antline.AntlineTileEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PortalAntlineBridge {
    private static final Map<ServerWorld, Map<UUID, Set<BlockPos>>> ACTIVE_SOURCES = new WeakHashMap<>();
    private PortalAntlineBridge() {
    }

    public static void setActive(ServerWorld world, UUID sourceId, Collection<BlockPos> sourceBlocks, boolean active) {
        Set<BlockPos> antlines = findAntlines(world, sourceBlocks);
        Map<UUID, Set<BlockPos>> sources = ACTIVE_SOURCES.computeIfAbsent(world, ignored -> new HashMap<>());
        if (active) sources.put(sourceId, antlines);
        else sources.remove(sourceId);

        for (BlockPos pos : antlines) propagate(world, pos, active || isDrivenByAnotherSource(sources, pos));
        if (sources.isEmpty()) ACTIVE_SOURCES.remove(world);
    }

    public static void onWorldUnload(ServerWorld world) {
        ACTIVE_SOURCES.remove(world);
    }

    private static Set<BlockPos> findAntlines(ServerWorld world, Collection<BlockPos> sourceBlocks) {
        Set<BlockPos> antlines = new LinkedHashSet<>();
        for (BlockPos candidate : VentAntlineConnections.candidatePositions(sourceBlocks))
            if (world.getBlockState(candidate).getBlock() instanceof AntlineBlock) antlines.add(candidate);
        return antlines;
    }

    private static boolean isDrivenByAnotherSource(Map<UUID, Set<BlockPos>> sources, BlockPos pos) {
        for (Set<BlockPos> positions : sources.values()) if (positions.contains(pos)) return true;
        return false;
    }

    private static void propagate(ServerWorld world, BlockPos pos, boolean active) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        TileEntity tile = world.getBlockEntity(pos);
        if (!(block instanceof AntlineBlock) || !(tile instanceof AntlineTileEntity)) return;
        AntlineBlock antline = (AntlineBlock) block;
        AntlineTileEntity antlineTile = (AntlineTileEntity) tile;
        for (AntlineTileEntity.Side side : antlineTile.getSideMap().values()) {
            if (side == null || side.isEmpty()) continue;
            antline.recursiveSignalChain(world, side, pos, null, active, 0);
        }
    }
}
