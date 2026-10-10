package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * "贴台阶"的三种情况（墙 / 栅栏 / 灯笼用）。
 *
 * <p>判定是"下台阶优先"：下方是下台阶 → 整体下移半格；否则上方是上台阶 → 整体上移半格；
 * 都不成立则不位移。三个值都可达，墙每块状态数 972、栅栏 96、灯笼 12。
 *
 * <p>{@link #NONE} 必须是第一个常量：缺属性时枚举按第一个常量解析
 * （和 {@code DefaultFalseBooleanProperty} 的道理一样），旧存档/结构模板里没有这个属性时
 * 才会退回"不位移"的原版外观。
 *
 * <p>火把类使用二值布尔 {@code btsdhz_on_slab}：它们只有"放在下台阶上"这一种情况。
 */
public enum SlabOffset implements StringRepresentable {
    NONE("none"),        // 不贴台阶
    LOWERED("lowered"),  // 贴在下台阶上：模型与碰撞箱整体下移半格
    RAISED("raised");    // 贴在上台阶下：模型与碰撞箱整体上移半格

    private final String name;

    SlabOffset(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
