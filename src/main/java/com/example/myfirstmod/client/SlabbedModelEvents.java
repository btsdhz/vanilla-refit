package com.example.myfirstmod.client;

import com.example.myfirstmod.client.model.SlabbedLoweringModel;
import com.example.myfirstmod.client.model.SlabbedRaisingModel;
import com.example.myfirstmod.client.model.MixedSlabModel;
import com.example.myfirstmod.client.model.FenceStepModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * 客户端模型烘焙结果修改：把任何方块状态为 btsdhz_on_slab=true 的模型包装成“下移 0.5 格”。
 *
 * 这样无论方块是原版 4 种，还是其它模组继承 TorchBlock/LanternBlock 的自定义方块，
 * 只要它的状态里 btsdhz_on_slab=true（即放在普通下半台阶上），视觉都会下移贴齐。
 */
public class SlabbedModelEvents {

    // 该事件只在客户端、资源重载烘焙时触发；此处仅包装 BakedModel，不访问客户端对象。
    private static final String ON_SLAB_TRUE = "btsdhz_on_slab=true";
    private static final String UNDER_TOP_SLAB_TRUE = "btsdhz_under_top_slab=true";

    private static final String MIXED_SLAB_PATH = "merged_slab";

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 栅栏：台阶侧的横杆要按邻居高低实时修正，必须在“上/下台阶位移包装”的内层，
        // 所以先包装栅栏模型（它按位置取 ModelData 决定改哪半根横杆），再交给下面的位移包装。
        wrapFenceModels(event);

        List<ModelResourceLocation> toLower = new ArrayList<>();
        List<ModelResourceLocation> toRaise = new ArrayList<>();
        for (ModelResourceLocation loc : event.getModels().keySet()) {
            String variant = loc.getVariant();
            if (variant.contains(ON_SLAB_TRUE)) {
                toLower.add(loc);
            } else if (variant.contains(UNDER_TOP_SLAB_TRUE)) {
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
