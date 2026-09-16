/* Adapts authoritative vent fields into paired PortalMod force fields without owning any portal state. */
package io.github.bengman.pneumaticdiversityvents.server.externalforce;

import io.github.bengman.pneumaticdiversityvents.server.transport.VentTransportManager;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfile;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentOcclusionTester;

import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.portal.PortalEntity;
import net.portalmod.core.math.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PortalVentBridge {
    private static final double PORTAL_SPREAD_DEGREES = 30.0D;
    private static final double PORTAL_MIN_RANGE = 2.5D;
    private static final double PORTAL_MAX_RANGE = 6.0D;
    private static final double PORTAL_CENTER_OFFSET = 0.08D;
    private static final double MIN_TRANSMITTED_ACCELERATION = 0.0025D;

    private PortalVentBridge() {
    }

    /*
     * Derive destination-side fields from ordinary vent fields only. Derived fields are never fed back through this
     * method, preventing portal-pair feedback loops while still letting PortalMod move entities through the pair.
     */
    public static void addPortalFields(ServerWorld world, Map<UUID, VentExternalField> fields) {
        List<VentExternalField> sources = new ArrayList<>(fields.values());
        for (VentExternalField source : sources) {
            if (source.isPortalDerived() || source.getNetForce() == 0) continue;
            List<PortalEntity> portals = PortalEntity.getOpenPortals(world, source.getBounds(), portal -> portal.level == world && portal.getOtherPortal().isPresent());
            for (PortalEntity sourcePortal : portals) addPortalField(world, source, sourcePortal, fields);
        }
    }

    private static void addPortalField(ServerWorld world, VentExternalField source, PortalEntity sourcePortal,
            Map<UUID, VentExternalField> fields) {
        Optional<PortalEntity> targetOptional = sourcePortal.getOtherPortal();
        if (!targetOptional.isPresent()) return;
        PortalEntity targetPortal = targetOptional.get();
        // PortalMod portal-gun pairs are normally same-dimension. Keep this world-owned manager strictly dimension-local.
        if (targetPortal.level != world || !targetPortal.isOpen()) return;

        Vector3d sourceCenter = portalCenter(sourcePortal);
        Vector3d sourceNormal = new Vec3(sourcePortal.getNormal()).to3d().normalize();
        int visibility = sourceVisibility(world, source, sourcePortal, sourceCenter);
        Vector3d localForce = source.getInfluence(sourceCenter, visibility);
        if (localForce.lengthSqr() < 1.0E-12D) return;

        // Only force perpendicular to the portal surface can be carried through it.
        double sourceNormalForce = dot(localForce, sourceNormal);
        if (Math.abs(sourceNormalForce) < MIN_TRANSMITTED_ACCELERATION) return;
        Vector3d transmittedSourceVector = sourceNormal.scale(sourceNormalForce);
        Vector3d transformed = sourcePortal.teleportVector(new Vec3(transmittedSourceVector)).to3d();

        Vector3d targetNormal = new Vec3(targetPortal.getNormal()).to3d().normalize();
        double targetNormalForce = dot(transformed, targetNormal);
        double transmittedAcceleration = Math.abs(targetNormalForce);
        if (transmittedAcceleration < MIN_TRANSMITTED_ACCELERATION) return;

        boolean intake = targetNormalForce < 0.0D;
        Direction outwardDirection = directionFromNormal(targetNormal);
        double networkScale = VentTransportManager.getTransportSpeed(source.getNetForce()) / VentTransportManager.getBaseTransportSpeed();
        if (networkScale <= 1.0E-8D) return;

        double sourceMouthAcceleration = source.getProfile().getBaseAcceleration() * networkScale;
        double localFraction = sourceMouthAcceleration <= 1.0E-8D ? 0.0D
                : clamp(transmittedAcceleration / sourceMouthAcceleration, 0.0D, 1.0D);
        double range = PORTAL_MIN_RANGE + (PORTAL_MAX_RANGE - PORTAL_MIN_RANGE) * Math.sqrt(localFraction);
        // VentExternalField applies networkScale itself, so divide it out to preserve the actual sampled acceleration.
        double baseAcceleration = transmittedAcceleration / networkScale;
        double radialSteering = intake ? 0.62D : 0.38D;
        VentExternalFieldProfile profile = new VentExternalFieldProfile(range, PORTAL_SPREAD_DEGREES, baseAcceleration, radialSteering);

        Vector3d targetCenter = portalCenter(targetPortal).add(targetNormal.scale(PORTAL_CENTER_OFFSET));
        Set<BlockPos> ignoredBlocks = new HashSet<>(targetPortal.getBlocksBehind());
        UUID id = portalFieldId(source, sourcePortal, targetPortal);
        fields.put(id, new VentExternalField(id, source.getNetworkId(), source.getEdgeId(), targetCenter, outwardDirection,
                intake, source.getNetForce(), profile, ignoredBlocks, true));
    }

    private static int sourceVisibility(ServerWorld world, VentExternalField source, PortalEntity portal, Vector3d portalCenter) {
        Set<Long> ignored = new HashSet<>(source.getIgnoredBlocks());
        for (BlockPos pos : portal.getBlocksBehind()) ignored.add(pos.asLong());
        int mask = 0;
        List<Vector3d> apertures = source.getApertures();
        for (int i = 0; i < apertures.size(); i++) {
            if (VentOcclusionTester.isClear(world, null, portalCenter, apertures.get(i), ignored)) mask |= 1 << i;
        }
        return mask;
    }

    private static Vector3d portalCenter(PortalEntity portal) {
        AxisAlignedBB box = portal.getBoundingBox();
        return new Vector3d((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
    }

    private static UUID portalFieldId(VentExternalField source, PortalEntity sourcePortal, PortalEntity targetPortal) {
        String key = "portal-force:" + source.getId() + ':' + sourcePortal.getUUID() + ':' + targetPortal.getUUID();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static Direction directionFromNormal(Vector3d normal) {
        double ax = Math.abs(normal.x), ay = Math.abs(normal.y), az = Math.abs(normal.z);
        if (ax >= ay && ax >= az) return normal.x >= 0.0D ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return normal.y >= 0.0D ? Direction.UP : Direction.DOWN;
        return normal.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static double dot(Vector3d a, Vector3d b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
