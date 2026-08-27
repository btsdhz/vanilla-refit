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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
