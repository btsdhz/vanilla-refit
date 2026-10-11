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
 * 把被包装方块模型的四边顶点整体上移 0.5 格的 BakedModel 包装器。
 *
 * 用于“放在上台阶（上半台阶）下方、悬挂贴齐”的灯笼：视觉上移贴齐上台阶底面。
 * 模型是按方块状态烘焙一次后缓存的，这里只在烘焙结果上替换/包装，不涉及每帧计算。
 */
public class SlabbedRaisingModel extends BakedModelWrapper<BakedModel> {

    private static final IQuadTransformer RAISE = QuadTransformers.applying(
            new Transformation(new Matrix4f().translation(0.0F, 0.5F, 0.0F)));

    public SlabbedRaisingModel(BakedModel originalModel) {
        super(originalModel);
    }

    /**
     * 整体上移半格后，原本贴在方块下边界、带 {@code cullface: down} 的那组面（栅栏/墙立柱的底面等）
     * 已经升到半格高度，不再与下方方块紧贴，却被渲染器按“下方有方块”剔除了，于是出现缺面。
     * 这里把这一组面并到“不参与剔除”的那组里，保证位移后仍然可见。
     *
     * 上移模型的上组面（顶到上台阶底面）仍保留剔除。
     */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        if (side == Direction.DOWN) {
            return List.of();
        }
        return transform(withDownGroup(super.getQuads(state, side, random), super.getQuads(state, Direction.DOWN, random), side));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side == Direction.DOWN) {
            return List.of();
        }
        return transform(withDownGroup(
                originalModel.getQuads(state, side, random, data, renderType),
                originalModel.getQuads(state, Direction.DOWN, random, data, renderType),
                side));
    }

    private static List<BakedQuad> withDownGroup(List<BakedQuad> base, List<BakedQuad> downGroup, @Nullable Direction side) {
        if (side != null || downGroup.isEmpty()) {
            return base;
        }
        List<BakedQuad> merged = new ArrayList<>(base.size() + downGroup.size());
        merged.addAll(base);
        merged.addAll(downGroup);
        return merged;
    }

    private List<BakedQuad> transform(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            BakedQuad copy = new BakedQuad(
                    quad.getVertices().clone(),
                    quad.getTintIndex(),
                    quad.getDirection(),
                    quad.getSprite(),
                    quad.isShade());
            RAISE.processInPlace(copy);
            out.add(copy);
        }
        return out;
    }
}
