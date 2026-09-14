/* Declares datapack-extensible block groups used by vent airflow calculations. */
package io.github.bengman.pneumaticdiversityvents.shared;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags.IOptionalNamedTag;

public final class VentTags {
    public static final IOptionalNamedTag<Block> FORCE_TRANSPARENT =
            BlockTags.createOptional(new ResourceLocation(PneumaticDiversityVents.MOD_ID, "force_transparent"));

    private VentTags() {
    }
}
