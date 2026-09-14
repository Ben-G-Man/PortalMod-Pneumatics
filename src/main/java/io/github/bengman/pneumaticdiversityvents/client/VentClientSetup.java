/* Registers client-only render layers and particle rendering without leaking client classes into common startup. */
package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VentClientSetup {
    private VentClientSetup() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_SEGMENT_SHORT_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_SEGMENT_LONG_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_SEGMENT_TURN_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_IMPELLER_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_TERMINAL_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_ENCASED_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_SCANNER_BLOCK.get(), RenderType.translucent());
            RenderTypeLookup.setRenderLayer(PneumaticDiversityVents.VENT_JUNCTION_BLOCK.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    public static void makeScannerGridFullbright(ModelBakeEvent event) {
        event.getModelRegistry().replaceAll((location, model) ->
                PneumaticDiversityVents.MOD_ID.equals(location.getNamespace())
                        && "vent_scanner".equals(location.getPath())
                        ? new ScannerFullbrightModel(model) : model);
    }

    @SubscribeEvent
    public static void registerParticleFactories(ParticleFactoryRegisterEvent event) {
        Minecraft.getInstance().particleEngine.register(
                PneumaticDiversityVents.VENT_SMOKE_PARTICLE.get(),
                VentSmokeParticle.Factory::new);
    }
}
