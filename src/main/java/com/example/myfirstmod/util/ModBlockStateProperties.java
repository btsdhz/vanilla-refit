package com.example.myfirstmod.util;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class ModBlockStateProperties {
    public static final EnumProperty<VerticalSlabMode> MODE =
            EnumProperty.create("btsdhz_mode", VerticalSlabMode.class);

    public static final EnumProperty<FluidType> FLUID_TYPE =
            EnumProperty.create("btsdhz_fluid", FluidType.class);

    /**
     * 原版台阶 / 楼梯用的"除水以外的液体"（目前只有熔岩），2 个值。
     *
     * <p>水改用原版自己的 {@code waterlogged} 存（见 {@link ExtraFluid} 的说明）：这样状态数从
     * "3 值液体 × 2 值 waterlogged（其中一个恒为 false）"降到"2 值 × 2 值"，
     * 台阶每块 54 → 36、楼梯每块 480 → 320；而且**卸掉模组后水还在**。
     * 属性名与原来那个 3 值属性相同（{@code btsdhz_fluid}），所以旧存档/结构里
     * {@code btsdhz_fluid=none|lava} 仍然认；只有 {@code =water} 需要改成 {@code waterlogged=true}。
     * 本模组自己的混合半砖没有原版 {@code waterlogged}，仍然用上面那个 3 值属性。
     */
    public static final EnumProperty<ExtraFluid> EXTRA_FLUID =
            EnumProperty.create("btsdhz_fluid", ExtraFluid.class);

    // 竖楼梯标记：false=原版平放楼梯，true=竖楼梯。
    //
    // 必须用默认 false 的属性：原版 BooleanProperty 的取值顺序是 [true, false]，
    // 而“缺属性”会按第一个取值解析。中途加入模组读取旧存档时，存档里没有 btsdhz_vertical，
    // 用 BooleanProperty 就会把原本的平放楼梯全部解析成竖楼梯（形态凭空改变）。
    public static final Property<Boolean> VERTICAL =
            new DefaultFalseBooleanProperty("btsdhz_vertical");

    // 竖楼梯的连接形态不再单独占一个属性：它借用原版 shape 的 5 个值当载体，
    // 见 StairConnection（每块楼梯因此从 2,880 个状态降到 480 个）。

    // 火把/灵魂火把（含红石火把）是否放在“下台阶”（下半台阶）上。
    // true 时模型与碰撞箱整体下移半格，与下台阶的上表面贴合。
    // 用默认 false 的属性，避免旧存档/未显式保存该属性的方块读取时默认成 true（误判下移）。
    // 墙/栅栏/灯笼改用 SlabOffset 三值枚举（它们还有“贴在上台阶下”的情况），见下面。
    public static final Property<Boolean> ON_SLAB =
            new DefaultFalseBooleanProperty("btsdhz_on_slab");

    // 墙/栅栏/灯笼与台阶的贴合情况：不贴 / 贴下台阶（下移半格）/ 贴上台阶下（上移半格）。
    // 以前是两个布尔（ON_SLAB + UNDER_TOP_SLAB），但两者不会同时为真，合并后 4 个声明值变 3 个。
    public static final EnumProperty<SlabOffset> SLAB_OFFSET =
            EnumProperty.create("btsdhz_slab_offset", SlabOffset.class);

    // 玻璃板四角水平面片：为真时在玻璃板中间高度（8 像素）渲染一块 1/4 方块面大小的水平玻璃面，
    // 位置靠向该属性表示的方向（东北/东南/西北/西南）。
    // 与其它“附加形态”属性一样用默认 false 的属性：缺失该属性时保持原版玻璃板外观。
    public static final Property<Boolean> PANE_NORTH_EAST =
            new DefaultFalseBooleanProperty("btsdhz_ne");

    public static final Property<Boolean> PANE_SOUTH_EAST =
            new DefaultFalseBooleanProperty("btsdhz_se");

    public static final Property<Boolean> PANE_NORTH_WEST =
            new DefaultFalseBooleanProperty("btsdhz_nw");

    public static final Property<Boolean> PANE_SOUTH_WEST =
            new DefaultFalseBooleanProperty("btsdhz_sw");

    // 玻璃板四个方向的上半部分（北上/东上/南上/西上）。
    // 原版的 north/east/south/west 属性不改名，含义改为对应方向的“下半部分”，
    // 两者都为真时该方向就是一整面 16 像素高的玻璃。
    // 与其它附加形态属性一样默认 false，缺失该属性时保持原版玻璃板外观。
    public static final Property<Boolean> PANE_NORTH_UP =
            new DefaultFalseBooleanProperty("btsdhz_north_up");

    public static final Property<Boolean> PANE_EAST_UP =
            new DefaultFalseBooleanProperty("btsdhz_east_up");

    public static final Property<Boolean> PANE_SOUTH_UP =
            new DefaultFalseBooleanProperty("btsdhz_south_up");

    public static final Property<Boolean> PANE_WEST_UP =
            new DefaultFalseBooleanProperty("btsdhz_west_up");
}
