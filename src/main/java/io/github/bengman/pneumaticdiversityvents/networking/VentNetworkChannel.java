/* Owns Pneumatic Diversity Vents' minimal server-to-client synchronization channel. */
package io.github.bengman.pneumaticdiversityvents.networking;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class VentNetworkChannel {
    private static final String VERSION = "6";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PneumaticDiversityVents.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private VentNetworkChannel() {
    }

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(0, VentAmbientSoundPacket.class, VentAmbientSoundPacket::encode,
                VentAmbientSoundPacket::decode, VentAmbientSoundPacket::handle);
        CHANNEL.registerMessage(1, VentInspectionPacket.class, VentInspectionPacket::encode,
                VentInspectionPacket::decode, VentInspectionPacket::handle);
        registered = true;
    }

    public static void sendAmbientSoundSnapshot(ServerWorld world, VentAmbientSoundPacket packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(world::dimension), packet);
    }

    public static void sendInspection(ServerPlayerEntity player, VentInspectionPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
