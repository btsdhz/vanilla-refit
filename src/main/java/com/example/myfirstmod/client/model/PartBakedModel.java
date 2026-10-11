package com.example.myfirstmod.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * "一个模型 + 按状态挑片段"的通用模型：一个材质一个实例，所有状态共用。
 *
 * <p>原版的栅栏和墙用的是 multipart 方块状态：几条"什么条件用什么模型"的规则各自判断命中与否，
 * 游戏为**每一个状态**都烘焙出一个拼接结果模型，而且每次取四边形都要逐条判断规则、临时拼列表。
 * 这里的做法：把每个片段的四边形在烘焙期取好（按"6 个方向 + 不剔除组"分组），
 * 渲染时读状态算出一个小编号，直接取该编号预先拼好的列表。
 *
 * <p>片段本身、以及每个片段在哪个方向可以被邻居挡掉（剔除分组），都照抄原版烘焙结果，
 * 不做任何几何变换或重排。
 *
 * <p>拼好的结果按编号缓存（懒加载、无锁）：区块重建在多个线程上跑，撞车时两边算出的结果等价，
 * 后写入的覆盖先写入的即可。
 */
public class PartBakedModel extends BakedModelWrapper<BakedModel> {

    /** 分组总数：6 个方向 + 1 个"不参与剔除"。 */
    public static final int GROUPS = 7;

    /** 分组索引：方向用 ordinal，null（不参与剔除）用 6。 */
    public static final int UNCLASSIFIED_GROUP = 6;

    /** 一个片段：7 个分组的四边形，加上"这个编号下画不画它"。 */
    public record Part(List<List<BakedQuad>> buckets, IntPredicate enabled) {
    }

    private final ToIntFunction<BlockState> keyFunction;
    private final Part[] parts;
    private final AtomicReferenceArray<List<List<BakedQuad>>> byKey;

    public PartBakedModel(BakedModel body, int keyCount, ToIntFunction<BlockState> keyFunction, Part[] parts) {
        super(body);
        this.keyFunction = keyFunction;
        this.parts = parts;
        this.byKey = new AtomicReferenceArray<>(keyCount);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return collect(key(state), group(side));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return collect(key(state), group(side));
    }

    private int key(@Nullable BlockState state) {
        return state == null ? 0 : this.keyFunction.applyAsInt(state);
    }

    /** 该状态这一组要画哪些四边形：取按编号缓存好的拼装结果（第一次用到才拼）。 */
    private List<BakedQuad> collect(int key, int groupIndex) {
        List<List<BakedQuad>> groups = this.byKey.get(key);
        if (groups == null) {
            groups = merge(key);
            this.byKey.set(key, groups);
        }
        return groups.get(groupIndex);
    }

    /** 把一个编号的 7 个分组一次拼好（每个编号只会算一次，之后长期复用）。 */
    private List<List<BakedQuad>> merge(int key) {
        List<List<BakedQuad>> groups = new ArrayList<>(GROUPS);
        for (int groupIndex = 0; groupIndex < GROUPS; groupIndex++) {
            groups.add(mergeGroup(key, groupIndex));
        }
        return List.copyOf(groups);
    }

    /** 一个分组里所有启用片段的四边形；只有一份来源时直接复用那份列表（零拷贝）。 */
    private List<BakedQuad> mergeGroup(int key, int groupIndex) {
        int sources = 0;
        List<BakedQuad> only = null;
        for (Part part : this.parts) {
            if (!part.enabled().test(key)) {
                continue;
            }
            List<BakedQuad> quads = part.buckets().get(groupIndex);
            if (quads.isEmpty()) {
                continue;
            }
            sources++;
            only = quads;
        }
        if (sources == 0) {
            return List.of();
        }
        if (sources == 1) {
            return only;
        }
        List<BakedQuad> merged = new ArrayList<>();
        for (Part part : this.parts) {
            if (part.enabled().test(key)) {
                merged.addAll(part.buckets().get(groupIndex));
            }
        }
        return List.copyOf(merged);
    }

    private static int group(@Nullable Direction side) {
        return side == null ? UNCLASSIFIED_GROUP : side.ordinal();
    }
}
