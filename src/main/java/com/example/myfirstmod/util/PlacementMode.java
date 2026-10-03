package com.example.myfirstmod.util;

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
    /** 原版逻辑：完全走原版放置。 */
    VANILLA("vanilla");

    private final String name;

    PlacementMode(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    /** 目前每种方块只有两个模式，切换就是取反；以后加了逻辑 2 再改成轮转。 */
    public PlacementMode next() {
        return this == MOD ? VANILLA : MOD;
    }

    public static PlacementMode byOrdinal(int ordinal) {
        PlacementMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOD;
    }
}
