package com.example.myfirstmod.client.model;

import com.example.myfirstmod.util.StateIntCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 玻璃板 / 铁栏杆的“运行时拼装”模型：一个材质只有一个模型实例，所有状态共用。
 *
 * <p>背景：一块玻璃板的外观 = 12 个部件（4 个方向的上下两半 + 4 个角上面片）加上
 * “什么都没连”时那根棍，一共 8,192 个状态。原版做法是给每个状态各烘焙一份模型，
 * 实测这部分按状态展开的模型数据是内存的大头（几百 MB），而状态里那 12 个属性
 * 表达的其实只是“这些固定几何要不要画”。
 *
 * <p>这里：blockstate 只留一个变体（不写任何属性条件），三个子部件模型的四边形
 * 在烘焙期取好并按“部件 + 朝向分组”存起来，渲染时按状态的属性拼出这一次要画的四边形。
 * 属性、连接规则、碰撞箱、状态数都不变，只是不再按状态各存一份模型数据。
 *
 * <p>分组索引沿用原版的剔除分组：0~5 对应 {@link Direction#values()}（带 cullface 的面），
 * 6 是“不参与剔除”那一组（渲染器每帧都画）。部件本身都不带 cullface，所以通常只在第 6 组里。
 *
 * <p>拼好的结果按“12 位部件掩码”长期缓存（见 {@link #byMask}）：一个掩码只拼一次，之后每次
 * {@code getQuads} 只剩“读 12 个属性 + 取一次缓存”，不再新建任何集合——渲染路径因此没有分配。
 * 拼装发生在区块网格重建的渲染线程上，可能并发，所以缓存用无锁的 {@link AtomicReferenceArray}，
 * 撞车时两边都算出正确结果、后写入的覆盖先写入的即可。
 */
public class PaneBakedModel extends BakedModelWrapper<BakedModel> {

    /** 分组总数：6 个方向 + 1 个“不参与剔除”。 */
    public static final int GROUPS = 7;

    /** 分组索引：方向用 ordinal，null（不参与剔除）用 6。 */
    public static final int UNCLASSIFIED_GROUP = 6;

    /** 12 个部件属性，顺序与 {@code pieces} 里每 7 个一组一一对应。 */
    private final List<Property<Boolean>> properties;

    /** 部件四边形：索引 = 部件序号 * 7 + 分组。 */
    private final List<List<BakedQuad>> pieces;

    /** 12 个部件全为假时才渲染的“棍”（立柱 + 四个柱面），同样是 7 个分组。 */
    private final List<List<BakedQuad>> stick;

    /**
     * 按部件掩码缓存的“每个分组要画哪些四边形”：索引是掩码本身，值是该掩码的 7 个分组。
     * 掩码 0（＝那根棍）不走这里，直接用 {@link #stick}。
     * 12 个属性 → 4,096 个槽位（约 16 KB 引用数组/材质，只有实际出现过的掩码会被填上内容）。
     */
    private final AtomicReferenceArray<List<List<BakedQuad>>> byMask;

    /**
     * “状态 → 12 位掩码”的缓存。原版重建区块网格时对**同一个状态**连续问 6 个方向 + 1 次不带方向，
     * 7 次调用读的是同一批属性；缓存之后只算一遍（详见 {@link com.example.myfirstmod.util.StateIntCache}）。
     */
    private final StateIntCache masks;

    public PaneBakedModel(BakedModel body, List<Property<Boolean>> properties,
                          List<List<BakedQuad>> pieces, List<List<BakedQuad>> stick) {
        super(body);
        this.properties = properties;
        this.pieces = pieces;
        this.stick = stick;
        this.byMask = new AtomicReferenceArray<>(1 << properties.size());
        this.masks = new StateIntCache(state -> enabledPieces(state, properties));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return collect(state, group(side));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return collect(state, group(side));
    }

    /** 该状态这一组要画哪些四边形：全部部件为假就是那根棍，否则取按掩码缓存好的结果。 */
    private List<BakedQuad> collect(@Nullable BlockState state, int groupIndex) {
        int mask = state == null ? 0 : this.masks.get(state);
        if (mask == 0) {
            return this.stick.get(groupIndex);
        }
        List<List<BakedQuad>> groups = this.byMask.get(mask);
        if (groups == null) {
            groups = mergeGroups(mask);
            this.byMask.set(mask, groups);
        }
        return groups.get(groupIndex);
    }

    /** 把一个掩码的 7 个分组一次拼好（每个掩码只会算一次，之后长期复用）。 */
    private List<List<BakedQuad>> mergeGroups(int mask) {
        List<List<BakedQuad>> groups = new ArrayList<>(GROUPS);
        for (int groupIndex = 0; groupIndex < GROUPS; groupIndex++) {
            groups.add(mergeGroup(mask, groupIndex));
        }
        return List.copyOf(groups);
    }

    /**
     * 一个分组里启用部件们的四边形：只有一个部件有货时直接返回那份共享列表（零拷贝），
     * 多个部件才合并成一份新列表——合并结果会被 {@link #byMask} 缓存，不在每次调用时重复。
     */
    private List<BakedQuad> mergeGroup(int mask, int groupIndex) {
        List<BakedQuad> only = null;
        int sources = 0;
        for (int piece = 0; piece < this.properties.size(); piece++) {
            if ((mask & (1 << piece)) == 0) {
                continue;
            }
            List<BakedQuad> quads = this.pieces.get(piece * GROUPS + groupIndex);
            if (quads.isEmpty()) {
                continue;
            }
            only = quads;
            sources++;
        }
        if (sources == 0) {
            return List.of();
        }
        if (sources == 1) {
            return only;
        }
        List<BakedQuad> merged = new ArrayList<>();
        for (int piece = 0; piece < this.properties.size(); piece++) {
            if ((mask & (1 << piece)) != 0) {
                merged.addAll(this.pieces.get(piece * GROUPS + groupIndex));
            }
        }
        return List.copyOf(merged);
    }

    /** 状态里 12 个部件属性的位掩码；缺少属性时按“全假”处理（＝原版那根棍）。 */
    private static int enabledPieces(BlockState state, List<Property<Boolean>> properties) {
        if (!state.hasProperty(properties.get(properties.size() - 1))) {
            return 0;   // 不是本模组支持的玻璃板（少了附加属性），别 getValue 一个不存在的属性
        }
        int bits = 0;
        for (int i = 0; i < properties.size(); i++) {
            if (state.getValue(properties.get(i))) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    private static int group(@Nullable Direction side) {
        return side == null ? UNCLASSIFIED_GROUP : side.ordinal();
    }
}
