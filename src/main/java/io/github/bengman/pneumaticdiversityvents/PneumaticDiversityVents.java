/* Registers Pneumatic Diversity Vents blocks, items, and particles without client-only dependencies. */
package io.github.bengman.pneumaticdiversityvents;

import io.github.bengman.pneumaticdiversityvents.config.VentCommonConfig;
import io.github.bengman.pneumaticdiversityvents.networking.VentNetworkChannel;
import io.github.bengman.pneumaticdiversityvents.shared.EncasedVentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentItem;
import io.github.bengman.pneumaticdiversityvents.shared.VentJunctionBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentScannerBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTerminalBlock;
import io.github.bengman.pneumaticdiversityvents.shared.VentTurnBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleType;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(PneumaticDiversityVents.MOD_ID)
public class PneumaticDiversityVents {
    public static final String MOD_ID = "pneumaticdiversityvents";

    public static final ItemGroup DIVERSITY_VENTS_TAB = new ItemGroup(MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(VENT_SEGMENT_ITEM.get());
        }
    };

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final RegistryObject<VentBlock> VENT_SEGMENT_SHORT_BLOCK =
            BLOCKS.register("vent_segment_short", () -> createVentBlock(true));
    public static final RegistryObject<VentBlock> VENT_SEGMENT_LONG_BLOCK =
            BLOCKS.register("vent_segment_long", () -> createVentBlock(false));
    public static final RegistryObject<VentTurnBlock> VENT_SEGMENT_TURN_BLOCK =
            BLOCKS.register("vent_segment_turn", PneumaticDiversityVents::createTurnBlock);
    public static final RegistryObject<VentImpellerBlock> VENT_IMPELLER_BLOCK =
            BLOCKS.register("vent_impeller", () -> new VentImpellerBlock(impellerProperties()));
    public static final RegistryObject<VentTerminalBlock> VENT_TERMINAL_BLOCK =
            BLOCKS.register("vent_terminal", () -> new VentTerminalBlock(terminalProperties()));
    public static final RegistryObject<EncasedVentBlock> VENT_ENCASED_BLOCK =
            BLOCKS.register("vent_encased", () -> new EncasedVentBlock(encasedProperties()));
    public static final RegistryObject<VentScannerBlock> VENT_SCANNER_BLOCK =
            BLOCKS.register("vent_scanner", () -> new VentScannerBlock(scannerProperties()));
    public static final RegistryObject<VentJunctionBlock> VENT_JUNCTION_BLOCK =
            BLOCKS.register("vent_junction", () -> new VentJunctionBlock(junctionProperties()));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> VENT_SEGMENT_ITEM = ITEMS.register(
            "vent_segment",
            () -> new VentItem(VENT_SEGMENT_SHORT_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));
    public static final RegistryObject<Item> VENT_IMPELLER_ITEM = ITEMS.register(
            "vent_impeller",
            () -> new VentItem(VENT_IMPELLER_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));
    public static final RegistryObject<Item> VENT_TERMINAL_ITEM = ITEMS.register(
            "vent_terminal",
            () -> new VentItem(VENT_TERMINAL_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));
    public static final RegistryObject<Item> VENT_ENCASED_ITEM = ITEMS.register(
            "vent_encased",
            () -> new VentItem(VENT_ENCASED_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));
    public static final RegistryObject<Item> VENT_SCANNER_ITEM = ITEMS.register(
            "vent_scanner",
            () -> new VentItem(VENT_SCANNER_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));
    public static final RegistryObject<Item> VENT_JUNCTION_ITEM = ITEMS.register(
            "vent_junction",
            () -> new VentItem(VENT_JUNCTION_BLOCK.get(), new Item.Properties().tab(DIVERSITY_VENTS_TAB)));

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);
    public static final RegistryObject<BasicParticleType> VENT_SMOKE_PARTICLE =
            PARTICLES.register("vent_smoke", () -> new BasicParticleType(true));

    private static AbstractBlock.Properties ventProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(2.5F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.GLASS);
    }

    private static AbstractBlock.Properties encasedProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(2.5F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.METAL);
    }

    private static AbstractBlock.Properties impellerProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(3.0F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.METAL);
    }

    private static AbstractBlock.Properties terminalProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(2.5F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.METAL);
    }

    private static AbstractBlock.Properties scannerProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(2.5F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.GLASS);
    }

    private static AbstractBlock.Properties junctionProperties() {
        return AbstractBlock.Properties.of(Material.METAL)
                .strength(3.0F)
                .harvestTool(ToolType.PICKAXE)
                .harvestLevel(0)
                .sound(SoundType.GLASS);
    }

    private static VentBlock createVentBlock(boolean extendable) {
        return new VentBlock(ventProperties(), extendable);
    }

    private static VentTurnBlock createTurnBlock() {
        return new VentTurnBlock(ventProperties());
    }

    public PneumaticDiversityVents() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VentCommonConfig.SPEC);
        VentNetworkChannel.register();

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        PARTICLES.register(bus);
    }
}
