/* Drains queued vent updates once at the end of every server tick. */
package io.github.bengman.pneumaticdiversityvents.server;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.server.update.VentUpdate;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VentUpdateHandler {
    private VentUpdateHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        VentUpdate update;
        while ((update = VentUpdateQueue.get().pop()) != null) update.execute();
    }
}
