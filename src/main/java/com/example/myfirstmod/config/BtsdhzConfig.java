package com.example.myfirstmod.config;

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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
