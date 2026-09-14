package io.github.bengman.pneumaticdiversityvents.networking;

import io.github.bengman.pneumaticdiversityvents.client.VentAmbientSoundClient;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.externalforce.VentExternalFieldManager;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class VentAmbientSoundPacket {
    private final List<NetworkSound> networks;
    private final List<ImpellerSound> impellers;
    private final List<PortalSound> portals;

    public VentAmbientSoundPacket(List<NetworkSound> networks, List<ImpellerSound> impellers, List<PortalSound> portals) {
        this.networks = Collections.unmodifiableList(new ArrayList<>(networks));
        this.impellers = Collections.unmodifiableList(new ArrayList<>(impellers));
        this.portals = Collections.unmodifiableList(new ArrayList<>(portals));
    }

    public static VentAmbientSoundPacket fromWorld(VentSpatialRegistry registry, List<VentExternalFieldManager.PortalSoundSource> portalSources) {
        List<NetworkSound> networks = new ArrayList<>();
        for (VentSpatialRegistry.ActiveNetworkSound source : registry.getActiveNetworkSounds())
            networks.add(new NetworkSound(source.getNetworkId(), source.getNetForce(), source.getSourcePoints()));
        List<ImpellerSound> impellers = new ArrayList<>();
        for (VentSpatialRegistry.ActiveImpellerSound source : registry.getActiveImpellerSounds())
            impellers.add(new ImpellerSound(source.getEdgeId(), source.getCenter(), source.isEnabled(), source.isStrained()));
        List<PortalSound> portals = new ArrayList<>();
        for (VentExternalFieldManager.PortalSoundSource source : portalSources)
            portals.add(new PortalSound(source.getId(), source.getCenter()));
        return new VentAmbientSoundPacket(networks, impellers, portals);
    }

    public static void encode(VentAmbientSoundPacket packet, PacketBuffer buffer) {
        buffer.writeVarInt(packet.networks.size());
        for (NetworkSound network : packet.networks) {
            buffer.writeUUID(network.id); buffer.writeInt(network.netForce); buffer.writeVarInt(network.points.size());
            for (Vector3d p : network.points) writeVector(buffer, p);
        }
        buffer.writeVarInt(packet.impellers.size());
        for (ImpellerSound impeller : packet.impellers) {
            buffer.writeUUID(impeller.id); writeVector(buffer, impeller.center); buffer.writeBoolean(impeller.active); buffer.writeBoolean(impeller.strained);
        }
        buffer.writeVarInt(packet.portals.size());
        for (PortalSound portal : packet.portals) {
            buffer.writeUUID(portal.id); writeVector(buffer, portal.center);
        }
    }

    public static VentAmbientSoundPacket decode(PacketBuffer buffer) {
        int networkCount = buffer.readVarInt();
        List<NetworkSound> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            UUID id = buffer.readUUID(); int force = buffer.readInt(); int pointCount = buffer.readVarInt();
            List<Vector3d> points = new ArrayList<>(pointCount);
            for (int j = 0; j < pointCount; j++) points.add(readVector(buffer));
            networks.add(new NetworkSound(id, force, points));
        }
        int impellerCount = buffer.readVarInt();
        List<ImpellerSound> impellers = new ArrayList<>(impellerCount);
        for (int i = 0; i < impellerCount; i++) impellers.add(new ImpellerSound(buffer.readUUID(), readVector(buffer), buffer.readBoolean(), buffer.readBoolean()));
        int portalCount = buffer.readVarInt();
        List<PortalSound> portals = new ArrayList<>(portalCount);
        for (int i = 0; i < portalCount; i++) portals.add(new PortalSound(buffer.readUUID(), readVector(buffer)));
        return new VentAmbientSoundPacket(networks, impellers, portals);
    }

    public static void handle(VentAmbientSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VentAmbientSoundClient.accept(packet.networks, packet.impellers, packet.portals)));
        context.setPacketHandled(true);
    }

    private static void writeVector(PacketBuffer buffer, Vector3d p) {
        buffer.writeDouble(p.x);
        buffer.writeDouble(p.y);
        buffer.writeDouble(p.z);
    }

    private static Vector3d readVector(PacketBuffer buffer) {
        return new Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static final class NetworkSound {
        private final UUID id; private final int netForce; private final List<Vector3d> points;
        public NetworkSound(UUID id, int netForce, List<Vector3d> points) {
            this.id = id;
            this.netForce = netForce;
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
        }

        public UUID getId() { return id; } public int getNetForce() { return netForce; } public List<Vector3d> getPoints() { return points; }
    }

    public static final class ImpellerSound {
        private final UUID id; private final Vector3d center; private final boolean active, strained;
        public ImpellerSound(UUID id, Vector3d center, boolean active, boolean strained) {
            this.id = id; this.center = center; this.active = active; this.strained = strained;
        }

        public UUID getId() {
            return id;
        }

        public Vector3d getCenter() {
            return center;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isStrained() {
            return strained;
        }
    }

    public static final class PortalSound {
        private final UUID id; private final Vector3d center;
        public PortalSound(UUID id, Vector3d center) {
            this.id = id;
            this.center = center;
        }

        public UUID getId() { return id; } public Vector3d getCenter() { return center; }
    }
}
