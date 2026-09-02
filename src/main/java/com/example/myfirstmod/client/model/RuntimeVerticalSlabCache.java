package com.example.myfirstmod.client.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 运行期生成的“竖半砖模型”缓存。放在非 mixin 类里，便于在模型重新烘焙时清理，
 * 也避免 mixin 类中出现非 private 的静态方法（Mixin 不允许）。
 */
public final class RuntimeVerticalSlabCache {

    private static final Map<BlockState, BakedModel> CACHE = new ConcurrentHashMap<>();

    private RuntimeVerticalSlabCache() {
    }

    public static BakedModel get(BlockState state) {
        return CACHE.get(state);
    }

    public static void put(BlockState state, BakedModel model) {
        CACHE.put(state, model);
    }

    /** 资源重载 / 模型重新烘焙时清空，避免引用到过期模型。 */
    public static void clear() {
        CACHE.clear();
    }
}
