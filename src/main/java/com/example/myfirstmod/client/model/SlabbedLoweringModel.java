package com.example.myfirstmod.client.model;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * 把被包装方块模型的四边顶点整体下移 0.5 格的 BakedModel 包装器。
 *
 * 用于“放在普通下半台阶上的火把/灯笼/任何继承原版类的方块”：视觉下移贴齐。
 * 模型是按方块状态烘焙一次后缓存的，这里只在烘焙结果上替换/包装，不涉及每帧计算。
 */
public class SlabbedLoweringModel extends BakedModelWrapper<BakedModel> {

    private static final IQuadTransformer LOWER = QuadTransformers.applying(
            new Transformation(new Matrix4f().translation(0.0F, -0.5F, 0.0F)));

    public SlabbedLoweringModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return transform(super.getQuads(state, side, random));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return transform(originalModel.getQuads(state, side, random, data, renderType));
    }

    private List<BakedQuad> transform(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            // 复制顶点后再变换，避免污染原模型共享的顶点数组
            BakedQuad copy = new BakedQuad(
                    quad.getVertices().clone(),
                    quad.getTintIndex(),
                    quad.getDirection(),
                    quad.getSprite(),
                    quad.isShade());
            LOWER.processInPlace(copy);
            out.add(copy);
        }
        return out;
    }
}
