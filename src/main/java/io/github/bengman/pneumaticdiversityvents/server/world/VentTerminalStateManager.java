package io.github.bengman.pneumaticdiversityvents.server.world;

import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentNetwork;
import io.github.bengman.pneumaticdiversityvents.server.transport.VentTransportManager;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalVisualState;

import net.minecraft.block.BlockState;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.core.init.SoundInit;

import java.util.List;

public final class VentTerminalStateManager {
    private VentTerminalStateManager() {
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        for (VentEdge edge : registry.getNetworkManager().getEdges()) {
            List<BlockPos> blocks = registry.getBlocks(edge.getId());
            BlockState sample = null;
            for (BlockPos block : blocks) {
                BlockState state = world.getBlockState(block);
                if (state.getBlock() instanceof VentTerminalBlock) {
                    sample = state;
                    break;
                }
            }
            if (sample == null) continue;

            VentTerminalVisualState current = sample.getValue(VentTerminalBlock.VISUAL);
            VentTerminalVisualState next = resolve(current, edge.getNetwork());
            if (next == current) continue;
            for (BlockPos block : blocks) {
                BlockState state = world.getBlockState(block);
                if (state.getBlock() instanceof VentTerminalBlock && state.hasProperty(VentTerminalBlock.VISUAL))
                    world.setBlock(block, state.setValue(VentTerminalBlock.VISUAL, next), 3);
            }
            if (!blocks.isEmpty()) playTransitionSound(world, blocks.get(0), current, next);
        }
    }
    /** Resolves only the lamp member; the selected indication mode itself is preserved. */
    public static VentTerminalVisualState resolve(VentTerminalVisualState state, VentNetwork network) {
        boolean lit;
        switch (state.getMode()) {
            case FORCE:
                lit = network != null && network.hasActiveForceField() && network.getNetForce() != 0;
                break;
            case PLAYER:
                lit = network != null && network.hasActiveForceField()
                        && VentTransportManager.canTransportPlayer(network.getNetForce());
                break;
            default:
                lit = false;
        }
        return state.withLit(lit);
    }

    public static void playTransitionSound(ServerWorld world, BlockPos pos, VentTerminalVisualState from, VentTerminalVisualState to) {
        if (from.isLit() == to.isLit()) return;
        world.playSound(null, pos, (to.isLit() ? SoundInit.ANTLINE_INDICATOR_ACTIVATE : SoundInit.ANTLINE_INDICATOR_DEACTIVATE).get(),
                SoundCategory.BLOCKS, 0.45F, 1.0F);
    }

}
