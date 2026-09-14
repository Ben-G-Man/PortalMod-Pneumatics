package io.github.bengman.pneumaticdiversityvents.server.world.utils;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.shared.EncasedVentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentScannerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalBlock;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.UUID;

public final class VentDropUtil {
    private VentDropUtil() {
    }

    public static void dropEdge(ServerWorld world, UUID edgeId) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        List<BlockPos> blocks = registry.getBlocks(edgeId);
        if (blocks.isEmpty()) return;
        boolean impeller = false, terminal = false, encased = false, scanner = false, junction = false;
        for (BlockPos p : blocks) {
            Block block = world.getBlockState(p).getBlock();
            impeller |= block instanceof VentImpellerBlock;
            terminal |= block instanceof VentTerminalBlock;
            encased |= block instanceof EncasedVentBlock;
            scanner |= block instanceof VentScannerBlock;
            junction |= block instanceof VentJunctionBlock;
        }
        ItemStack stack;
        if (impeller) stack = new ItemStack(PneumaticDiversityVents.VENT_IMPELLER_ITEM.get(), 1);
        else if (terminal) stack = new ItemStack(PneumaticDiversityVents.VENT_TERMINAL_ITEM.get(), 1);
        else if (encased) stack = new ItemStack(PneumaticDiversityVents.VENT_ENCASED_ITEM.get(), 1);
        else if (scanner) stack = new ItemStack(PneumaticDiversityVents.VENT_SCANNER_ITEM.get(), 1);
        else if (junction) stack = new ItemStack(PneumaticDiversityVents.VENT_JUNCTION_ITEM.get(), 1);
        else stack = new ItemStack(PneumaticDiversityVents.VENT_SEGMENT_ITEM.get(), Math.max(1, blocks.size() / 4));
        BlockPos p = blocks.get(0);
        Block.popResource(world, p, stack);
    }
}
