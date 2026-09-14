package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.networking.VentAmbientSoundPacket;
import io.github.bengman.pneumaticdiversityvents.server.transport.VentTransportManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.TickableSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VentAmbientSoundClient {
    private static final Map<UUID, NetworkWindSound> WINDS = new HashMap<>();
    private static final Map<UUID, NetworkRattleSound> RATTLES = new HashMap<>();
    private static final Map<UUID, ImpellerMotorSound> MOTORS = new HashMap<>();
    private static final Map<UUID, PortalWhooshSound> PORTALS = new HashMap<>();

    private VentAmbientSoundClient() {
    }

    public static void accept(List<VentAmbientSoundPacket.NetworkSound> networks, List<VentAmbientSoundPacket.ImpellerSound> impellers, List<VentAmbientSoundPacket.PortalSound> portals) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }

        Set<UUID> activeNetworks = new HashSet<>();
        Set<UUID> overchargedNetworks = new HashSet<>();
        for (VentAmbientSoundPacket.NetworkSound network : networks) {
            activeNetworks.add(network.getId());
            NetworkWindSound wind = WINDS.get(network.getId());
            if (wind == null || wind.isStopped()) {
                wind = new NetworkWindSound(network.getPoints(), network.getNetForce());
                WINDS.put(network.getId(), wind);
                minecraft.getSoundManager().play(wind);
            } else wind.update(network.getPoints(), network.getNetForce());

            if (Math.abs(network.getNetForce()) >= VentTransportManager.PLAYER_FORCE_THRESHOLD) {
                overchargedNetworks.add(network.getId());
                NetworkRattleSound rattle = RATTLES.get(network.getId());
                if (rattle == null || rattle.isStopped()) {
                    rattle = new NetworkRattleSound(network.getPoints());
                    RATTLES.put(network.getId(), rattle);
                    minecraft.getSoundManager().play(rattle);
                } else rattle.update(network.getPoints());
            }
        }
        stopMissing(WINDS, activeNetworks);
        stopMissing(RATTLES, overchargedNetworks);

        Set<UUID> activeImpellers = new HashSet<>();
        for (VentAmbientSoundPacket.ImpellerSound impeller : impellers) {
            if (!impeller.isActive()) continue;
            activeImpellers.add(impeller.getId());
            ImpellerMotorSound motor = MOTORS.get(impeller.getId());
            if (motor == null || motor.isStopped()) {
                motor = new ImpellerMotorSound(impeller.getCenter(), impeller.isStrained());
                MOTORS.put(impeller.getId(), motor);
                minecraft.getSoundManager().play(motor);
            } else motor.update(impeller.getCenter(), impeller.isStrained());
        }
        stopMissing(MOTORS, activeImpellers);

        Set<UUID> activePortals = new HashSet<>();
        for (VentAmbientSoundPacket.PortalSound portal : portals) {
            activePortals.add(portal.getId());
            PortalWhooshSound whoosh = PORTALS.get(portal.getId());
            if (whoosh == null || whoosh.isStopped()) {
                whoosh = new PortalWhooshSound(portal.getCenter());
                PORTALS.put(portal.getId(), whoosh);
                minecraft.getSoundManager().play(whoosh);
            } else whoosh.update(portal.getCenter());
        }
        stopMissing(PORTALS, activePortals);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        clear();
    }

    private static <T extends ControlledLoopSound> void stopMissing(Map<UUID, T> sounds, Set<UUID> present) {
        sounds.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;
            entry.getValue().deactivate();
            return true;
        });
    }

    private static void clear() {
        for (ControlledLoopSound sound : WINDS.values()) sound.deactivate();
        for (ControlledLoopSound sound : RATTLES.values()) sound.deactivate();
        for (ControlledLoopSound sound : MOTORS.values()) sound.deactivate();
        for (ControlledLoopSound sound : PORTALS.values()) sound.deactivate();
        WINDS.clear(); RATTLES.clear(); MOTORS.clear(); PORTALS.clear();
    }

    private abstract static class ControlledLoopSound extends TickableSound {
        protected ControlledLoopSound(SoundEvent event) {
            this(event, ISound.AttenuationType.LINEAR);
        }

        protected ControlledLoopSound(SoundEvent event, ISound.AttenuationType attenuationType) {
            super(event, SoundCategory.BLOCKS);
            looping = true;
            delay = 0;
            attenuation = attenuationType;
            relative = false;
        }

        public void deactivate() {
            stop();
        }

        protected void setPosition(Vector3d position) {
            x = position.x;
            y = position.y;
            z = position.z;
        }

        protected Vector3d nearestPoint(List<Vector3d> points) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || points.isEmpty()) return points.isEmpty() ? Vector3d.ZERO : points.get(0);
            Vector3d viewer = minecraft.player.position();
            Vector3d nearest = points.get(0); double best = viewer.distanceToSqr(nearest);
            for (int i = 1; i < points.size(); i++) {
                double distance = viewer.distanceToSqr(points.get(i));
                if (distance < best) {
                    best = distance;
                    nearest = points.get(i);
                }
            }
            return nearest;
        }

        protected double nearestDistance(List<Vector3d> points) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || points.isEmpty()) return Double.POSITIVE_INFINITY;
            Vector3d viewer = minecraft.player.position();
            double best = Double.POSITIVE_INFINITY;
            for (Vector3d point : points) best = Math.min(best, Math.sqrt(viewer.distanceToSqr(point)));
            return best;
        }
    }

    private static final class NetworkWindSound extends ControlledLoopSound {
        private List<Vector3d> points; private int netForce;
        private NetworkWindSound(List<Vector3d> points, int netForce) {
            super(SoundEvents.ELYTRA_FLYING, ISound.AttenuationType.NONE);
            update(points, netForce);
        }

        private void update(List<Vector3d> points, int netForce) {
            this.points = points;
            this.netForce = netForce;
            apply();
        }

        private void apply() {
            float scale = Math.min(1.0F, Math.abs(netForce) / 6.0F);
            double distance = nearestDistance(points);
            float distanceScale = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - distance / 28.0D));
            // Manual attenuation guarantees falloff even for samples (such as Elytra wind) whose vanilla metadata behaves unusually.
            volume = (0.20F + 0.48F * scale) * distanceScale;
            pitch = 0.72F + 0.35F * scale;
            setPosition(nearestPoint(points));
        }

        @Override public void tick() { if (Minecraft.getInstance().player == null || points == null || points.isEmpty()) { stop(); return; } apply(); }
    }

    private static final class NetworkRattleSound extends ControlledLoopSound {
        private List<Vector3d> points;
        private NetworkRattleSound(List<Vector3d> points) {
            super(SoundEvents.CHAIN_HIT);
            update(points);
        }

        private void update(List<Vector3d> points) {
            this.points = points;
            apply();
        }

        private void apply() {
            volume = 0.075F;
            pitch = 0.83F;
            setPosition(nearestPoint(points));
        }

        @Override public void tick() { if (Minecraft.getInstance().player == null || points == null || points.isEmpty()) { stop(); return; } apply(); }
    }

    private static final class ImpellerMotorSound extends ControlledLoopSound {
        private Vector3d center;
        private boolean strained;
        private ImpellerMotorSound(Vector3d center, boolean strained) {
            super(SoundEvents.MINECART_RIDING);
            update(center, strained);
        }

        private void update(Vector3d center, boolean strained) {
            this.center = center;
            this.strained = strained;
            apply();
        }

        private void apply() {
            volume = strained ? 0.075F : 0.16F;
            pitch = strained ? 0.84F * 1.45F : 0.84F;
            setPosition(center);
        }

        @Override public void tick() { if (Minecraft.getInstance().player == null || center == null) { stop(); return; } apply(); }
    }

    private static final class PortalWhooshSound extends ControlledLoopSound {
        private Vector3d center;
        private PortalWhooshSound(Vector3d center) {
            super(SoundEvents.ELYTRA_FLYING, ISound.AttenuationType.NONE);
            update(center);
        }

        private void update(Vector3d center) {
            this.center = center;
            apply();
        }

        private void apply() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || center == null) {
                stop();
                return;
            }
            double distance = minecraft.player.position().distanceTo(center);
            float distanceScale = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - distance / 14.0D));
            volume = 0.075F * distanceScale;
            pitch = 0.88F;
            setPosition(center);
        }
        @Override
        public void tick() {
            apply();
        }
    }
}
