/* Moves captured entities along vent centerlines, including open-end intake and clean momentum-preserving exits. */
package io.github.bengman.pneumaticdiversityvents.server.transport;

import io.github.bengman.pneumaticdiversityvents.config.VentCommonConfig;
import io.github.bengman.pneumaticdiversityvents.server.VentAdvancements;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.integration.PortalCubeDropperBridge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentConnection;
import io.github.bengman.pneumaticdiversityvents.server.network.VentEdge;
import io.github.bengman.pneumaticdiversityvents.server.network.VentNetwork;
import io.github.bengman.pneumaticdiversityvents.server.world.VentScannerManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.item.HangingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameType;
import net.minecraft.world.server.ServerWorld;
import net.portalmod.common.sorted.turret.TurretEntity;
import net.portalmod.common.sorted.turret.TurretState;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class VentTransportManager {
    public static double getBaseTransportSpeed() {
        return VentCommonConfig.baseTransportSpeed();
    }

    public static double getMaximumTransportSpeed() {
        return VentCommonConfig.maximumTransportSpeed();
    }

    public static int getForceUnitsAtMaximumSpeed() {
        return VentCommonConfig.forceUnitsAtMaximumSpeed();
    }

    public static int getMinimumImpellersToTransportPlayer() {
        return VentCommonConfig.minimumImpellersToTransportPlayer();
    }

    public static int getMinimumForceToTransportPlayer() {
        return VentCommonConfig.minimumForceToTransportPlayer();
    }

    private static final double CAPTURE_RADIUS = 0.80D;
    private static final double MOUTH_HANDOFF_DEPTH = 0.85D;
    private static final double MOUTH_HANDOFF_RADIUS = 0.95D;
    private static final double BRAKE_RELEASE_RADIUS = 1.15D;
    private static final double EXIT_CLEARANCE_MARGIN = 0.08D;
    private static final double EXIT_BONUS_SPEED = 0.15D;
    private static final double PLAYER_CENTERING_GAIN = 0.35D;
    private static final double PLAYER_MAX_CENTERING_SPEED = 0.25D;
    private static final double PLAYER_HARD_RESYNC_DISTANCE = 1.00D;
    private static final Map<ServerWorld, WorldState> WORLDS = new WeakHashMap<>();

    private VentTransportManager() {
    }

    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        registry.prepareForceState(world);
        WorldState state = WORLDS.computeIfAbsent(world, ignored -> new WorldState());
        state.syncPaths(registry);
        state.tickCaptured(world, registry);
        state.captureNew(world, registry);
    }

    public static void onEntityLeaving(ServerWorld world, Entity entity) {
        WorldState state = WORLDS.get(world);
        if (state == null || entity == null) return;
        TransportState transport = state.transported.remove(entity.getUUID());
        if (transport != null) release(entity, transport, Vector3d.ZERO);
    }

    public static boolean isTransported(ServerWorld world, Entity entity) {
        WorldState state = WORLDS.get(world);
        return state != null && entity != null && state.transported.containsKey(entity.getUUID());
    }


    public static void onWorldUnload(ServerWorld world) {
        WorldState state = WORLDS.remove(world);
        if (state != null) state.releaseAll(world);
    }

    private static final class WorldState {
        private final Map<UUID, VentPath> paths = new HashMap<>();
        private final Map<UUID, TransportState> transported = new HashMap<>();

        private void syncPaths(VentSpatialRegistry registry) {
            Set<UUID> live = new HashSet<>();
            for (VentEdge edge : registry.getNetworkManager().getEdges()) {
                live.add(edge.getId());
                paths.computeIfAbsent(edge.getId(), ignored -> VentPath.of(registry, edge));
            }
            paths.keySet().removeIf(id -> !live.contains(id));
        }

        private void releaseAll(ServerWorld world) {
            for (Map.Entry<UUID, TransportState> entry : transported.entrySet()) {
                Entity entity = world.getEntity(entry.getKey());
                if (entity != null) release(entity, entry.getValue(), Vector3d.ZERO);
            }
            transported.clear();
            paths.clear();
        }

        private void tickCaptured(ServerWorld world, VentSpatialRegistry registry) {
            Iterator<Map.Entry<UUID, TransportState>> iterator = transported.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, TransportState> entry = iterator.next();
                Entity entity = world.getEntity(entry.getKey());
                TransportState state = entry.getValue();
                if (entity == null || entity.removed) {
                    iterator.remove();
                    continue;
                }

                VentEdge edge = registry.getEdge(state.edgeId);
                VentPath path = paths.get(state.edgeId);
                if (edge == null || path == null || edge.getNetwork() == null || !shouldRemainTransported(entity, edge.getNetwork())) {
                    release(entity, state, Vector3d.ZERO);
                    iterator.remove();
                    continue;
                }

                int netForce = edge.getNetwork().getNetForce();
                double speed = getTransportSpeed(netForce);
                boolean aToB = flowAToB(edge, netForce);

                if (entity instanceof ServerPlayerEntity && isBraking((ServerPlayerEntity) entity)) {
                    if (!brakePlayer((ServerPlayerEntity) entity, state, path, aToB)) {
                        release(entity, state, Vector3d.ZERO);
                        iterator.remove();
                    }
                    continue;
                }

                beginRide(entity, state);
                if (entity instanceof ServerPlayerEntity && Math.abs(netForce) >= getForceUnitsAtMaximumSpeed())
                    VentAdvancements.grantMaxSpeedRide((ServerPlayerEntity) entity);
                prepareForTransport(entity, state);
                if (state.isOutside()) advanceOutside(world, registry, entity, state, path, aToB, speed);
                else advanceInside(world, registry, entity, state, path, aToB, speed);

                if (state.dropperHandoff != null) {
                    BlockPos dropper = state.dropperHandoff;
                    state.dropperHandoff = null;
                    release(entity, state, Vector3d.ZERO);
                    PortalCubeDropperBridge.handoff(world, dropper, entity);
                    iterator.remove();
                    continue;
                }

                VentEdge currentEdge = registry.getEdge(state.edgeId);
                VentPath currentPath = paths.get(state.edgeId);
                if (currentEdge == null || currentPath == null || currentEdge.getNetwork() == null) {
                    release(entity, state, Vector3d.ZERO);
                    iterator.remove();
                    continue;
                }

                boolean currentAToB = flowAToB(currentEdge, currentEdge.getNetwork().getNetForce());
                Vector3d center, tangent;
                if (state.isOutside()) {
                    boolean atA = state.outsideAtA;
                    center = currentPath.outside(atA, state.outsideDistance);
                    tangent = currentPath.tangent(atA ? 0.0D : currentPath.getLength(), currentAToB);

                    if (!isEntryBoundary(atA, currentAToB) && state.outsideDistance >= getExitClearance(entity, tangent)) {
                        // Finish outside the shell, then hand motion back to Minecraft with a small extra launch kick.
                        placeAtCenter(entity, center);
                        release(entity, state, tangent.scale(speed + EXIT_BONUS_SPEED));
                        iterator.remove();
                        continue;
                    }
                } else {
                    center = currentPath.sample(state.distance);
                    tangent = currentPath.tangent(state.distance, currentAToB);
                }

                moveTransported(entity, center, tangent, speed);
                orientBody(entity, tangent);
            }
        }

        private void captureNew(ServerWorld world, VentSpatialRegistry registry) {
            Map<UUID, Candidate> candidates = new HashMap<>();

            // Existing behavior: anything already inside a powered network can be picked up by its nearest centerline.
            for (VentEdge edge : registry.getNetworkManager().getEdges()) {
                VentNetwork network = edge.getNetwork();
                VentPath path = paths.get(edge.getId());
                if (network == null || path == null || !network.hasActiveForceField() || network.getNetForce() == 0) continue;

                Collection<Entity> entities = world.getEntities((Entity) null, path.getBounds(), entity -> isCandidate(entity, network));
                for (Entity entity : entities) {
                    if (transported.containsKey(entity.getUUID())) continue;
                    Vector3d center = entityCenter(entity);
                    if (!contains(path.getBounds(), center)) continue;
                    VentPath.Projection projection = path.project(center);
                    if (projection.getDistanceSquared() > CAPTURE_RADIUS * CAPTURE_RADIUS) continue;
                    Candidate existing = candidates.get(entity.getUUID());
                    if (existing == null || projection.getDistanceSquared() < existing.score)
                        candidates.put(entity.getUUID(), Candidate.inside(entity, edge, path, projection));
                }
            }

            // The external field does the suction; deterministic transport takes over just before the mouth so standing players cannot be stopped by shell collision.
            for (VentEdge edge : registry.getNetworkManager().getEdges()) {
                VentNetwork network = edge.getNetwork();
                VentPath path = paths.get(edge.getId());
                if (network == null || path == null || !network.hasActiveForceField() || network.getNetForce() == 0) continue;

                boolean aToB = flowAToB(edge, network.getNetForce());
                boolean entryAtA = aToB;
                VentConnection entry = entryAtA ? edge.getA() : edge.getB();
                if (entry.getPeer() != null) continue;
                // A dropper roof is not an intake aperture; entities can only enter it from inside the tube.
                if (PortalCubeDropperBridge.isConnectedEndpoint(world, registry, edge, entry)) continue;

                AxisAlignedBB intake = path.getOutsideCaptureBounds(entryAtA, MOUTH_HANDOFF_DEPTH, MOUTH_HANDOFF_RADIUS);
                Collection<Entity> entities = world.getEntities((Entity) null, intake, entity -> isCandidate(entity, network));
                for (Entity entity : entities) {
                    if (transported.containsKey(entity.getUUID()) || candidates.containsKey(entity.getUUID())) continue;
                    VentPath.OutsideProjection projection = path.projectOutside(entryAtA, entityCenter(entity));
                    if (projection.getDistance() > MOUTH_HANDOFF_DEPTH || projection.getRadialDistanceSquared() > MOUTH_HANDOFF_RADIUS * MOUTH_HANDOFF_RADIUS) continue;
                    candidates.put(entity.getUUID(), Candidate.outside(entity, edge, path, entryAtA, projection));
                }
            }

            for (Candidate candidate : candidates.values()) {
                Entity entity = candidate.entity;
                TransportState state = candidate.outside
                        ? TransportState.captureOutside(entity, candidate.edge.getId(), candidate.atA ? 0.0D : candidate.path.getLength(), candidate.atA, candidate.outsideDistance)
                        : TransportState.capture(entity, candidate.edge.getId(), candidate.projection.getDistance());
                transported.put(entity.getUUID(), state);

                boolean aToB = flowAToB(candidate.edge, candidate.edge.getNetwork().getNetForce());
                if (entity instanceof ServerPlayerEntity && isBraking((ServerPlayerEntity) entity)) {
                    brakePlayer((ServerPlayerEntity) entity, state, candidate.path, aToB);
                    continue;
                }

                beginRide(entity, state);
                VentScannerManager.observeTraversal(world, registry, candidate.edge, entity);
                prepareForTransport(entity, state);
                Vector3d center = candidate.outside ? candidate.path.outside(candidate.atA, candidate.outsideDistance) : candidate.projection.getPoint();
                Vector3d tangent = candidate.path.tangent(candidate.outside ? (candidate.atA ? 0.0D : candidate.path.getLength()) : state.distance, aToB);
                moveTransported(entity, center, tangent, getTransportSpeed(candidate.edge.getNetwork().getNetForce()));
                orientBody(entity, tangent);
            }
        }

        /* Advances while inside physical vent geometry; an open face becomes a virtual outside extension instead of an immediate release. */
        private void advanceInside(ServerWorld world, VentSpatialRegistry registry, Entity entity, TransportState state, VentPath initialPath, boolean initialAToB, double amount) {
            VentPath path = initialPath;
            boolean aToB = initialAToB;
            double remaining = amount;

            while (remaining > 1.0E-7D) {
                VentEdge current = registry.getEdge(state.edgeId);
                if (current != null) VentScannerManager.observeTraversal(world, registry, current, entity);
                double available = aToB ? path.getLength() - state.distance : state.distance;
                if (remaining <= available + 1.0E-7D) {
                    state.distance += aToB ? remaining : -remaining;
                    return;
                }

                remaining -= Math.max(0.0D, available);
                VentEdge edge = registry.getEdge(state.edgeId);
                if (edge == null) return;
                VentConnection exit = aToB ? edge.getB() : edge.getA();
                VentConnection peer = exit.getPeer();
                if (peer == null) {
                    BlockPos dropper = PortalCubeDropperBridge.findConnectedDropper(world, registry, edge, exit);
                    if (dropper != null) {
                        state.distance = aToB ? path.getLength() : 0.0D;
                        state.outsideAtA = null;
                        state.outsideDistance = 0.0D;
                        state.dropperHandoff = dropper;
                        return;
                    }
                    state.outsideAtA = !aToB;
                    state.outsideDistance = remaining;
                    state.distance = aToB ? path.getLength() : 0.0D;
                    return;
                }

                VentEdge next = peer.getParent();
                VentPath nextPath = paths.get(next.getId());
                if (nextPath == null || next.getNetwork() != edge.getNetwork()) {
                    state.outsideAtA = !aToB;
                    state.outsideDistance = remaining;
                    state.distance = aToB ? path.getLength() : 0.0D;
                    return;
                }

                state.edgeId = next.getId();
                VentScannerManager.observeTraversal(world, registry, next, entity);
                aToB = peer == next.getA();
                state.distance = aToB ? 0.0D : nextPath.getLength();
                path = nextPath;
            }
        }

        /* Outside extensions are reversible: upstream motion enters the vent, downstream motion clears the outlet before release. */
        private void advanceOutside(ServerWorld world, VentSpatialRegistry registry, Entity entity, TransportState state, VentPath path, boolean aToB, double amount) {
            boolean entering = isEntryBoundary(state.outsideAtA, aToB);
            if (!entering) {
                state.outsideDistance += amount;
                return;
            }

            if (amount + 1.0E-7D < state.outsideDistance) {
                state.outsideDistance -= amount;
                return;
            }

            double remaining = Math.max(0.0D, amount - state.outsideDistance);
            boolean atA = state.outsideAtA;
            state.outsideAtA = null;
            state.outsideDistance = 0.0D;
            state.distance = atA ? 0.0D : path.getLength();
            if (remaining > 1.0E-7D) advanceInside(world, registry, entity, state, path, aToB, remaining);
        }
    }

    private static boolean shouldRemainTransported(Entity entity, VentNetwork network) {
        if (!network.hasActiveForceField()) return false;
        int netForce = network.getNetForce();
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            return canTransportPlayer(netForce) && !player.isSpectator() && !isCreativeFlying(player);
        }
        return netForce != 0;
    }

    public static boolean canTransportPlayer(int netForce) {
        return Math.abs(netForce) >= getMinimumForceToTransportPlayer();
    }

    /* All entities in one network use the same speed derived only from absolute net impeller force. */
    public static double getTransportSpeed(int netForce) {
        int magnitude = Math.abs(netForce);
        if (magnitude == 0) return 0.0D;
        int maxForce = getForceUnitsAtMaximumSpeed();
        double baseSpeed = getBaseTransportSpeed(), maxSpeed = getMaximumTransportSpeed();
        if (magnitude >= maxForce) return maxSpeed;
        double progress = (magnitude - 1.0D) / (maxForce - 1.0D);
        return baseSpeed + (maxSpeed - baseSpeed) * progress;
    }

    private static boolean isCandidate(Entity entity, VentNetwork network) {
        if (entity == null || entity.removed || entity instanceof HangingEntity || entity.isPassenger() || entity.isVehicle()) return false;
        return shouldRemainTransported(entity, network);
    }

    public static boolean flowAToB(VentEdge edge, int netForce) {
        return netForce > 0 ? edge.isAToB() : !edge.isAToB();
    }

    private static boolean isEntryBoundary(boolean atA, boolean aToB) {
        return atA == aToB;
    }

    private static boolean isCreativeFlying(ServerPlayerEntity player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE && player.abilities.flying;
    }

    private static boolean isBraking(ServerPlayerEntity player) {
        if (!player.isShiftKeyDown()) return false;
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.CREATIVE;
    }

    private static boolean brakePlayer(ServerPlayerEntity player, TransportState state, VentPath path, boolean aToB) {
        setPlayerPose(player, state, Pose.CROUCHING);
        player.stopFallFlying();
        player.setSwimming(false);
        player.noPhysics = false;
        player.setNoGravity(true);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vector3d.ZERO);
        syncPlayerVelocity(player);

        Vector3d center = entityCenter(player);
        if (state.isOutside()) {
            VentPath.OutsideProjection projection = path.projectOutside(state.outsideAtA, center);
            if (projection.getRadialDistanceSquared() > BRAKE_RELEASE_RADIUS * BRAKE_RELEASE_RADIUS) return false;
            state.outsideDistance = projection.getDistance();
            orientBody(player, path.tangent(state.outsideAtA ? 0.0D : path.getLength(), aToB));
            return true;
        }

        VentPath.Projection projection = path.project(center);
        if (projection.getDistanceSquared() > BRAKE_RELEASE_RADIUS * BRAKE_RELEASE_RADIUS) return false;
        state.distance = projection.getDistance();
        orientBody(player, path.tangent(state.distance, aToB));
        return true;
    }

    private static void beginRide(Entity entity, TransportState state) {
        if (state.rideStarted) return;
        state.rideStarted = true;
        if (entity instanceof TurretEntity) {
            TurretEntity turret = (TurretEntity) entity;
            if (turret.getState() != TurretState.DEAD) {
                // External suction is ordinary physics; a turret is only disabled once deterministic tube capture actually begins.
                turret.setState(TurretState.DEAD);
                turret.setAnimationTicks(0);
                turret.refreshDimensions();
            }
        }
    }

    private static void prepareForTransport(Entity entity, TransportState state) {
        entity.noPhysics = true;
        entity.setNoGravity(true);
        entity.fallDistance = 0.0F;
        if (entity instanceof MobEntity) ((MobEntity) entity).setNoAi(true);
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            setPlayerPose(player, state, Pose.FALL_FLYING);
            player.setSwimming(false);
            if (!player.isFallFlying()) player.startFallFlying();
        }
    }

    private static void setPlayerPose(ServerPlayerEntity player, TransportState state, Pose pose) {
        if (state.transportPose == pose) return;
        player.setForcedPose(pose);
        player.refreshDimensions();
        state.transportPose = pose;
    }

    private static void release(Entity entity, TransportState state, Vector3d exitVelocity) {
        entity.noPhysics = state.previousNoPhysics;
        entity.setNoGravity(state.previousNoGravity);
        entity.fallDistance = 0.0F;
        if (entity instanceof MobEntity && state.previousNoAi != null) ((MobEntity) entity).setNoAi(state.previousNoAi);
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            player.setSwimming(state.previousSwimming);
            if (state.previousFallFlying) player.startFallFlying(); else player.stopFallFlying();
            player.setForcedPose(state.previousForcedPose);
            player.refreshDimensions();
            state.transportPose = null;
            if (exitVelocity.lengthSqr() > 1.0E-10D) VentAdvancements.armExitFall(player);
        }

        // Apply launch motion after restoring vanilla state so living-entity state changes cannot erase the exit impulse.
        entity.setDeltaMovement(exitVelocity);
        syncEntityVelocity(entity);
    }

    private static void moveTransported(Entity entity, Vector3d center, Vector3d tangent, double speed) {
        if (entity instanceof ServerPlayerEntity) movePlayerSmoothly((ServerPlayerEntity) entity, center, tangent, speed);
        else {
            placeAtCenter(entity, center);
            entity.setDeltaMovement(Vector3d.ZERO);
        }
    }

    /* Players ride on velocity so the client interpolates continuously; only large drift uses a positional correction. */
    private static void movePlayerSmoothly(ServerPlayerEntity player, Vector3d targetCenter, Vector3d tangent, double speed) {
        Vector3d offset = targetCenter.subtract(entityCenter(player));
        double distance = offset.length();
        if (distance > PLAYER_HARD_RESYNC_DISTANCE) {
            placeAtCenter(player, targetCenter);
            offset = Vector3d.ZERO;
        }

        Vector3d correction = offset.scale(PLAYER_CENTERING_GAIN);
        double correctionSpeed = correction.length();
        if (correctionSpeed > PLAYER_MAX_CENTERING_SPEED)
            correction = correction.scale(PLAYER_MAX_CENTERING_SPEED / correctionSpeed);

        Vector3d velocity = tangent.scale(speed).add(correction);
        if (velocity.lengthSqr() > 1.0E-10D) velocity = velocity.normalize().scale(speed);
        player.setDeltaMovement(velocity);
        syncPlayerVelocity(player);
    }

    private static double getExitClearance(Entity entity, Vector3d tangent) {
        AxisAlignedBB box = entity.getBoundingBox();
        double halfExtent;
        if (Math.abs(tangent.x) > 0.5D) halfExtent = (box.maxX - box.minX) / 2.0D;
        else if (Math.abs(tangent.y) > 0.5D) halfExtent = (box.maxY - box.minY) / 2.0D;
        else halfExtent = (box.maxZ - box.minZ) / 2.0D;
        return halfExtent + EXIT_CLEARANCE_MARGIN;
    }

    private static void syncPlayerVelocity(ServerPlayerEntity player) {
        player.connection.send(new SEntityVelocityPacket(player.getId(), player.getDeltaMovement()));
    }

    private static void syncEntityVelocity(Entity entity) {
        if (!(entity.level instanceof ServerWorld)) return;
        ((ServerWorld) entity.level).getChunkSource().broadcastAndSend(entity,
                new SEntityVelocityPacket(entity.getId(), entity.getDeltaMovement()));
    }

    private static void placeAtCenter(Entity entity, Vector3d center) {
        double baseY = center.y - entity.getBbHeight() / 2.0D;
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            player.connection.teleport(center.x, baseY, center.z, player.yRot, player.xRot);
        } else entity.setPos(center.x, baseY, center.z);
    }

    private static Vector3d entityCenter(Entity entity) {
        AxisAlignedBB box = entity.getBoundingBox();
        return new Vector3d((box.minX + box.maxX) / 2.0D, (box.minY + box.maxY) / 2.0D, (box.minZ + box.maxZ) / 2.0D);
    }

    private static boolean contains(AxisAlignedBB bounds, Vector3d point) {
        return point.x >= bounds.minX && point.x <= bounds.maxX
                && point.y >= bounds.minY && point.y <= bounds.maxY
                && point.z >= bounds.minZ && point.z <= bounds.maxZ;
    }

    private static void orientBody(Entity entity, Vector3d tangent) {
        if (!(entity instanceof net.minecraft.entity.LivingEntity)) return;
        double horizontal = tangent.x * tangent.x + tangent.z * tangent.z;
        if (horizontal < 1.0E-5D) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-tangent.x, tangent.z));
        net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) entity;
        living.setYBodyRot(yaw);
        living.yBodyRotO = yaw;
    }

    private static final class TransportState {
        private UUID edgeId;
        private double distance;
        private Boolean outsideAtA;
        private double outsideDistance;
        private BlockPos dropperHandoff;
        private final boolean previousNoPhysics, previousNoGravity;
        private final Boolean previousNoAi;
        private final Pose previousForcedPose;
        private final boolean previousFallFlying, previousSwimming;
        private Pose transportPose;
        private boolean rideStarted;

        private TransportState(UUID edgeId, double distance, Boolean outsideAtA, double outsideDistance,
                boolean previousNoPhysics, boolean previousNoGravity, Boolean previousNoAi,
                Pose previousForcedPose, boolean previousFallFlying, boolean previousSwimming) {
            this.edgeId = edgeId;
            this.distance = distance;
            this.outsideAtA = outsideAtA;
            this.outsideDistance = outsideDistance;
            this.previousNoPhysics = previousNoPhysics;
            this.previousNoGravity = previousNoGravity;
            this.previousNoAi = previousNoAi;
            this.previousForcedPose = previousForcedPose;
            this.previousFallFlying = previousFallFlying;
            this.previousSwimming = previousSwimming;
        }

        private boolean isOutside() {
            return outsideAtA != null;
        }

        private static TransportState capture(Entity entity, UUID edgeId, double distance) {
            return capture(entity, edgeId, distance, null, 0.0D);
        }

        private static TransportState captureOutside(Entity entity, UUID edgeId, double distance, boolean atA, double outsideDistance) {
            return capture(entity, edgeId, distance, atA, outsideDistance);
        }

        private static TransportState capture(Entity entity, UUID edgeId, double distance, Boolean outsideAtA, double outsideDistance) {
            Boolean noAi = entity instanceof MobEntity ? ((MobEntity) entity).isNoAi() : null;
            ServerPlayerEntity player = entity instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity : null;
            Pose forced = player == null ? null : player.getForcedPose();
            return new TransportState(edgeId, distance, outsideAtA, outsideDistance, entity.noPhysics, entity.isNoGravity(), noAi, forced,
                    player != null && player.isFallFlying(), player != null && player.isSwimming());
        }
    }

    private static final class Candidate {
        private final Entity entity;
        private final VentEdge edge;
        private final VentPath path;
        private final VentPath.Projection projection;
        private final boolean outside, atA;
        private final double outsideDistance, score;

        private Candidate(Entity entity, VentEdge edge, VentPath path, VentPath.Projection projection,
                boolean outside, boolean atA, double outsideDistance, double score) {
            this.entity = entity;
            this.edge = edge;
            this.path = path;
            this.projection = projection;
            this.outside = outside;
            this.atA = atA;
            this.outsideDistance = outsideDistance;
            this.score = score;
        }

        private static Candidate inside(Entity entity, VentEdge edge, VentPath path, VentPath.Projection projection) {
            return new Candidate(entity, edge, path, projection, false, false, 0.0D, projection.getDistanceSquared());
        }

        private static Candidate outside(Entity entity, VentEdge edge, VentPath path, boolean atA, VentPath.OutsideProjection projection) {
            return new Candidate(entity, edge, path, null, true, atA, projection.getDistance(), projection.getRadialDistanceSquared());
        }
    }
}
