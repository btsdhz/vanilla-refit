package com.example.myfirstmod.util;

import java.util.List;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 台阶/楼梯的放置逻辑。
 *
 * <p>台阶和楼梯各存一份、互不影响（可以只把台阶切回原版而楼梯保持本模组逻辑）。
 */
public enum PlacementMode implements StringRepresentable {
    /** 本模组逻辑 1：竖放/混合台阶等（见 BlockItemMixin、SlabBlockMixin、StairBlockMixin）。 */
    MOD("mod"),
    /**
     * 本模组逻辑 2（目前只有台阶）：在逻辑 1 的基础上，把面中央抠出一个正方形，
     * 点正方形内放置“平放台阶”，正方形之外仍然竖放。正方形大小见配置文件
     * {@code slabCenterSquareRatio}。
     */
    MOD_2("mod2"),
    /**
     * 本模组逻辑 3（目前只有台阶）：紧贴点击的那个面放置——
     * 点顶面放（贴在下面的）平放台阶，点侧面放贴着该侧的竖台阶。不需要辅助线。
     */
    MOD_3("mod3"),
    /** 原版逻辑：完全走原版放置。 */
    VANILLA("vanilla");

    /** 台阶可选的模式顺序（按切换键轮转）。 */
    public static final List<PlacementMode> SLAB_ORDER = List.of(MOD, MOD_2, MOD_3, VANILLA);
    /** 楼梯目前还没有逻辑 2。 */
    public static final List<PlacementMode> STAIR_ORDER = List.of(MOD, VANILLA);

    private final String name;

    PlacementMode(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    /** 按切换键轮转；以后再加逻辑 3 就往对应 ORDER 列表里加一项即可。 */
    public PlacementMode next(boolean forStairs) {
        List<PlacementMode> order = forStairs ? STAIR_ORDER : SLAB_ORDER;
        int index = order.indexOf(this);
        return order.get((index + 1) % order.size());
    }

    public static PlacementMode byOrdinal(int ordinal) {
        PlacementMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOD;
    }
}
