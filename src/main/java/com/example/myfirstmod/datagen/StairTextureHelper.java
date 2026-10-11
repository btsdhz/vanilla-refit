package com.example.myfirstmod.datagen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class StairTextureHelper {

    private static final Map<Block, String> TEXTURE_MAP = new HashMap<>();

    static {
        // ===== 石质楼梯 =====
        TEXTURE_MAP.put(Blocks.STONE_STAIRS, "minecraft:block/stone");
        TEXTURE_MAP.put(Blocks.COBBLESTONE_STAIRS, "minecraft:block/cobblestone");
        TEXTURE_MAP.put(Blocks.MOSSY_COBBLESTONE_STAIRS, "minecraft:block/mossy_cobblestone");
        TEXTURE_MAP.put(Blocks.STONE_BRICK_STAIRS, "minecraft:block/stone_bricks");
        TEXTURE_MAP.put(Blocks.MOSSY_STONE_BRICK_STAIRS, "minecraft:block/mossy_stone_bricks");
        TEXTURE_MAP.put(Blocks.GRANITE_STAIRS, "minecraft:block/granite");
        TEXTURE_MAP.put(Blocks.POLISHED_GRANITE_STAIRS, "minecraft:block/polished_granite");
        TEXTURE_MAP.put(Blocks.DIORITE_STAIRS, "minecraft:block/diorite");
        TEXTURE_MAP.put(Blocks.POLISHED_DIORITE_STAIRS, "minecraft:block/polished_diorite");
        TEXTURE_MAP.put(Blocks.ANDESITE_STAIRS, "minecraft:block/andesite");
        TEXTURE_MAP.put(Blocks.POLISHED_ANDESITE_STAIRS, "minecraft:block/polished_andesite");
        TEXTURE_MAP.put(Blocks.COBBLED_DEEPSLATE_STAIRS, "minecraft:block/cobbled_deepslate");
        TEXTURE_MAP.put(Blocks.POLISHED_DEEPSLATE_STAIRS, "minecraft:block/polished_deepslate");
        TEXTURE_MAP.put(Blocks.DEEPSLATE_BRICK_STAIRS, "minecraft:block/deepslate_bricks");
        TEXTURE_MAP.put(Blocks.DEEPSLATE_TILE_STAIRS, "minecraft:block/deepslate_tiles");
        TEXTURE_MAP.put(Blocks.TUFF_STAIRS, "minecraft:block/tuff");
        TEXTURE_MAP.put(Blocks.POLISHED_TUFF_STAIRS, "minecraft:block/polished_tuff");
        TEXTURE_MAP.put(Blocks.TUFF_BRICK_STAIRS, "minecraft:block/tuff_bricks");
        TEXTURE_MAP.put(Blocks.BRICK_STAIRS, "minecraft:block/bricks");
        TEXTURE_MAP.put(Blocks.MUD_BRICK_STAIRS, "minecraft:block/mud_bricks");

        // ===== 砂岩与黑石 =====
        TEXTURE_MAP.put(Blocks.SANDSTONE_STAIRS, "minecraft:block/sandstone");
        TEXTURE_MAP.put(Blocks.SMOOTH_SANDSTONE_STAIRS, "minecraft:block/sandstone_top");
        TEXTURE_MAP.put(Blocks.RED_SANDSTONE_STAIRS, "minecraft:block/red_sandstone");
        TEXTURE_MAP.put(Blocks.SMOOTH_RED_SANDSTONE_STAIRS, "minecraft:block/red_sandstone_top");
        TEXTURE_MAP.put(Blocks.BLACKSTONE_STAIRS, "minecraft:block/blackstone");
        TEXTURE_MAP.put(Blocks.POLISHED_BLACKSTONE_STAIRS, "minecraft:block/polished_blackstone");
        TEXTURE_MAP.put(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, "minecraft:block/polished_blackstone_bricks");

        // ===== 木质楼梯（含诡异木/绯红木）=====
        TEXTURE_MAP.put(Blocks.OAK_STAIRS, "minecraft:block/oak_planks");
        TEXTURE_MAP.put(Blocks.SPRUCE_STAIRS, "minecraft:block/spruce_planks");
        TEXTURE_MAP.put(Blocks.BIRCH_STAIRS, "minecraft:block/birch_planks");
        TEXTURE_MAP.put(Blocks.JUNGLE_STAIRS, "minecraft:block/jungle_planks");
        TEXTURE_MAP.put(Blocks.ACACIA_STAIRS, "minecraft:block/acacia_planks");
        TEXTURE_MAP.put(Blocks.DARK_OAK_STAIRS, "minecraft:block/dark_oak_planks");
        TEXTURE_MAP.put(Blocks.MANGROVE_STAIRS, "minecraft:block/mangrove_planks");
        TEXTURE_MAP.put(Blocks.CHERRY_STAIRS, "minecraft:block/cherry_planks");
        TEXTURE_MAP.put(Blocks.BAMBOO_STAIRS, "minecraft:block/bamboo_planks");
        TEXTURE_MAP.put(Blocks.BAMBOO_MOSAIC_STAIRS, "minecraft:block/bamboo_mosaic");
        TEXTURE_MAP.put(Blocks.CRIMSON_STAIRS, "minecraft:block/crimson_planks");
        TEXTURE_MAP.put(Blocks.WARPED_STAIRS, "minecraft:block/warped_planks");

        // ===== 下界与末地 =====
        TEXTURE_MAP.put(Blocks.NETHER_BRICK_STAIRS, "minecraft:block/nether_bricks");
        TEXTURE_MAP.put(Blocks.RED_NETHER_BRICK_STAIRS, "minecraft:block/red_nether_bricks");
        TEXTURE_MAP.put(Blocks.END_STONE_BRICK_STAIRS, "minecraft:block/end_stone_bricks");
        TEXTURE_MAP.put(Blocks.PURPUR_STAIRS, "minecraft:block/purpur_block");

        // ===== 海晶石 =====
        TEXTURE_MAP.put(Blocks.PRISMARINE_STAIRS, "minecraft:block/prismarine");
        TEXTURE_MAP.put(Blocks.PRISMARINE_BRICK_STAIRS, "minecraft:block/prismarine_bricks");
        TEXTURE_MAP.put(Blocks.DARK_PRISMARINE_STAIRS, "minecraft:block/dark_prismarine");

        // ===== 石英 =====
        TEXTURE_MAP.put(Blocks.QUARTZ_STAIRS, "minecraft:block/quartz_block_side");
        TEXTURE_MAP.put(Blocks.SMOOTH_QUARTZ_STAIRS, "minecraft:block/quartz_block_bottom");

        // ===== 铜制楼梯 =====
        TEXTURE_MAP.put(Blocks.CUT_COPPER_STAIRS, "minecraft:block/cut_copper");
        TEXTURE_MAP.put(Blocks.EXPOSED_CUT_COPPER_STAIRS, "minecraft:block/exposed_cut_copper");
        TEXTURE_MAP.put(Blocks.WEATHERED_CUT_COPPER_STAIRS, "minecraft:block/weathered_cut_copper");
        TEXTURE_MAP.put(Blocks.OXIDIZED_CUT_COPPER_STAIRS, "minecraft:block/oxidized_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_CUT_COPPER_STAIRS, "minecraft:block/cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, "minecraft:block/exposed_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, "minecraft:block/weathered_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:block/oxidized_cut_copper");
    }

    public static String getTexturePath(Block stairs) {
        String path = TEXTURE_MAP.get(stairs);
        if (path != null) {
            return path;
        }
        // 兜底：去掉 _stairs
        String name = BuiltInRegistries.BLOCK.getKey(stairs).getPath();
        return "minecraft:block/" + name.replace("_stairs", "");
    }
}