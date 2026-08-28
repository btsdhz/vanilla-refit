package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum StairConnection implements StringRepresentable {
    NONE("none"),      // 普通竖 L
    BOTTOM("bottom"),  // 下拐角连接
    TOP("top"),        // 上拐角连接
    CONN_RIGHT("conn_right"),   // 单大面连接右（模型A）
    CONN_LEFT("conn_left"),     // 单大面连接左（模型B）
    CONN_DOUBLE("conn_double");  // 双大面连接（模型C）

    private final String name;

    StairConnection(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}