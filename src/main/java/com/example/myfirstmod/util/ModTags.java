package com.example.myfirstmod.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ModTags {

    // 不能容纳熔岩的方块标签（非诡异木/绯红木的木头台阶）
    public static final TagKey<Block> LAVA_BLACKLIST_SLABS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("btsdhz_original", "lava_blacklist_slabs"));

    // 不能容纳熔岩的方块标签（非诡异木/绯红木的木头楼梯）
    public static final TagKey<Block> LAVA_BLACKLIST_STAIRS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("btsdhz_original", "lava_blacklist_stairs"));

    // 可放置在普通下半台阶上并“下移贴齐”的火把类方块（默认原版火把、灵魂火把）
    public static final TagKey<Block> ON_SLAB_TORCH =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("btsdhz_original", "on_slab_torch"));

    // 可放置在普通下半台阶上并“下移贴齐”的灯笼类方块（默认原版灯笼、灵魂灯笼）
    public static final TagKey<Block> ON_SLAB_LANTERN =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("btsdhz_original", "on_slab_lantern"));

    // 判断一个方块能否容纳指定流体
    public static boolean canHoldFluid(BlockState state, FluidType fluidType) {
        if (fluidType != FluidType.LAVA) return true;
        Block block = state.getBlock();
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        // 非诡异木/绯红木的木头台阶+楼梯（含竹/竹马赛克）
        boolean woodSlab = path.endsWith("_slab") && isNonWarpedCrimsonWood(path.replace("_slab", ""));
        boolean woodStair = path.endsWith("_stairs") && isNonWarpedCrimsonWood(path.replace("_stairs", ""));
        return !(woodSlab || woodStair);
    }

    private static boolean isNonWarpedCrimsonWood(String name) {
        switch (name) {
            case "oak": case "spruce": case "birch": case "jungle":
            case "acacia": case "dark_oak": case "mangrove": case "cherry":
            case "bamboo": case "bamboo_mosaic":
                return true;
            default:
                return false;
        }
    }
}
