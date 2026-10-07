package com.example.myfirstmod.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组配置文件。
 * 使用原版 NeoForge 的 ModConfigSpec，会在 config/ 目录下生成
 * btsdhz_original-common.toml 之类的 TOML 配置文件。
 */
public class BtsdhzConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 开关：火把/灯笼等下台阶贴合功能。
     *
     * 开启时，普通火把、灵魂火把、灯笼、灵魂灯笼放置在“下台阶”（下半台阶）上时，
     * 其模型与碰撞箱会整体下移半格，使它们与下台阶的上表面完全贴合，不再悬空。
     * 关闭时则使用原版行为（竖放在台阶上方，略微悬空）。
     */
    public static final ModConfigSpec.BooleanValue TORCH_LANTERN_ON_SLAB =
            BUILDER
                    .comment("是否允许火把、灵魂火把、灯笼、灵魂灯笼在“下台阶”上时贴合台阶表面。")
                    .comment("true（默认）：模型与碰撞箱整体下移半格，与下台阶上表面对齐。")
                    .comment("false：恢复原版行为，火把/灯笼悬空在台阶上方半格。")
                    .define("allowTorchLanternOnSlab", true);

    /**
     * 开关：灯笼悬挂“上台阶”下功能。
     *
     * 开启时，普通灯笼、灵魂灯笼（悬挂形态）放置于“上台阶”（上半台阶）下方时，
     * 其模型与碰撞箱会整体上移半格，灯笼悬挂贴合上台阶的底面，不再与上台阶之间
     * 留下半格空隙。关闭时则按原版行为处理（原版无法在上台阶下悬挂灯笼）。
     */
    public static final ModConfigSpec.BooleanValue LANTERN_UNDER_TOP_SLAB =
            BUILDER
                    .comment("是否允许灯笼、灵魂灯笼在“上台阶”（上半台阶）下方悬挂并贴合底面。")
                    .comment("true（默认）：灯笼模型与碰撞箱整体上移半格，悬挂贴合上台阶底面。")
                    .comment("false：恢复原版行为，无法在上台阶下悬挂灯笼。")
                    .define("allowLanternUnderTopSlab", true);

    /**
     * 台阶放置逻辑 2 的“中央正方形”大小。
     *
     * 逻辑 2 在逻辑 1 的对角线分割基础上，把方块面中央抠出一个正方形：
     * 点击正方形内放置平放台阶，正方形之外仍然按逻辑 1 竖放。
     * 数值是正方形边长占方块面边长的比例（0~1）：越大越容易点到平放，0 表示取消正方形。
     */
    public static final ModConfigSpec.DoubleValue SLAB_CENTER_SQUARE_RATIO =
            BUILDER
                    .comment("台阶放置逻辑2：面中央“正方形”区域的边长占方块面边长的比例（0~1）。")
                    .comment("点击该正方形内放置平放台阶；正方形之外仍按逻辑1竖放。")
                    .comment("越大正方形越大（越容易点到平放），设为 0 相当于取消这个正方形。默认 0.5。")
                    .defineInRange("slabCenterSquareRatio", 0.5D, 0.0D, 1.0D);

    /**
     * 方块名单：哪些方块允许本模组给它加竖形态。
     *
     * <p>每行一条，从上往下匹配，第一条命中的生效；一条都没命中时默认允许。
     * 支持的写法：
     *
     * <pre>
     *   +*                        全部允许（默认，通常放第一条当兜底）
     *   -*                        全部禁止
     *   +minecraft:oak_slab       按方块 ID
     *   +somemod:*                按模组（命名空间）
     *   +#minecraft:wooden_slabs  按方块标签
     * </pre>
     *
     * <p>注意：方块状态是注册期注入的，名单不会减少状态数，它只决定放置时会不会生成竖形态。
     * 游戏里可以用 {@code /btsdhz_original blocklist} 查看当前名单和生效统计。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKLIST =
            BUILDER
                    .comment("方块名单：哪些方块允许本模组给它加竖形态。")
                    .comment("每行一条，从上往下匹配，第一条命中的生效；都没命中时默认允许。")
                    .comment("写法：+* / -* 全部；+minecraft:oak_slab 单个方块；+somemod:* 整个模组；+#minecraft:wooden_slabs 标签。")
                    .comment("只影响放置行为，不会减少已注册的方块状态。")
                    .defineList(List.of("blocklist"), List.of("+*"), o -> o instanceof String);

    /**
     * 实验开关：是否让其它模组的台阶也能竖放。
     *
     * <p>本模组自己的竖台阶模型是预先生成的（只覆盖原版方块），别的模组没有对应模型。
     * 打开这个开关后，客户端会改为「把那个台阶自己的模型旋转成竖形态」来渲染，
     * 不再需要预生成模型，于是任何继承原版 SlabBlock 的台阶都能竖放。
     *
     * <p>默认开启；碰到渲染效果不合适、或者和其它模组打架的方块，可以关掉这个开关，
     * 或者用 {@code blocklist} 单独把那个方块退回原版。楼梯不在此列
     * （竖楼梯的连接形态需要更多套几何），其它模组的楼梯始终保持原版行为。
     */
    public static final ModConfigSpec.BooleanValue ALLOW_MODDED_SLABS =
            BUILDER
                    .comment("是否让其它模组的台阶也能竖放（实验性，默认开启）。")
                    .comment("开启后用该台阶自己的模型旋转出竖形态，因此不需要预生成模型。")
                    .comment("个别方块观感不合适时，可以关掉这个开关，或用 blocklist 单独排除它。")
                    .comment("楼梯不在这个开关范围内。")
                    .define("allowModdedSlabs", true);

    /**
     * 实验开关：是否让其它模组的楼梯也能竖放。
     *
     * <p>竖楼梯的连接形态（top / bottom / conn_left / conn_right / conn_double，以及上平楼梯
     * 版本）是手工拼的盒子，没法靠旋转对方的一个模型得到。客户端改为
     * 「用本模组的模板几何 + 换成对方的贴图」来渲染（见 {@code client/model/RetexturedTemplateModel}），
     * 所以任何继承原版 StairBlock 的楼梯都能竖放、也能参与连接。
     *
     * <p>默认开启；与台阶开关相互独立，可以分别关掉。
     */
    public static final ModConfigSpec.BooleanValue ALLOW_MODDED_STAIRS =
            BUILDER
                    .comment("是否让其它模组的楼梯也能竖放（实验性，默认开启）。")
                    .comment("开启后用本模组的模板几何配上对方楼梯的贴图渲染竖形态，连接形态全部支持。")
                    .comment("个别方块观感不合适时，可以关掉这个开关，或用 blocklist 单独排除它。")
                    .define("allowModdedStairs", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
