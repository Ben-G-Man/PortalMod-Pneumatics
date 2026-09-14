package io.github.bengman.pneumaticdiversityvents.server.sound;

import io.github.bengman.pneumaticdiversityvents.networking.VentAmbientSoundPacket;
import io.github.bengman.pneumaticdiversityvents.networking.VentNetworkChannel;
import io.github.bengman.pneumaticdiversityvents.server.VentSpatialRegistry;
import io.github.bengman.pneumaticdiversityvents.server.externalforce.VentExternalFieldManager;

import net.minecraft.world.server.ServerWorld;

public final class VentAmbientSoundManager {
    private VentAmbientSoundManager() {
    }

    /* Client-owned loop instances can react to network changes immediately instead of waiting for long one-shot sounds to finish. */
    public static void tick(ServerWorld world) {
        VentSpatialRegistry registry = VentSpatialRegistry.get(world);
        VentNetworkChannel.sendAmbientSoundSnapshot(world, VentAmbientSoundPacket.fromWorld(registry, VentExternalFieldManager.getPortalSoundSources(world)));
    }
}
