package com.example.myfirstmod.client.model;

import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 混合半砖的客户端模型：从方块实体读取两块半砖材质，按其朝向动态合并渲染。
 *
 * 之所以要运行时合并而不是烘焙成静态模型，是因为“哪两种材质组合”千变万化，无法预烘焙。
 * 但这里仍然走 BakedModel.getQuads 的原版管线（不是每帧递归渲染方块实体），性能好。
 */
public class MixedSlabModel extends BakedModelWrapper<BakedModel> {

    public MixedSlabModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MixedSlabBlockEntity mixed) {
            return modelData.derive()
                    .with(MixedSlabBlockEntity.MERGED_SLAB_A, mixed.getFirstSlab())
                    .with(MixedSlabBlockEntity.MERGED_SLAB_B, mixed.getSecondSlab())
                    .build();
        }
        return modelData;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null || data == null) {
            return super.getQuads(state, side, random, data, renderType);
        }
        Block slabA = data.get(MixedSlabBlockEntity.MERGED_SLAB_A);
        Block slabB = data.get(MixedSlabBlockEntity.MERGED_SLAB_B);
        if (slabA == null || slabB == null) {
            return super.getQuads(state, side, random, data, renderType);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return super.getQuads(state, side, random, data, renderType);
        }

        BlockState stateA = buildHalfState(slabA, state, true);
        BlockState stateB = buildHalfState(slabB, state, false);
        BakedModel modelA = mc.getBlockRenderer().getBlockModel(stateA);
        BakedModel modelB = mc.getBlockRenderer().getBlockModel(stateB);

        if (renderType == null) {
            // 非区块渲染（如工具提示/inventory）：取全部两块
            List<BakedQuad> merged = new ArrayList<>();
            merged.addAll(modelA.getQuads(stateA, side, random, ModelData.EMPTY, null));
            merged.addAll(modelB.getQuads(stateB, side, random, ModelData.EMPTY, null));
            return merged;
        }

        // 区块渲染：只取属于当前 renderType 的那块半砖的几何，避免不同图层错位叠加
        List<BakedQuad> merged = new ArrayList<>();
        if (modelA.getRenderTypes(stateA, random, ModelData.EMPTY).contains(renderType)) {
            merged.addAll(modelA.getQuads(stateA, side, random, ModelData.EMPTY, renderType));
        }
        if (modelB.getRenderTypes(stateB, random, ModelData.EMPTY).contains(renderType)) {
            merged.addAll(modelB.getQuads(stateB, side, random, ModelData.EMPTY, renderType));
        }
        return merged;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        if (state == null || data == null) {
            return super.getRenderTypes(state, random, data);
        }
        Block slabA = data.get(MixedSlabBlockEntity.MERGED_SLAB_A);
        Block slabB = data.get(MixedSlabBlockEntity.MERGED_SLAB_B);
        if (slabA == null || slabB == null) {
            return super.getRenderTypes(state, random, data);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return super.getRenderTypes(state, random, data);
        }
        BlockState stateA = buildHalfState(slabA, state, true);
        BlockState stateB = buildHalfState(slabB, state, false);
        BakedModel modelA = mc.getBlockRenderer().getBlockModel(stateA);
        BakedModel modelB = mc.getBlockRenderer().getBlockModel(stateB);
        return ChunkRenderTypeSet.union(
                modelA.getRenderTypes(stateA, random, ModelData.EMPTY),
                modelB.getRenderTypes(stateB, random, ModelData.EMPTY));
    }

    /**
     * 把一个材质 Block 转成“在混合半砖里应该呈现的那一半”的 BlockState。
     * 具体朝向由混合半砖状态里的 MODE / TYPE 决定。
     *
     *  - MODE=SLAB：BOTTOM→下半(BOTTOM)，TOP→上半(TOP)；isFirst 为真表示这块是"下半/北半/西半"。
     *  - MODE=VERTICAL_NS：BOTTOM→北半，TOP→南半；原版无该竖半砖状态，需用本模组的竖半砖状态表达。
     *  - MODE=VERTICAL_EW：BOTTOM→西半，TOP→东半。
     */
    private BlockState buildHalfState(Block slab, BlockState mixedState, boolean isFirst) {
        VerticalSlabMode mode = mixedState.getValue(ModBlockStateProperties.MODE);
        SlabType type = mixedState.getValue(SlabBlock.TYPE);
        FluidType fluid = mixedState.getValue(ModBlockStateProperties.FLUID_TYPE);

        BlockState base = slab.defaultBlockState();
        if (mode == VerticalSlabMode.SLAB) {
            // 水平堆叠：A=下半，B=上半
            SlabType half = isFirst ? SlabType.BOTTOM : SlabType.TOP;
            if (base.hasProperty(SlabBlock.TYPE)) {
                return base.setValue(SlabBlock.TYPE, half);
            }
            return base;
        }

        // 竖直拼合：使用本模组的竖半砖属性体系（复用 MODE 表达方向）
        // 第一块（A）是"北/西"侧，即 MODE+ 朝向为正向；第二块（B）是反侧。
        VerticalSlabMode verticalMode = mode;
        if (base.hasProperty(ModBlockStateProperties.MODE)) {
            return base.setValue(ModBlockStateProperties.MODE, verticalMode)
                    .setValue(SlabBlock.TYPE, isFirst ? SlabType.BOTTOM : SlabType.TOP)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
        }
        return base;
    }
}
