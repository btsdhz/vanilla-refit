package com.example.myfirstmod.client;

import com.example.myfirstmod.client.model.SlabbedLoweringModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
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

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        List<ModelResourceLocation> toWrap = new ArrayList<>();
        for (ModelResourceLocation loc : event.getModels().keySet()) {
            if (loc.getVariant().contains(ON_SLAB_TRUE)) {
                toWrap.add(loc);
            }
        }
        for (ModelResourceLocation loc : toWrap) {
            BakedModel original = event.getModels().get(loc);
            if (original != null) {
                event.getModels().put(loc, new SlabbedLoweringModel(original));
            }
        }
    }
}
