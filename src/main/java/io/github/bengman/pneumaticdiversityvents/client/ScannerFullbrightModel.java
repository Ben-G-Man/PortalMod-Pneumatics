package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class ScannerFullbrightModel extends BakedModelWrapper<IBakedModel> {
    private static final ResourceLocation SCANNER_GRID = new ResourceLocation(
            PneumaticDiversityVents.MOD_ID, "block/vent/scanner/shared/scanner_grid");
    private static final int FULL_BRIGHT = 0x00F000F0;

    ScannerFullbrightModel(IBakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand) {
        return makeScannerGridFullbright(originalModel.getQuads(state, side, rand));
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand,
                                    @Nonnull IModelData extraData) {
        return makeScannerGridFullbright(originalModel.getQuads(state, side, rand, extraData));
    }

    private static List<BakedQuad> makeScannerGridFullbright(List<BakedQuad> quads) {
        List<BakedQuad> result = null;
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            if (!SCANNER_GRID.equals(quad.getSprite().getName())) continue;

            if (result == null) result = new ArrayList<>(quads);
            int[] vertices = quad.getVertices().clone();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) vertices[vertex * stride + 6] = FULL_BRIGHT;
            result.set(i, new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
        }
        return result == null ? quads : result;
    }
}
