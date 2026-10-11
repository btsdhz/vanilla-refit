package com.example.myfirstmod.client.model;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 把「其它模组方块」的竖形态渲染成「本模组的模板几何 + 对方贴图」。台阶和楼梯都走这一条路。
 *
 * <p>为什么不旋转对方自己的模型：旋转会把 UV、面剔除标记（cullface）和顶点朝向一起带走——
 * 贴图会跟着转 90°、环境光遮蔽会上下颠倒、剔除还会落到错误的方向上。而这些模板
 * （{@code vertical_slab_*} / {@code vertical_stair_*}）本身就是按竖放姿势手工调好的，
 * 几何、UV、剔除标记全都是对的，只需要把贴图换掉。
 *
 * <p>模板来源很省事：取原版方块（橡木台阶 / 橡木楼梯）**同状态**已经烘焙好的模型即可——
 * 那些状态在 datagen 里就指向本模组的模板，角度与 UV 都已就绪，运行时不用做任何几何运算。
 *
 * <p>换图规则和手工模型（{@code vertical_slab_multi_*} / {@code vertical_stair_multi}）一致：
 * 朝上的面用 top、朝下的面用 bottom、其余用 side。UV 只做等比映射，保持模板里定好的区域。
 */
public class RetexturedTemplateModel extends BakedModelWrapper<BakedModel> {

    private final ModelSprites sprites;
    private final TextureAtlasSprite particle;

    private RetexturedTemplateModel(BakedModel template, ModelSprites sprites, TextureAtlasSprite particle) {
        super(template);
        this.sprites = sprites;
        this.particle = particle;
    }

    /**
     * @param template 本模组的竖形态模板（几何、UV、剔除标记）
     * @param sprites  目标方块的贴图
     * @param particle 目标方块自己的粒子贴图（破坏碎屑；不传就会跟着模板走成橡木）
     */
    public static RetexturedTemplateModel create(BakedModel template, ModelSprites sprites,
                                                 TextureAtlasSprite particle) {
        return new RetexturedTemplateModel(template, sprites, particle);
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return particle;
    }

    private List<BakedQuad> retexture(List<BakedQuad> quads, @Nullable Direction side) {
        if (quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            // 桶是"cullface 桶"：无剔除桶里的面只能靠顶点算朝向（模板里 south/east 就是这种）
            Direction face = side != null ? side : QuadFacing.of(quad);
            out.add(ModelSprites.retexture(quad, sprites.forFace(face),
                    face != null ? face : quad.getDirection()));
        }
        return out;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return retexture(originalModel.getQuads(state, side, random), side);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return retexture(originalModel.getQuads(state, side, random, data, renderType), side);
    }
}
