package com.example.myfirstmod.util;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

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
    // 用默认 false 的属性，避免旧存档/未显式保存该属性的方块读取时默认成 true（误判下移）。
    public static final Property<Boolean> ON_SLAB =
            new DefaultFalseBooleanProperty("btsdhz_on_slab");

    // 灯笼/灵魂灯笼/墙是否放在“上台阶”（上半台阶）下方。
    // true 时模型与碰撞箱整体上移半格，贴合上台阶的底面。
    // 与 ON_SLAB 同理用默认 false 的属性：结构模板等只按属性名读取旧数据时，
    // 缺失该属性不会被误判成“上移形态”。
    public static final Property<Boolean> UNDER_TOP_SLAB =
            new DefaultFalseBooleanProperty("btsdhz_under_top_slab");
}
