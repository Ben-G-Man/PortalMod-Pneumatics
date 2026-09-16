/* Runs deterministic vent transport and safely restores entities when they leave a server world. */
package io.github.bengman.pneumaticdiversityvents.server.transport;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.VentAdvancements;
import io.github.bengman.pneumaticdiversityvents.server.externalforce.VentExternalFieldManager;
import io.github.bengman.pneumaticdiversityvents.server.integration.PortalAntlineBridge;
import io.github.bengman.pneumaticdiversityvents.server.integration.PortalCubeDropperBridge;
import io.github.bengman.pneumaticdiversityvents.server.sound.VentAmbientSoundManager;
import io.github.bengman.pneumaticdiversityvents.server.world.VentJunctionManager;
import io.github.bengman.pneumaticdiversityvents.server.world.VentScannerManager;
import io.github.bengman.pneumaticdiversityvents.server.world.VentTerminalStateManager;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VentTransportHandler {
    private VentTransportHandler() {
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.world;
        VentAdvancements.tick(world);
        // Junction power changes are topology changes, so apply them before any force/path consumer sees this tick.
        VentJunctionManager.tick(world);
        PortalCubeDropperBridge.tick(world);
        VentExternalFieldManager.tick(world);
        VentTransportManager.tick(world);
        VentScannerManager.tick(world);
        VentTerminalStateManager.tick(world);
        VentInspectionManager.tick(world);
        VentAmbientSoundManager.tick(world);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveWorldEvent event) {
        if (!(event.getWorld() instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.getWorld();
        VentTransportManager.onEntityLeaving(world, event.getEntity());
        PortalCubeDropperBridge.onEntityLeaving(world, event.getEntity());
        if (event.getEntity() instanceof ServerPlayerEntity) VentAdvancements.onPlayerLeaving((ServerPlayerEntity) event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayerEntity)
            VentAdvancements.disarmExitFall((ServerPlayerEntity) event.getEntityMounting());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity)
            VentAdvancements.onDeath((ServerPlayerEntity) event.getEntityLiving(), event.getSource());
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level instanceof ServerWorld
                && VentTransportManager.isTransported((ServerWorld) event.getEntity().level, event.getEntity())) event.setCanceled(true);
    }


    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PortalCubeDropperBridge.onRightClickBlock(event);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.getWorld();
        VentExternalFieldManager.onWorldUnload(world);
        VentTransportManager.onWorldUnload(world);
        VentScannerManager.onWorldUnload(world);
        PortalAntlineBridge.onWorldUnload(world);
        PortalCubeDropperBridge.onWorldUnload(world);
    }
}
