package com.example.myfirstmod.client.model;

import com.example.myfirstmod.util.VerticalSlabMode;
import com.mojang.math.Transformation;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 运行时把方块自己的“平放台阶模型”按所处朝向旋成一个竖半砖模型。
 *
 * <p>用于在客户端动态支持“只继承原版 SlabBlock、且只有一张复用贴图”的其它模组台阶：
 * 这些台阶的竖放状态（btsdhz_mode=vertical_*）在 blockstate 里往往没有显式列出变体，
 * 会被 blockstate 的部分匹配（如 type=bottom）解析成平放模型。我们把它包装成
 * 旋转后的竖放模型，从而无需为每个材质预生成模型即可显示竖放。
 *
 * <p>旋转用的是绕向正确的旋转+平移（det=+1），避免反射导致面被反转剔除。
 * 由于是整体刚性旋转，贴图会随着面一起转向；对“单贴图复用”的台阶没有问题。
 */
public class VerticalSlabRuntimeModel extends BakedModelWrapper<BakedModel> {

    private final VerticalSlabMode mode;
    private final IQuadTransformer transformer;

    public VerticalSlabRuntimeModel(BakedModel original, VerticalSlabMode mode) {
        super(original);
        this.mode = mode;
        this.transformer = buildTransformer(mode);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        // 渲染器要的是竖放状态的某个面，但底层模型是平放台阶，需先把它映射到对应的平方面
        Direction flatSide = (side == null) ? null : mapSide(side);
        return transformer.process(originalModel.getQuads(state, flatSide, random, data, renderType));
    }

    /**
     * 把“要渲染的竖放面方向”映射成“底层平放台阶的面方向”。
     * 依据竖放旋转的逆映射得到。
     */
    private Direction mapSide(Direction side) {
        return switch (mode) {
            case VERTICAL_NS -> switch (side) {
                case NORTH -> Direction.DOWN;
                case SOUTH -> Direction.UP;
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                case EAST -> Direction.EAST;
                case WEST -> Direction.WEST;
            };
            case VERTICAL_EW -> switch (side) {
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                case UP -> Direction.WEST;
                case DOWN -> Direction.EAST;
                case EAST -> Direction.UP;
                case WEST -> Direction.DOWN;
            };
            default -> side;
        };
    }

    /**
     * 平放台阶旋成竖半砖的旋转（det=+1，含平移）：
     *  - VERTICAL_NS：(x,y,z) -> (x, 16-z, y)。平放下半→北半，平放上半→南半。
     *  - VERTICAL_EW：(x,y,z) -> (y, 16-x, z)。平放下半→西半，平放上半→东半。
     */
    private static IQuadTransformer buildTransformer(VerticalSlabMode mode) {
        Matrix4f m = new Matrix4f();
        m.identity();
        switch (mode) {
            case VERTICAL_NS -> {
                m.m00(1.0f); m.m01(0.0f); m.m02(0.0f);  m.m03(0.0f);
                m.m10(0.0f); m.m11(0.0f); m.m12(-1.0f); m.m13(16.0f);
                m.m20(0.0f); m.m21(1.0f);  m.m22(0.0f);  m.m23(0.0f);
                m.m30(0.0f); m.m31(0.0f);  m.m32(0.0f);  m.m33(1.0f);
            }
            case VERTICAL_EW -> {
                m.m00(0.0f);  m.m01(1.0f);  m.m02(0.0f); m.m03(0.0f);
                m.m10(-1.0f); m.m11(0.0f);  m.m12(0.0f); m.m13(16.0f);
                m.m20(0.0f);  m.m21(0.0f);  m.m22(1.0f); m.m23(0.0f);
                m.m30(0.0f);  m.m31(0.0f);  m.m32(0.0f); m.m33(1.0f);
            }
            default -> {
            }
        }
        return QuadTransformers.applying(new Transformation(m));
    }
}
