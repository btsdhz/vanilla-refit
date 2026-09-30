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

    /**
     * 整体下移半格后，原本贴在方块上边界、带 {@code cullface: up} 的那组面（栅栏/墙立柱的顶面等）
     * 已经降到半格高度，不再与上方方块紧贴，却被渲染器按“上方有方块”剔除了，于是出现缺面。
     * 这里把这一组面并到“不参与剔除”的那组里（渲染器每帧都会画），保证位移后仍然可见。
     *
     * 下移模型的下组面（立柱底面，落在下台阶上表面）仍保留剔除，避免和台阶上表面重合。
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        if (side == Direction.UP) {
            return List.of();
        }
        return transform(withUpGroup(super.getQuads(state, side, random), super.getQuads(state, Direction.UP, random), side));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side == Direction.UP) {
            return List.of();
        }
        return transform(withUpGroup(
                originalModel.getQuads(state, side, random, data, renderType),
                originalModel.getQuads(state, Direction.UP, random, data, renderType),
                side));
    }

    private static List<BakedQuad> withUpGroup(List<BakedQuad> base, List<BakedQuad> upGroup, @Nullable Direction side) {
        if (side != null || upGroup.isEmpty()) {
            return base;
        }
        List<BakedQuad> merged = new ArrayList<>(base.size() + upGroup.size());
        merged.addAll(base);
        merged.addAll(upGroup);
        return merged;
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
