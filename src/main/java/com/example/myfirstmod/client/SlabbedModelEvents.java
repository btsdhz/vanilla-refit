package com.example.myfirstmod.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.example.myfirstmod.client.model.SlabbedLoweringModel;
import com.example.myfirstmod.client.model.SlabbedRaisingModel;
import com.example.myfirstmod.client.model.MixedSlabModel;
import com.example.myfirstmod.client.model.FenceStepModel;
import com.example.myfirstmod.client.model.ModelSprites;
import com.example.myfirstmod.client.model.RetexturedTemplateModel;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.VerticalSlabMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Matrix4f;

/**
 * 客户端模型烘焙结果修改：把任何方块状态为 btsdhz_on_slab=true 的模型包装成“下移 0.5 格”。
 *
 * 这样无论方块是原版 4 种，还是其它模组继承 TorchBlock/LanternBlock 的自定义方块，
 * 只要它的状态里 btsdhz_on_slab=true（即放在普通下半台阶上），视觉都会下移贴齐。
 */
public class SlabbedModelEvents {

    private static final Logger LOGGER = LogUtils.getLogger();


    // 该事件只在客户端、资源重载烘焙时触发；此处仅包装 BakedModel，不访问客户端对象。
    private static final String ON_SLAB_TRUE = "btsdhz_on_slab=true";                      // 火把类
    private static final String SLAB_OFFSET_LOWERED = "btsdhz_slab_offset=lowered";        // 墙/栅栏/灯笼
    private static final String SLAB_OFFSET_RAISED = "btsdhz_slab_offset=raised";

    private static final String MIXED_SLAB_PATH = "merged_slab";

    /**
     * 手工建模的特例：这些方块的竖形态是本模组手工做的多纹理模型，运行时**不接管**。
     * （平滑石台阶 + 砂岩家族 4 个台阶 + 砂岩家族 4 个楼梯）
     */
    private static final Set<ResourceLocation> HAND_AUTH = Set.of(
            mcLoc("smooth_stone_slab"),
            mcLoc("sandstone_slab"),
            mcLoc("cut_sandstone_slab"),
            mcLoc("red_sandstone_slab"),
            mcLoc("cut_red_sandstone_slab"),
            mcLoc("sandstone_stairs"),
            mcLoc("red_sandstone_stairs"),
            mcLoc("smooth_sandstone_stairs"),
            mcLoc("smooth_red_sandstone_stairs")
    );

    /** 模板载体：它们的静态模型就是运行时用的几何模板，不接管。 */
    private static final Set<ResourceLocation> TEMPLATE_CARRIERS = Set.of(
            mcLoc("oak_slab"),
            mcLoc("oak_stairs")
    );

    private static ResourceLocation mcLoc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** 该方块是否由运行时接管竖形态模型（手工特例、模板载体、本模组自己的方块都不接管）。 */
    private static boolean isRuntimeWrapped(ResourceLocation blockId) {
        return !HAND_AUTH.contains(blockId)
                && !TEMPLATE_CARRIERS.contains(blockId)
                && !blockId.getNamespace().equals("btsdhz_original");
    }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 其它模组的台阶：竖形态没有预生成模型，用"旋转它自己的平放模型"来补（配置开关控制）
        wrapModdedSlabModels(event);
        // 其它模组的楼梯：竖形态用"本模组的模板几何 + 对方的贴图"来补
        wrapModdedStairModels(event);

        // 栅栏：台阶侧的横杆要按邻居高低实时修正，必须在“上/下台阶位移包装”的内层，
        // 所以先包装栅栏模型（它按位置取 ModelData 决定改哪半根横杆），再交给下面的位移包装。
        wrapFenceModels(event);

        List<ModelResourceLocation> toLower = new ArrayList<>();
        List<ModelResourceLocation> toRaise = new ArrayList<>();
        for (ModelResourceLocation loc : event.getModels().keySet()) {
            String variant = loc.getVariant();
            if (variant.contains(ON_SLAB_TRUE) || variant.contains(SLAB_OFFSET_LOWERED)) {
                toLower.add(loc);
            } else if (variant.contains(SLAB_OFFSET_RAISED)) {
                toRaise.add(loc);
            }
        }
        for (ModelResourceLocation loc : toLower) {
            BakedModel original = event.getModels().get(loc);
            if (original != null) {
                event.getModels().put(loc, new SlabbedLoweringModel(original));
            }
        }
        for (ModelResourceLocation loc : toRaise) {
            BakedModel original = event.getModels().get(loc);
            if (original != null) {
                event.getModels().put(loc, new SlabbedRaisingModel(original));
            }
        }

        // 混合半砖：把模型包装成可读取方块实体中两块材质并动态合并的模型
        for (ModelResourceLocation loc : event.getModels().keySet()) {
            if (loc.id().getNamespace().equals("btsdhz_original") && loc.id().getPath().equals(MIXED_SLAB_PATH)) {
                BakedModel original = event.getModels().get(loc);
                if (original != null) {
                    event.getModels().put(loc, new MixedSlabModel(original));
                }
            }
        }
    }

    /**
     * 给其它模组的台阶补上竖形态模型（见 {@link RetexturedTemplateModel}）。
     *
     * <p>做法和楼梯完全一致：模板取原版橡木台阶**同状态**的烘焙模型（几何、UV、剔除标记
     * 都是按竖放姿势手工调好的），只把贴图换成目标台阶自己的。
     *
     * <p>为什么不旋转对方自己的平放模型：旋转会把 UV 和 cullface 一起带走，结果是贴图转向、
     * 环境光遮蔽上下颠倒、剔除落到错误方向（试过，问题就出在这）。用模板则完全不碰几何。
     */
    private static void wrapModdedSlabModels(ModelEvent.ModifyBakingResult event) {
        Block template = Blocks.OAK_SLAB;
        ResourceLocation templateId = BuiltInRegistries.BLOCK.getKey(template);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof SlabBlock)) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (!isRuntimeWrapped(blockId)) {
                continue;   // 手工特例（砂岩家族等）/ 模板载体（橡木）/ 本模组自己的方块
            }
            if (!SlabSupport.isSupportedSlab(block)) {
                LOGGER.debug("[原版精修/模型] 跳过台阶 {}：配置没打开 allowModdedSlabs", blockId);
                continue;
            }
            BlockState definition = block.getStateDefinition().any();
            if (!definition.hasProperty(ModBlockStateProperties.MODE)
                    || !definition.hasProperty(SlabBlock.TYPE)) {
                LOGGER.debug("[原版精修/模型] 跳过台阶 {}：状态定义里没有本模组的属性", blockId);
                continue;
            }
            BlockState flatState = definition.setValue(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            BakedModel flat = event.getModels().get(BlockModelShaper.stateToModelLocation(blockId, flatState));
            if (flat == null) {
                LOGGER.debug("[原版精修/模型] 跳过台阶 {}：找不到平放状态的模型（烘焙表里没有这个 key）", blockId);
                continue;
            }
            ModelSprites sprites = ModelSprites.of(flat, flatState);
            if (sprites == null) {
                LOGGER.debug("[原版精修/模型] 跳过台阶 {}：问不出顶/底/侧贴图", blockId);
                continue;
            }
            // 破坏方块时的碎屑用对方自己的粒子贴图（否则会跟着模板变成橡木）
            TextureAtlasSprite particle = flat.getParticleIcon(ModelData.EMPTY);
            if (particle == null) {
                particle = sprites.side();
            }
            TextureAtlasSprite particleSprite = particle;
            int wrappedCount = 0;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB) {
                    continue;
                }
                // 同状态的原版橡木台阶模型 = 本模组的竖台阶模板（几何、UV、剔除标记都在这儿）
                BlockState donor = template.defaultBlockState()
                        .setValue(ModBlockStateProperties.MODE, state.getValue(ModBlockStateProperties.MODE))
                        .setValue(SlabBlock.TYPE, state.getValue(SlabBlock.TYPE));
                BakedModel geometry = event.getModels().get(BlockModelShaper.stateToModelLocation(templateId, donor));
                if (geometry == null) {
                    continue;
                }
                event.getModels().put(BlockModelShaper.stateToModelLocation(blockId, state),
                        RetexturedTemplateModel.create(geometry, sprites, particleSprite));
                wrappedCount++;
            }
            LOGGER.debug("[原版精修/模型] 台阶 {}：包裹了 {} 个竖状态", blockId, wrappedCount);
        }
    }

    /**
     * 给其它模组的楼梯补上竖形态模型（见 {@link RetexturedTemplateModel}）。
     *
     * <p>竖楼梯的连接形态是手工拼的盒子，没法靠旋转对方模型得到，所以这里借原版楼梯（橡木）
     * 竖形态的烘焙模型当"几何 + 角度"模板——它已经按 {@code StairShapeModels} 的表转好了，
     * 这里只把贴图换成目标楼梯自己的。目标状态与模板状态用同一组 (connection, facing, half)，
     * 角度自然一致。
     */
    private static void wrapModdedStairModels(ModelEvent.ModifyBakingResult event) {
        Block template = Blocks.OAK_STAIRS;
        ResourceLocation templateId = BuiltInRegistries.BLOCK.getKey(template);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof StairBlock)) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (!isRuntimeWrapped(blockId)) {
                continue;   // 手工特例（砂岩家族等）/ 模板载体（橡木）/ 本模组自己的方块
            }
            if (!SlabSupport.isSupportedStair(block)) {
                LOGGER.debug("[原版精修/模型] 跳过楼梯 {}：配置没打开 allowModdedStairs", blockId);
                continue;
            }
            BlockState definition = block.getStateDefinition().any();
            if (!definition.hasProperty(ModBlockStateProperties.VERTICAL)
                    || !definition.hasProperty(StairBlock.FACING)
                    || !definition.hasProperty(StairBlock.HALF)
                    || !definition.hasProperty(StairBlock.SHAPE)) {
                LOGGER.debug("[原版精修/模型] 跳过楼梯 {}：状态定义里没有本模组的属性", blockId);
                continue;
            }
            BlockState flatState = definition.setValue(ModBlockStateProperties.VERTICAL, false);
            BakedModel flat = event.getModels().get(BlockModelShaper.stateToModelLocation(blockId, flatState));
            if (flat == null) {
                LOGGER.debug("[原版精修/模型] 跳过楼梯 {}：找不到平放状态的模型", blockId);
                continue;
            }
            ModelSprites sprites = ModelSprites.of(flat, flatState);
            if (sprites == null) {
                LOGGER.debug("[原版精修/模型] 跳过楼梯 {}：问不出顶/底/侧贴图", blockId);
                continue;
            }
            // 破坏方块时的碎屑用对方自己的粒子贴图（否则会跟着模板变成橡木）
            TextureAtlasSprite particle = flat.getParticleIcon(ModelData.EMPTY);
            if (particle == null) {
                particle = sprites.side();
            }
            TextureAtlasSprite particleSprite = particle;
            int wrappedCount = 0;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.getValue(ModBlockStateProperties.VERTICAL)) {
                    continue;
                }
                BlockState donor = template.defaultBlockState()
                        .setValue(ModBlockStateProperties.VERTICAL, true)
                        .setValue(StairBlock.FACING, state.getValue(StairBlock.FACING))
                        .setValue(StairBlock.HALF, state.getValue(StairBlock.HALF))
                        // 连接形态的载体就是 shape，直接照抄即可
                        .setValue(StairBlock.SHAPE, state.getValue(StairBlock.SHAPE));
                BakedModel geometry = event.getModels().get(BlockModelShaper.stateToModelLocation(templateId, donor));
                if (geometry == null) {
                    continue;
                }
                event.getModels().put(BlockModelShaper.stateToModelLocation(blockId, state),
                        RetexturedTemplateModel.create(geometry, sprites, particleSprite));
                wrappedCount++;
            }
            LOGGER.debug("[原版精修/模型] 楼梯 {}：包裹了 {} 个竖状态", blockId, wrappedCount);
        }
    }

    /** 把每个栅栏方块状态的模型包一层 {@link FenceStepModel}（没台阶侧时行为与原模型完全一致）。 */
    private static void wrapFenceModels(ModelEvent.ModifyBakingResult event) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof FenceBlock)) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockId, state);
                BakedModel original = event.getModels().get(location);
                if (original != null && !(original instanceof FenceStepModel)) {
                    event.getModels().put(location, new FenceStepModel(original));
                }
            }
        }
    }
}
