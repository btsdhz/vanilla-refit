package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 台阶 / 楼梯里"<b>除水以外</b>的液体"（目前只有熔岩）。
 *
 * <p>为什么水不在这里：原版方块本来就带 {@code waterlogged} 布尔，水的存在由它表示。
 * 本属性只记除水以外的液体，<b>水 → 原版 {@code waterlogged}；其它液体 → 本模组这个 2 值属性</b>，
 * 两者在逻辑上互斥。这样做的结果：
 * <ul>
 *   <li>液体组合从 6 种降到 4 种（台阶每块 54 → 36、楼梯每块 480 → 320）；</li>
 *   <li><b>卸掉模组后水还在</b>：水存在原版属性里，不依赖本模组。</li>
 * </ul>
 *
 * <p>读写请一律走 {@link FluidHolder}，它负责在原版属性与本属性之间做互斥映射。
 */
public enum ExtraFluid implements StringRepresentable {
    NONE("none"),
    LAVA("lava");

    private final String name;

    ExtraFluid(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
