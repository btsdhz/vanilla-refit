package com.example.myfirstmod.util;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class ModBlockStateProperties {
    public static final EnumProperty<VerticalSlabMode> MODE =
            EnumProperty.create("btsdhz_mode", VerticalSlabMode.class);

    public static final EnumProperty<FluidType> FLUID_TYPE =
            EnumProperty.create("btsdhz_fluid", FluidType.class);

    // 竖楼梯标记：false=原版平放楼梯，true=竖楼梯
    public static final BooleanProperty VERTICAL =
            BooleanProperty.create("btsdhz_vertical");

    // 竖楼梯连接形态：NONE=普通L / BOTTOM=下拐角 / TOP=上拐角
    public static final EnumProperty<StairConnection> STAIR_CONNECTION =
            EnumProperty.create("btsdhz_connection", StairConnection.class);

    // 火把/灵魂火把/灯笼/灵魂灯笼是否放在“下台阶”（下半台阶）上。
    // true 时模型与碰撞箱整体下移半格，与下台阶的上表面贴合。
    public static final BooleanProperty ON_SLAB =
            BooleanProperty.create("btsdhz_on_slab");
}
