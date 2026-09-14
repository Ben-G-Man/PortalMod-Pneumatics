package io.github.bengman.pneumaticdiversityvents.networking;

import io.github.bengman.pneumaticdiversityvents.client.VentInspectionClient;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** One-player, one-tick wrench inspection snapshot. */
public final class VentInspectionPacket {
    private final boolean visible;
    private final Vector3d direction;
    private final double blocksPerSecond;
    private final boolean playerCapable;
    private final boolean blocked;
    private final boolean noFlow;

    public VentInspectionPacket(boolean visible, Vector3d direction, double blocksPerSecond, boolean playerCapable) {
        this(visible, direction, blocksPerSecond, playerCapable, false, false);
    }

    public VentInspectionPacket(boolean visible, Vector3d direction, double blocksPerSecond, boolean playerCapable, boolean blocked, boolean noFlow) {
        this.visible = visible;
        this.direction = direction == null ? Vector3d.ZERO : direction;
        this.blocksPerSecond = blocksPerSecond;
        this.playerCapable = playerCapable;
        this.blocked = blocked;
        this.noFlow = noFlow;
    }

    public static VentInspectionPacket hidden() {
        return new VentInspectionPacket(false, Vector3d.ZERO, 0.0D, false, false, false);
    }

    public static VentInspectionPacket blocked() {
        return new VentInspectionPacket(true, Vector3d.ZERO, 0.0D, false, true, false);
    }

    public static VentInspectionPacket noFlow() {
        return new VentInspectionPacket(true, Vector3d.ZERO, 0.0D, false, false, true);
    }

    public static void encode(VentInspectionPacket packet, PacketBuffer buffer) {
        buffer.writeBoolean(packet.visible);
        if (!packet.visible) return;
        buffer.writeBoolean(packet.blocked);
        if (packet.blocked) return;
        buffer.writeBoolean(packet.noFlow);
        if (packet.noFlow) return;
        buffer.writeDouble(packet.direction.x); buffer.writeDouble(packet.direction.y); buffer.writeDouble(packet.direction.z);
        buffer.writeDouble(packet.blocksPerSecond);
        buffer.writeBoolean(packet.playerCapable);
    }

    public static VentInspectionPacket decode(PacketBuffer buffer) {
        if (!buffer.readBoolean()) return hidden();
        if (buffer.readBoolean()) return blocked();
        if (buffer.readBoolean()) return noFlow();
        return new VentInspectionPacket(true, new Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readDouble(), buffer.readBoolean(), false, false);
    }

    public static void handle(VentInspectionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VentInspectionClient.accept(packet)));
        context.setPacketHandled(true);
    }

    public boolean isVisible() {
        return visible;
    }

    public Vector3d getDirection() {
        return direction;
    }

    public double getBlocksPerSecond() {
        return blocksPerSecond;
    }

    public boolean isPlayerCapable() {
        return playerCapable;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isNoFlow() {
        return noFlow;
    }
}
