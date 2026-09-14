package io.github.bengman.pneumaticdiversityvents.server.transport;

import io.github.bengman.pneumaticdiversityvents.networking.VentInspectionPacket;
import io.github.bengman.pneumaticdiversityvents.networking.VentNetworkChannel;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentNetwork;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.items.WrenchItem;

/** Builds the wrench-only HUD snapshot from authoritative network/path state. */
public final class VentInspectionManager {
    private static final double REACH = 6.0D;
    private VentInspectionManager() {
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        for (ServerPlayerEntity player : world.players()) {
            // No inspection traffic at all unless the player is actively using the wrench.
            if (!WrenchItem.holdingWrench(player)) continue;
            VentNetworkChannel.sendInspection(player, inspect(player, registry));
        }
    }

    private static VentInspectionPacket inspect(ServerPlayerEntity player, VentSpatialRegistry registry) {
        RayTraceResult hit = player.pick(REACH, 1.0F, false);
        if (!(hit instanceof BlockRayTraceResult)) return VentInspectionPacket.hidden();
        VentEdge edge = registry.getEdge(((BlockRayTraceResult) hit).getBlockPos());
        if (edge == null) return VentInspectionPacket.hidden();
        VentNetwork network = edge.getNetwork();
        if (network == null) return VentInspectionPacket.hidden();
        if (network.isBlocked()) return VentInspectionPacket.blocked();
        if (network.getNetForce() == 0) return VentInspectionPacket.noFlow();

        VentPath path;
        try { path = VentPath.of(registry, edge); }

        catch (RuntimeException ignored) { return VentInspectionPacket.hidden(); }
        Vector3d hitPosition = hit.getLocation();
        VentPath.Projection projection = path.project(hitPosition);
        boolean aToB = VentTransportManager.flowAToB(edge, network.getNetForce());
        Vector3d direction = path.tangent(projection.getDistance(), aToB);
        double speed = VentTransportManager.getTransportSpeed(network.getNetForce()) * 20.0D;
        boolean playerCapable = Math.abs(network.getNetForce()) >= VentTransportManager.PLAYER_FORCE_THRESHOLD;
        return new VentInspectionPacket(true, direction, speed, playerCapable);
    }
}
