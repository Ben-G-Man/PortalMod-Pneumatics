/* Owns debounced endpoint fields, cached occlusion, and order-independent external force accumulation. */
package io.github.bengman.pneumaticdiversityvents.server.externalforce;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.transport.VentTransportManager;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfile;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfileProvider;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentExternalFieldProfiles;
import io.github.bengman.pneumaticdiversityvents.shared.externalforce.VentOcclusionTester;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.HangingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.portal.PortalEntity;
import net.portalmod.common.sorted.turret.TurretEntity;
import net.portalmod.common.sorted.turret.TurretState;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class VentExternalFieldManager {
    public static final int FIELD_REBUILD_DELAY_TICKS = 40;
    public static final int BROADPHASE_INTERVAL_TICKS = 2;
    public static final int OCCLUSION_INTERVAL_TICKS = 4;
    private static final int PARTICLE_INTERVAL_TICKS = 2;
    private static final double PARTICLES_PER_FIELD_PER_EMISSION = 0.75D;
    private static final double PORTAL_PARTICLE_DENSITY = 0.65D;
    private static final double PARTICLE_BOUNDARY_CHANCE = 0.32D;
    private static final double PARTICLE_VIEW_DISTANCE = 64.0D;
    private static final double PARTICLE_SPEED_MULTIPLIER = 1.8D;

    private static final Random PARTICLE_RANDOM = new Random();
    private static final Map<ServerWorld, WorldState> WORLDS = new WeakHashMap<>();

    private VentExternalFieldManager() {
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        registry.prepareForceState(world);
        WorldState state = WORLDS.computeIfAbsent(world, ignored -> new WorldState());
        state.syncFields(world, registry);
        spawnForceParticles(world, state.getVisualFields());
        state.tickPhysics(world);
    }

    public static void onWorldUnload(ServerWorld world) {
        WORLDS.remove(world);
    }

    public static java.util.List<PortalSoundSource> getPortalSoundSources(ServerWorld world) {
        WorldState state = WORLDS.get(world);
        if (state == null) return java.util.Collections.emptyList();
        java.util.List<PortalSoundSource> result = new java.util.ArrayList<>();
        for (VentExternalField field : state.getVisualFields()) {
            if (!field.isPortalDerived() || field.getNetForce() == 0) continue;
            result.add(new PortalSoundSource(field.getId(), field.getCenter()));
        }
        return java.util.Collections.unmodifiableList(result);
    }


    /* Server-authored particles use the exact desired fields, including portal-derived fields, with no client cache. */
    private static void spawnForceParticles(ServerWorld world, Collection<VentExternalField> fields) {
        if (world.getGameTime() % PARTICLE_INTERVAL_TICKS != 0) return;
        for (VentExternalField field : fields) {
            if (field.getNetForce() == 0) continue;
            double expected = PARTICLES_PER_FIELD_PER_EMISSION * (field.isPortalDerived() ? PORTAL_PARTICLE_DENSITY : 1.0D);
            int count = (int) expected;
            if (PARTICLE_RANDOM.nextDouble() < expected - count) count++;
            double speedScale = particleSpeedScale(field.getNetForce());
            for (int i = 0; i < count; i++) {
                ParticleSample sample = sampleParticle(field, speedScale);
                for (ServerPlayerEntity player : world.players()) {
                    if (player.position().distanceToSqr(sample.position) > PARTICLE_VIEW_DISTANCE * PARTICLE_VIEW_DISTANCE) continue;
                    world.sendParticles(player, PneumaticDiversityVents.VENT_SMOKE_PARTICLE.get(), true, sample.position.x, sample.position.y, sample.position.z,
                            0, sample.velocity.x, sample.velocity.y, sample.velocity.z, 1.0D);
                }
            }
        }
    }

    private static ParticleSample sampleParticle(VentExternalField field, double speedScale) {
        Direction outwardDirection = field.getOutwardDirection();
        VentExternalFieldProfile profile = field.getProfile();
        Vector3d outward = directionVector(outwardDirection), u = basisU(outwardDirection), v = basisV(outwardDirection);

        // Portal suction spends part of its already-sparse particle budget showing the near-field capture halo around the rim.
        if (field.isPortalDerived() && field.isIntake() && PARTICLE_RANDOM.nextDouble() < 0.35D)
            return samplePortalNearFieldParticle(field, outward, u, v, speedScale);

        // Squaring-ish the random distance clusters dust near the mouth, where force is strongest, while still sampling the full cone.
        double r = PARTICLE_RANDOM.nextDouble();
        double distance = 0.15D + Math.pow(r, 1.8D) * Math.max(0.01D, profile.getRange() - 0.15D);
        double halfWidth = 1.0D + distance * Math.tan(profile.getSpreadRadians());
        double[] lateral = PARTICLE_RANDOM.nextDouble() < PARTICLE_BOUNDARY_CHANCE ? boundaryPoint(halfWidth)
                : new double[] { randomTriangular(halfWidth), randomTriangular(halfWidth) };
        Vector3d position = field.getCenter().add(outward.scale(distance)).add(u.scale(lateral[0])).add(v.scale(lateral[1]));

        Vector3d velocity;
        if (field.isIntake()) {
            Vector3d aperture = field.getCenter().add(u.scale(clamp(lateral[0], -0.75D, 0.75D))).add(v.scale(clamp(lateral[1], -0.75D, 0.75D)));
            velocity = safeNormalize(aperture.subtract(position), outward.scale(-1.0D)).scale(0.055D * speedScale);
        } else {
            double radialScale = distance < 1.0E-6D ? 0.0D : 1.0D / distance;
            Vector3d radial = u.scale(lateral[0] * radialScale).add(v.scale(lateral[1] * radialScale));
            velocity = safeNormalize(outward.add(radial.scale(Math.tan(profile.getSpreadRadians()))), outward).scale(0.062D * speedScale);
        }
        return new ParticleSample(position, velocity);
    }

    private static ParticleSample samplePortalNearFieldParticle(VentExternalField field, Vector3d outward, Vector3d u, Vector3d v,
            double speedScale) {
        double axial = randomRange(-0.10D, VentExternalField.PORTAL_NEAR_FIELD_DEPTH);
        double limit = 1.0D + VentExternalField.PORTAL_NEAR_FIELD_MARGIN;
        double lateralU = 0.0D, lateralV = 0.0D;
        for (int attempt = 0; attempt < 6; attempt++) {
            lateralU = randomRange(-limit, limit);
            lateralV = randomRange(-limit, limit);
            double outsideU = Math.max(0.0D, Math.abs(lateralU) - 1.0D);
            double outsideV = Math.max(0.0D, Math.abs(lateralV) - 1.0D);
            if (Math.sqrt(outsideU * outsideU + outsideV * outsideV) <= VentExternalField.PORTAL_NEAR_FIELD_MARGIN) break;
        }
        Vector3d position = field.getCenter().add(outward.scale(axial)).add(u.scale(lateralU)).add(v.scale(lateralV));
        Vector3d target = field.getCenter().subtract(outward.scale(0.45D));
        Vector3d velocity = safeNormalize(target.subtract(position), outward.scale(-1.0D)).scale(0.060D * speedScale);
        return new ParticleSample(position, velocity);
    }

    private static VentExternalFieldProfile profileForEndpoint(ServerWorld world, VentSpatialRegistry.OpenEndpoint endpoint) {
        Block block = world.getBlockState(endpoint.getProfileBlock()).getBlock();
        return block instanceof VentExternalFieldProfileProvider
                ? ((VentExternalFieldProfileProvider) block).getExternalFieldProfile(endpoint.isIntake())
                : (endpoint.isIntake() ? VentExternalFieldProfiles.STANDARD_INTAKE : VentExternalFieldProfiles.STANDARD_EXHAUST);
    }

    private static double particleSpeedScale(int netForce) {
        double transportScale = VentTransportManager.getTransportSpeed(netForce) / VentTransportManager.BASE_TRANSPORT_SPEED;
        return PARTICLE_SPEED_MULTIPLIER * clamp(transportScale, 0.75D, 4.0D);
    }

    private static double[] boundaryPoint(double halfWidth) {
        double along = randomRange(-halfWidth, halfWidth);
        switch (PARTICLE_RANDOM.nextInt(4)) {
            case 0: return new double[] {-halfWidth, along};
            case 1: return new double[] { halfWidth, along};
            case 2: return new double[] {along, -halfWidth};
            default: return new double[] {along, halfWidth};
        }
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

    private static Vector3d directionVector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Vector3d safeNormalize(Vector3d vector, Vector3d fallback) {
        return vector.lengthSqr() < 1.0E-10D ? fallback : vector.normalize();
    }

    private static double randomRange(double min, double max) {
        return min + PARTICLE_RANDOM.nextDouble() * (max - min);
    }

    private static double randomTriangular(double halfWidth) {
        return (PARTICLE_RANDOM.nextDouble() + PARTICLE_RANDOM.nextDouble() - 1.0D) * halfWidth;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ParticleSample {
        private final Vector3d position, velocity;
        private ParticleSample(Vector3d position, Vector3d velocity) {
            this.position = position;
            this.velocity = velocity;
        }
    }

    private static final class WorldState {
        private final Map<UUID, RuntimeField> active = new HashMap<>();
        private final Map<UUID, PendingField> pending = new HashMap<>();
        private final Map<UUID, TurretState> suctionProtectedTurrets = new HashMap<>();
        private Map<UUID, VentExternalField> visualFields = Collections.emptyMap();

        private void syncFields(ServerWorld world, VentSpatialRegistry registry) {
            long now = world.getGameTime();
            Map<UUID, VentExternalField> desired = buildDesiredFields(world, registry);
            visualFields = Collections.unmodifiableMap(new HashMap<>(desired));

            Iterator<Map.Entry<UUID, RuntimeField>> activeIterator = active.entrySet().iterator();
            while (activeIterator.hasNext()) {
                Map.Entry<UUID, RuntimeField> entry = activeIterator.next();
                VentExternalField next = desired.get(entry.getKey());
                if (next == null) {
                    activeIterator.remove(); // Invalid/open-end-occupied fields disappear immediately.
                    continue;
                }
                RuntimeField runtime = entry.getValue();
                if (!runtime.field.runtimeEquals(next)) {
                    boolean structural = !runtime.field.structurallyEquals(next);
                    runtime.replace(next, structural);
                }
            }

            pending.keySet().removeIf(id -> !desired.containsKey(id));
            for (Map.Entry<UUID, VentExternalField> entry : desired.entrySet()) {
                UUID id = entry.getKey();
                VentExternalField field = entry.getValue();
                if (active.containsKey(id)) continue;
                PendingField queued = pending.get(id);
                long activateAt = now + (field.isPortalDerived() ? 0 : FIELD_REBUILD_DELAY_TICKS);
                if (queued == null) pending.put(id, new PendingField(field, activateAt));
                else if (!queued.field.structurallyEquals(field)) pending.put(id, new PendingField(field, activateAt));
                else queued.field = field; // Force magnitude may change while the same endpoint is waiting; do not restart debounce.
            }

            Iterator<Map.Entry<UUID, PendingField>> pendingIterator = pending.entrySet().iterator();
            while (pendingIterator.hasNext()) {
                Map.Entry<UUID, PendingField> entry = pendingIterator.next();
                PendingField queued = entry.getValue();
                VentExternalField current = desired.get(entry.getKey());
                if (current == null) {
                    pendingIterator.remove();
                    continue;
                }
                if (now < queued.activateAt) continue;
                active.put(entry.getKey(), new RuntimeField(current));
                pendingIterator.remove();
            }
        }

        private Collection<VentExternalField> getVisualFields() {
            return visualFields.values();
        }

        private void tickPhysics(ServerWorld world) {
            long now = world.getGameTime();
            restoreSuctionProtectedTurrets(world);
            if (now % BROADPHASE_INTERVAL_TICKS == 0) for (RuntimeField runtime : active.values()) runtime.refreshCandidates(world);

            Map<UUID, Vector3d> accumulated = new HashMap<>();
            Map<UUID, Entity> entities = new HashMap<>();
            Set<UUID> suctionAffectedTurrets = new HashSet<>();
            for (RuntimeField runtime : active.values()) {
                VentExternalField field = runtime.field;
                for (UUID entityId : runtime.candidates) {
                    Entity entity = world.getEntity(entityId);
                    if (!eligible(entity, field) || VentTransportManager.isTransported(world, entity)) continue;
                    Vector3d center = entityCenter(entity);
                    if (!field.contains(center)) continue;
                    int visibility = runtime.getVisibility(world, entity, center, now);
                    Vector3d influence = field.getInfluence(center, visibility);
                    if (influence.lengthSqr() < 1.0E-12D) continue;
                    accumulated.merge(entityId, influence, Vector3d::add);
                    entities.put(entityId, entity);
                    if (field.isIntake() && entity instanceof TurretEntity) suctionAffectedTurrets.add(entityId);
                }
            }

            for (Map.Entry<UUID, Vector3d> entry : accumulated.entrySet()) {
                Entity entity = entities.get(entry.getKey());
                if (entity == null || entity.removed) continue;
                Vector3d force = entry.getValue(); // All field vectors are summed before any entity is mutated, so overlap is order-independent.
                if (force.lengthSqr() < 1.0E-12D) continue;
                if (entity instanceof TurretEntity && suctionAffectedTurrets.contains(entry.getKey())) {
                    TurretEntity turret = (TurretEntity) entity;
                    if (turret.getState().isStanding()) suctionProtectedTurrets.put(turret.getUUID(), turret.getState());
                }
                entity.setDeltaMovement(entity.getDeltaMovement().add(force));
                entity.hasImpulse = true; // Tell vanilla tracking that server-authored motion changed this tick.
                if (entity instanceof ServerPlayerEntity) {
                    ServerPlayerEntity player = (ServerPlayerEntity) entity;
                    player.connection.send(new SEntityVelocityPacket(player.getId(), player.getDeltaMovement()));
                }
            }
        }

        private void restoreSuctionProtectedTurrets(ServerWorld world) {
            if (suctionProtectedTurrets.isEmpty()) return;
            for (Map.Entry<UUID, TurretState> entry : suctionProtectedTurrets.entrySet()) {
                Entity entity = world.getEntity(entry.getKey());
                if (!(entity instanceof TurretEntity) || VentTransportManager.isTransported(world, entity)) continue;
                TurretEntity turret = (TurretEntity) entity;
                if (turret.getState() == TurretState.FALLING && entry.getValue().isStanding()) {
                    // PortalMod topples grounded turrets when horizontal motion is detected. Suction motion is exempt;
                    // deterministic tube capture is the only place Pneumatic Diversity Vents intentionally kills a turret.
                    turret.setState(entry.getValue());
                    turret.setAnimationTicks(0);
                    turret.refreshDimensions();
                }
            }
            suctionProtectedTurrets.clear();
        }

        private static Map<UUID, VentExternalField> buildDesiredFields(ServerWorld world, VentSpatialRegistry registry) {
            Map<UUID, VentExternalField> result = new HashMap<>();
            for (VentSpatialRegistry.OpenEndpoint endpoint : registry.getOpenForceEndpoints()) {
                VentExternalFieldProfile profile = profileForEndpoint(world, endpoint);
                result.put(endpoint.getConnectionId(), new VentExternalField(endpoint.getConnectionId(), endpoint.getNetworkId(), endpoint.getEdgeId(),
                        endpoint.getCenter(), endpoint.getOutwardDirection(), endpoint.isIntake(), endpoint.getNetForce(), profile, endpoint.getEdgeBlocks()));
            }
            PortalVentBridge.addPortalFields(world, result);
            return result;
        }
    }

    public static final class PortalSoundSource {
        private final UUID id;
        private final Vector3d center;
        private PortalSoundSource(UUID id, Vector3d center) {
            this.id = id;
            this.center = center;
        }

        public UUID getId() {
            return id;
        }

        public Vector3d getCenter() {
            return center;
        }
    }

    private static final class RuntimeField {
        private VentExternalField field;
        private final Set<UUID> candidates = new HashSet<>();
        private final Map<UUID, VisibilitySample> visibility = new HashMap<>();

        private RuntimeField(VentExternalField field) {
            this.field = field;
        }

        private void replace(VentExternalField replacement, boolean structural) {
            this.field = replacement;
            if (structural) {
                candidates.clear();
                visibility.clear();
            }
        }

        private void refreshCandidates(ServerWorld world) {
            candidates.clear();
            AxisAlignedBB bounds = field.getBounds();
            Collection<Entity> found = world.getEntities((Entity) null, bounds, entity -> eligible(entity, field));
            for (Entity entity : found) candidates.add(entity.getUUID());
            visibility.keySet().retainAll(candidates);
        }

        private int getVisibility(ServerWorld world, Entity entity, Vector3d entityCenter, long now) {
            VisibilitySample cached = visibility.get(entity.getUUID());
            if (cached != null && now - cached.tick < OCCLUSION_INTERVAL_TICKS) return cached.mask;
            int mask = 0;
            List<Vector3d> apertures = field.getApertures();
            for (int i = 0; i < apertures.size(); i++) {
                if (VentOcclusionTester.isClear(world, entity, entityCenter, apertures.get(i), field.getIgnoredBlocks())) mask |= 1 << i;
            }
            visibility.put(entity.getUUID(), new VisibilitySample(mask, now));
            return mask;
        }
    }

    private static final class PendingField {
        private VentExternalField field;
        private final long activateAt;
        private PendingField(VentExternalField field, long activateAt) {
            this.field = field;
            this.activateAt = activateAt;
        }
    }

    private static final class VisibilitySample {
        private final int mask;
        private final long tick;
        private VisibilitySample(int mask, long tick) {
            this.mask = mask;
            this.tick = tick;
        }
    }

    private static boolean eligible(Entity entity, VentExternalField field) {
        if (field.getNetForce() == 0) return false;
        if (entity == null || entity.removed || entity instanceof HangingEntity || entity instanceof PortalEntity || entity.isPassenger()) return false;
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            return !player.isSpectator() && !isCreativeFlying(player)
                    && VentTransportManager.canTransportPlayer(field.getNetForce());
        }
        // A weak field must not move a player indirectly by accelerating the vehicle they are riding.
        if (!VentTransportManager.canTransportPlayer(field.getNetForce()) && hasPlayerPassenger(entity)) return false;
        return true;
    }

    private static boolean isCreativeFlying(ServerPlayerEntity player) {
        return player.gameMode.getGameModeForPlayer() == net.minecraft.world.GameType.CREATIVE && player.abilities.flying;
    }

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof ServerPlayerEntity || hasPlayerPassenger(passenger)) return true;
        }
        return false;
    }

    private static Vector3d entityCenter(Entity entity) {
        AxisAlignedBB box = entity.getBoundingBox();
        return new Vector3d((box.minX + box.maxX) / 2.0D, (box.minY + box.maxY) / 2.0D, (box.minZ + box.maxZ) / 2.0D);
    }
}
