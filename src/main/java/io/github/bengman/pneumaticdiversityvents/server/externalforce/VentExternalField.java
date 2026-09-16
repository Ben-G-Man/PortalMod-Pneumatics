/* Evaluates one square-frustum suction or exhaust field in world space. */
package io.github.bengman.pneumaticdiversityvents.server.externalforce;

import io.github.bengman.pneumaticdiversityvents.server.transport.VentTransportManager;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfile;

import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VentExternalField {
    /** Intake portals get a compact capture halo around the portal plane in addition to their directional far-field cone. */
    public static final double PORTAL_NEAR_FIELD_MARGIN = 1.35D;
    public static final double PORTAL_NEAR_FIELD_DEPTH = 1.75D;
    public static final double PORTAL_NEAR_FIELD_BACK_DEPTH = 0.35D;

    private final UUID id, networkId, edgeId;
    private final Vector3d center, outward, uAxis, vAxis;
    private final Direction outwardDirection;
    private final boolean intake, portalDerived;
    private final int netForce;
    private final VentExternalFieldProfile profile;
    private final AxisAlignedBB bounds;
    private final List<Vector3d> apertures;
    private final Set<Long> ignoredBlocks;

    public VentExternalField(UUID id, UUID networkId, UUID edgeId, Vector3d center, Direction outwardDirection,
            boolean intake, int netForce, VentExternalFieldProfile profile, Collection<BlockPos> ignoredBlocks) {
        this(id, networkId, edgeId, center, outwardDirection, intake, netForce, profile, ignoredBlocks, false);
    }

    public VentExternalField(UUID id, UUID networkId, UUID edgeId, Vector3d center, Direction outwardDirection,
            boolean intake, int netForce, VentExternalFieldProfile profile, Collection<BlockPos> ignoredBlocks, boolean portalDerived) {
        this.id = id;
        this.networkId = networkId;
        this.edgeId = edgeId;
        this.center = center;
        this.outwardDirection = outwardDirection;
        this.outward = vector(outwardDirection);
        this.intake = intake;
        this.portalDerived = portalDerived;
        this.netForce = netForce;
        this.profile = profile;
        this.uAxis = basisU(outwardDirection);
        this.vAxis = basisV(outwardDirection);
        this.bounds = buildBounds();
        this.apertures = buildApertures();
        Set<Long> ignored = new HashSet<>();
        for (BlockPos block : ignoredBlocks) ignored.add(block.asLong());
        this.ignoredBlocks = Collections.unmodifiableSet(ignored);
    }

    public UUID getId() {
        return id;
    }

    public UUID getNetworkId() {
        return networkId;
    }

    public UUID getEdgeId() {
        return edgeId;
    }

    public Vector3d getCenter() {
        return center;
    }

    public Direction getOutwardDirection() {
        return outwardDirection;
    }

    public boolean isIntake() {
        return intake;
    }

    public boolean isPortalDerived() {
        return portalDerived;
    }

    public int getNetForce() {
        return netForce;
    }

    public int getForceMagnitude() {
        return Math.abs(netForce);
    }

    public VentExternalFieldProfile getProfile() {
        return profile;
    }

    public AxisAlignedBB getBounds() {
        return bounds;
    }

    public List<Vector3d> getApertures() {
        return apertures;
    }

    public Set<Long> getIgnoredBlocks() {
        return ignoredBlocks;
    }

    /* Exact square-frustum membership: half-width starts at 1 block and expands by tan(spread) per block. */
    public boolean contains(Vector3d point) {
        return containsFrustum(point) || containsPortalNearField(point);
    }

    private boolean containsFrustum(Vector3d point) {
        Vector3d relative = point.subtract(center);
        double distance = dot(relative, outward);
        if (distance < 0.0D || distance > profile.getRange()) return false;
        double halfWidth = halfWidth(distance);
        return Math.abs(dot(relative, uAxis)) <= halfWidth && Math.abs(dot(relative, vAxis)) <= halfWidth;
    }

    /** Near-field is intentionally suction-only: exhaust remains directional rather than becoming an omnidirectional blast. */
    public boolean containsPortalNearField(Vector3d point) {
        if (!portalDerived || !intake) return false;
        Vector3d relative = point.subtract(center);
        double axial = dot(relative, outward);
        if (axial < -PORTAL_NEAR_FIELD_BACK_DEPTH || axial > PORTAL_NEAR_FIELD_DEPTH) return false;
        double u = Math.abs(dot(relative, uAxis)), v = Math.abs(dot(relative, vAxis));
        double outsideU = Math.max(0.0D, u - 1.0D), outsideV = Math.max(0.0D, v - 1.0D);
        return Math.sqrt(outsideU * outsideU + outsideV * outsideV) <= PORTAL_NEAR_FIELD_MARGIN;
    }

    /* Returns this field's acceleration after aperture occlusion; callers sum all fields before mutating an entity. */
    public Vector3d getInfluence(Vector3d point, int visibleApertureMask) {
        if (visibleApertureMask == 0 || !contains(point)) return Vector3d.ZERO;
        if (!containsFrustum(point) && containsPortalNearField(point)) return getPortalNearFieldInfluence(point, visibleApertureMask);

        Vector3d relative = point.subtract(center);
        double distance = clamp(dot(relative, outward), 0.0D, profile.getRange());
        double halfWidth = halfWidth(distance);
        double lateral = Math.max(Math.abs(dot(relative, uAxis)), Math.abs(dot(relative, vAxis))) / Math.max(1.0E-6D, halfWidth);
        double distanceFactor = smooth01(1.0D - distance / profile.getRange());
        double lateralFactor = smooth01(1.0D - clamp(lateral, 0.0D, 1.0D));
        double networkScale = VentTransportManager.getTransportSpeed(netForce) / VentTransportManager.getBaseTransportSpeed();
        double magnitude = profile.getBaseAcceleration() * networkScale * distanceFactor * lateralFactor;
        if (magnitude <= 1.0E-8D) return Vector3d.ZERO;

        Vector3d sum = Vector3d.ZERO;
        int visible = 0;
        for (int i = 0; i < apertures.size(); i++) {
            if ((visibleApertureMask & (1 << i)) == 0) continue;
            visible++;
            Vector3d aperture = apertures.get(i);
            Vector3d steered;
            if (intake) {
                Vector3d target = aperture.subtract(outward.scale(0.75D));
                Vector3d toTarget = safeNormalize(target.subtract(point), outward.scale(-1.0D));
                steered = safeNormalize(outward.scale(-(1.0D - profile.getRadialSteering())).add(toTarget.scale(profile.getRadialSteering())), outward.scale(-1.0D));
            } else {
                Vector3d fromAperture = safeNormalize(point.subtract(aperture), outward);
                steered = safeNormalize(outward.scale(1.0D - profile.getRadialSteering()).add(fromAperture.scale(profile.getRadialSteering())), outward);
            }
            sum = sum.add(steered);
        }
        if (visible == 0) return Vector3d.ZERO;
        return sum.scale(magnitude / 4.0D); // Four fully visible apertures define 100% field strength.
    }

    private Vector3d getPortalNearFieldInfluence(Vector3d point, int visibleApertureMask) {
        Vector3d relative = point.subtract(center);
        double axial = dot(relative, outward);
        double u = Math.abs(dot(relative, uAxis)), v = Math.abs(dot(relative, vAxis));
        double outsideU = Math.max(0.0D, u - 1.0D), outsideV = Math.max(0.0D, v - 1.0D);
        double rimDistance = Math.sqrt(outsideU * outsideU + outsideV * outsideV);
        double lateralFactor = smooth01(1.0D - clamp(rimDistance / PORTAL_NEAR_FIELD_MARGIN, 0.0D, 1.0D));
        double axialDistance = axial < 0.0D ? -axial / PORTAL_NEAR_FIELD_BACK_DEPTH : axial / PORTAL_NEAR_FIELD_DEPTH;
        double axialFactor = 0.35D + 0.65D * smooth01(1.0D - clamp(axialDistance, 0.0D, 1.0D));
        int visible = Integer.bitCount(visibleApertureMask & 0xF);
        double visibilityFactor = visible / 4.0D;
        double networkScale = VentTransportManager.getTransportSpeed(netForce) / VentTransportManager.getBaseTransportSpeed();
        // Keep meaningful pull all the way around the immediate rim while still strengthening toward the aperture.
        double magnitude = profile.getBaseAcceleration() * networkScale * (0.38D + 0.62D * lateralFactor) * axialFactor * visibilityFactor;
        Vector3d target = center.subtract(outward.scale(0.45D)); // Aim slightly through the portal so side objects actually cross the plane.
        return safeNormalize(target.subtract(point), outward.scale(-1.0D)).scale(magnitude);
    }

    public boolean structurallyEquals(VentExternalField other) {
        if (other == null) return false;
        return id.equals(other.id) && networkId.equals(other.networkId) && edgeId.equals(other.edgeId)
                && center.equals(other.center) && outwardDirection == other.outwardDirection && intake == other.intake
                && portalDerived == other.portalDerived && sameProfile(profile, other.profile) && ignoredBlocks.equals(other.ignoredBlocks);
    }

    public boolean runtimeEquals(VentExternalField other) {
        return structurallyEquals(other) && netForce == other.netForce;
    }

    private AxisAlignedBB buildBounds() {
        double range = profile.getRange(), halfWidth = halfWidth(range);
        Vector3d end = center.add(outward.scale(range));
        double minX = Math.min(center.x, end.x), maxX = Math.max(center.x, end.x);
        double minY = Math.min(center.y, end.y), maxY = Math.max(center.y, end.y);
        double minZ = Math.min(center.z, end.z), maxZ = Math.max(center.z, end.z);
        if (outwardDirection.getAxis() != Direction.Axis.X) {
            minX -= halfWidth;
            maxX += halfWidth;
        }
        else { minX -= 0.01D; maxX += 0.01D; }

        if (outwardDirection.getAxis() != Direction.Axis.Y) {
            minY -= halfWidth;
            maxY += halfWidth;
        }
        else { minY -= 0.01D; maxY += 0.01D; }

        if (outwardDirection.getAxis() != Direction.Axis.Z) {
            minZ -= halfWidth;
            maxZ += halfWidth;
        }
        else { minZ -= 0.01D; maxZ += 0.01D; }

        if (portalDerived && intake) {
            double lateral = 1.0D + PORTAL_NEAR_FIELD_MARGIN;
            Vector3d back = center.subtract(outward.scale(PORTAL_NEAR_FIELD_BACK_DEPTH));
            Vector3d front = center.add(outward.scale(PORTAL_NEAR_FIELD_DEPTH));
            double nearMinX = Math.min(back.x, front.x), nearMaxX = Math.max(back.x, front.x);
            double nearMinY = Math.min(back.y, front.y), nearMaxY = Math.max(back.y, front.y);
            double nearMinZ = Math.min(back.z, front.z), nearMaxZ = Math.max(back.z, front.z);
            if (outwardDirection.getAxis() != Direction.Axis.X) {
                nearMinX -= lateral;
                nearMaxX += lateral;
            }

            if (outwardDirection.getAxis() != Direction.Axis.Y) {
                nearMinY -= lateral;
                nearMaxY += lateral;
            }

            if (outwardDirection.getAxis() != Direction.Axis.Z) {
                nearMinZ -= lateral;
                nearMaxZ += lateral;
            }
            minX = Math.min(minX, nearMinX); maxX = Math.max(maxX, nearMaxX);
            minY = Math.min(minY, nearMinY); maxY = Math.max(maxY, nearMaxY);
            minZ = Math.min(minZ, nearMinZ); maxZ = Math.max(maxZ, nearMaxZ);
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private List<Vector3d> buildApertures() {
        List<Vector3d> result = new ArrayList<>(4);
        result.add(center.add(uAxis.scale(-0.5D)).add(vAxis.scale(-0.5D)));
        result.add(center.add(uAxis.scale( 0.5D)).add(vAxis.scale(-0.5D)));
        result.add(center.add(uAxis.scale(-0.5D)).add(vAxis.scale( 0.5D)));
        result.add(center.add(uAxis.scale( 0.5D)).add(vAxis.scale( 0.5D)));
        return Collections.unmodifiableList(result);
    }

    private double halfWidth(double distance) {
        return 1.0D + Math.max(0.0D, distance) * Math.tan(profile.getSpreadRadians());
    }

    private static boolean sameProfile(VentExternalFieldProfile a, VentExternalFieldProfile b) {
        return Double.compare(a.getRange(), b.getRange()) == 0 && Double.compare(a.getSpreadDegrees(), b.getSpreadDegrees()) == 0
                && Double.compare(a.getBaseAcceleration(), b.getBaseAcceleration()) == 0
                && Double.compare(a.getRadialSteering(), b.getRadialSteering()) == 0;
    }

    private static Vector3d basisU(Direction direction) {
        switch (direction.getAxis()) {
            case X: return new Vector3d(0, 1, 0);
            case Y: return new Vector3d(1, 0, 0);
            case Z: return new Vector3d(1, 0, 0);
            default: throw new IllegalArgumentException("Unsupported direction axis.");
        }
    }

    private static Vector3d basisV(Direction direction) {
        switch (direction.getAxis()) {
            case X: return new Vector3d(0, 0, 1);
            case Y: return new Vector3d(0, 0, 1);
            case Z: return new Vector3d(0, 1, 0);
            default: throw new IllegalArgumentException("Unsupported direction axis.");
        }
    }

    private static Vector3d vector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Vector3d safeNormalize(Vector3d vector, Vector3d fallback) {
        return vector.lengthSqr() < 1.0E-10D ? fallback : vector.normalize();
    }

    private static double dot(Vector3d a, Vector3d b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double smooth01(double value) {
        double t = clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }
}
