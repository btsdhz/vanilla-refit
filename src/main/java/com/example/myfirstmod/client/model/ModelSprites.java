package com.example.myfirstmod.client.model;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/**
 * 「一个方块的顶 / 底 / 侧三张图」，以及按手工模型约定换图的小工具。
 *
 * <p>约定（与 {@code vertical_slab_multi_*} / {@code vertical_stair_multi} 一致）：
 *
 * <pre>
 *   朝上的面 → top     朝下的面 → bottom     其余（东南西北）→ side
 * </pre>
 *
 * <p>单纹理方块上这三张是同一张图，换与不换看不出区别；多纹理方块（砂岩类）才会体现出来。
 */
public record ModelSprites(TextureAtlasSprite top, TextureAtlasSprite bottom, TextureAtlasSprite side) {

    /**
     * 从某个平放模型里问出三张图；问不出来（模型结构太特殊）返回 null。
     *
     * <p>注意：不能直接 {@code getQuads(state, Direction.UP, ...)} ——烘焙是按 cullface 分桶的，
     * 没写 cullface 的面会落在"无剔除"桶里。所以这里把 7 个桶全部拿出来，按几何朝向归类。
     */
    @Nullable
    public static ModelSprites of(BakedModel flatModel, BlockState flatState) {
        RandomSource random = RandomSource.create(42L);
        TextureAtlasSprite top = null;
        TextureAtlasSprite bottom = null;
        TextureAtlasSprite side = null;
        for (Direction bucket : BUCKETS) {
            for (BakedQuad quad : flatModel.getQuads(flatState, bucket, random)) {
                Direction facing = QuadFacing.of(quad);
                if (facing == null) {
                    continue;
                }
                switch (facing) {
                    case UP -> top = top == null ? quad.getSprite() : top;
                    case DOWN -> bottom = bottom == null ? quad.getSprite() : bottom;
                    default -> side = side == null ? quad.getSprite() : side;
                }
            }
        }
        // 顶/底取不到就退回侧面图（很多方块顶底与侧面同图，这么做不会出错）
        if (side == null) {
            return null;
        }
        return new ModelSprites(top != null ? top : side, bottom != null ? bottom : side, side);
    }

    /** 六个方向 + 无剔除桶（null）。 */
    private static final Direction[] BUCKETS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, null
    };

    /** 该朝向的面该用哪张图。 */
    public TextureAtlasSprite forFace(@Nullable Direction face) {
        if (face == null) {
            return side;
        }
        return switch (face) {
            case UP -> top;
            case DOWN -> bottom;
            default -> side;
        };
    }

    /**
     * 把 quad 换成目标图：顶点数组复制一份，按"在旧图里的相对位置"把 UV 映射到新图上，
     * 并用给定朝向重建 quad（旋转过的模型要用旋转后的朝向，剔除与亮度才正确）。
     *
     * <p>注意：只换图，不做几何变换；需要旋转的话调用方再拿 IQuadTransformer 处理。
     */
    public static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target, Direction direction) {
        int[] vertices = quad.getVertices().clone();
        TextureAtlasSprite source = quad.getSprite();
        if (target != source) {
            float sourceWidth = source.getU1() - source.getU0();
            float sourceHeight = source.getV1() - source.getV0();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                float u = Float.intBitsToFloat(vertices[offset]);
                float v = Float.intBitsToFloat(vertices[offset + 1]);
                float relativeU = sourceWidth == 0.0F ? 0.0F : (u - source.getU0()) / sourceWidth;
                float relativeV = sourceHeight == 0.0F ? 0.0F : (v - source.getV0()) / sourceHeight;
                vertices[offset] = Float.floatToRawIntBits(target.getU(relativeU));
                vertices[offset + 1] = Float.floatToRawIntBits(target.getV(relativeV));
            }
        }
        return new BakedQuad(vertices, quad.getTintIndex(), direction, target,
                quad.isShade(), quad.hasAmbientOcclusion());
    }
}
