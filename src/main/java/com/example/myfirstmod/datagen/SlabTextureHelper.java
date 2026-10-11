package com.example.myfirstmod.datagen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class SlabTextureHelper {
    private static final Map<Block, String> TEXTURE_MAP = new HashMap<>();

    static {
        // ===== 石质台阶 =====
        TEXTURE_MAP.put(Blocks.STONE_SLAB, "minecraft:block/stone");
        TEXTURE_MAP.put(Blocks.COBBLESTONE_SLAB, "minecraft:block/cobblestone");
        TEXTURE_MAP.put(Blocks.MOSSY_COBBLESTONE_SLAB, "minecraft:block/mossy_cobblestone");
        //TEXTURE_MAP.put(Blocks.SMOOTH_STONE_SLAB, "minecraft:block/smooth_stone");
        TEXTURE_MAP.put(Blocks.STONE_BRICK_SLAB, "minecraft:block/stone_bricks");
        TEXTURE_MAP.put(Blocks.MOSSY_STONE_BRICK_SLAB, "minecraft:block/mossy_stone_bricks");
        TEXTURE_MAP.put(Blocks.GRANITE_SLAB, "minecraft:block/granite");
        TEXTURE_MAP.put(Blocks.POLISHED_GRANITE_SLAB, "minecraft:block/polished_granite");
        TEXTURE_MAP.put(Blocks.DIORITE_SLAB, "minecraft:block/diorite");
        TEXTURE_MAP.put(Blocks.POLISHED_DIORITE_SLAB, "minecraft:block/polished_diorite");
        TEXTURE_MAP.put(Blocks.ANDESITE_SLAB, "minecraft:block/andesite");
        TEXTURE_MAP.put(Blocks.POLISHED_ANDESITE_SLAB, "minecraft:block/polished_andesite");
        TEXTURE_MAP.put(Blocks.COBBLED_DEEPSLATE_SLAB, "minecraft:block/cobbled_deepslate");
        TEXTURE_MAP.put(Blocks.POLISHED_DEEPSLATE_SLAB, "minecraft:block/polished_deepslate");
        TEXTURE_MAP.put(Blocks.DEEPSLATE_BRICK_SLAB, "minecraft:block/deepslate_bricks");
        TEXTURE_MAP.put(Blocks.DEEPSLATE_TILE_SLAB, "minecraft:block/deepslate_tiles");
        TEXTURE_MAP.put(Blocks.TUFF_SLAB, "minecraft:block/tuff");
        TEXTURE_MAP.put(Blocks.POLISHED_TUFF_SLAB, "minecraft:block/polished_tuff");
        TEXTURE_MAP.put(Blocks.TUFF_BRICK_SLAB, "minecraft:block/tuff_bricks");
        TEXTURE_MAP.put(Blocks.BRICK_SLAB, "minecraft:block/bricks");
        TEXTURE_MAP.put(Blocks.MUD_BRICK_SLAB, "minecraft:block/mud_bricks");

        // 砂岩与黑石 (Sandstone & Blackstone)
        TEXTURE_MAP.put(Blocks.SANDSTONE_SLAB, "minecraft:block/sandstone");
        TEXTURE_MAP.put(Blocks.SMOOTH_SANDSTONE_SLAB, "minecraft:block/sandstone_top");
        TEXTURE_MAP.put(Blocks.CUT_SANDSTONE_SLAB, "minecraft:block/cut_sandstone");
        TEXTURE_MAP.put(Blocks.RED_SANDSTONE_SLAB, "minecraft:block/red_sandstone");
        TEXTURE_MAP.put(Blocks.SMOOTH_RED_SANDSTONE_SLAB, "minecraft:block/red_sandstone_top");
        TEXTURE_MAP.put(Blocks.CUT_RED_SANDSTONE_SLAB, "minecraft:block/cut_red_sandstone");
        TEXTURE_MAP.put(Blocks.BLACKSTONE_SLAB, "minecraft:block/blackstone");
        TEXTURE_MAP.put(Blocks.POLISHED_BLACKSTONE_SLAB, "minecraft:block/polished_blackstone");
        TEXTURE_MAP.put(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, "minecraft:block/polished_blackstone_bricks");

        // ===== 木质台阶 =====
        TEXTURE_MAP.put(Blocks.OAK_SLAB, "minecraft:block/oak_planks");
        TEXTURE_MAP.put(Blocks.SPRUCE_SLAB, "minecraft:block/spruce_planks");
        TEXTURE_MAP.put(Blocks.BIRCH_SLAB, "minecraft:block/birch_planks");
        TEXTURE_MAP.put(Blocks.JUNGLE_SLAB, "minecraft:block/jungle_planks");
        TEXTURE_MAP.put(Blocks.ACACIA_SLAB, "minecraft:block/acacia_planks");
        TEXTURE_MAP.put(Blocks.DARK_OAK_SLAB, "minecraft:block/dark_oak_planks");
        TEXTURE_MAP.put(Blocks.MANGROVE_SLAB, "minecraft:block/mangrove_planks");
        TEXTURE_MAP.put(Blocks.CHERRY_SLAB, "minecraft:block/cherry_planks");
        TEXTURE_MAP.put(Blocks.BAMBOO_SLAB, "minecraft:block/bamboo_planks");
        TEXTURE_MAP.put(Blocks.CRIMSON_SLAB, "minecraft:block/crimson_planks");
        TEXTURE_MAP.put(Blocks.WARPED_SLAB, "minecraft:block/warped_planks");
        TEXTURE_MAP.put(Blocks.BAMBOO_MOSAIC_SLAB, "minecraft:block/bamboo_mosaic");

        // ===== 特殊：石化橡木台阶（使用橡木纹理） =====
        TEXTURE_MAP.put(Blocks.PETRIFIED_OAK_SLAB, "minecraft:block/oak_planks");

        // ===== 下界与末地 =====
        TEXTURE_MAP.put(Blocks.NETHER_BRICK_SLAB, "minecraft:block/nether_bricks");
        TEXTURE_MAP.put(Blocks.RED_NETHER_BRICK_SLAB, "minecraft:block/red_nether_bricks");
        TEXTURE_MAP.put(Blocks.END_STONE_BRICK_SLAB, "minecraft:block/end_stone_bricks");
        TEXTURE_MAP.put(Blocks.PURPUR_SLAB, "minecraft:block/purpur_block");

        // ===== 海晶石 =====
        TEXTURE_MAP.put(Blocks.PRISMARINE_SLAB, "minecraft:block/prismarine");
        TEXTURE_MAP.put(Blocks.PRISMARINE_BRICK_SLAB, "minecraft:block/prismarine_bricks");
        TEXTURE_MAP.put(Blocks.DARK_PRISMARINE_SLAB, "minecraft:block/dark_prismarine");

        // ===== 石英 =====
        TEXTURE_MAP.put(Blocks.QUARTZ_SLAB, "minecraft:block/quartz_block_side");
        TEXTURE_MAP.put(Blocks.SMOOTH_QUARTZ_SLAB, "minecraft:block/quartz_block_bottom");

        // ===== 铜制台阶 =====
        TEXTURE_MAP.put(Blocks.CUT_COPPER_SLAB, "minecraft:block/cut_copper");
        TEXTURE_MAP.put(Blocks.EXPOSED_CUT_COPPER_SLAB, "minecraft:block/exposed_cut_copper");
        TEXTURE_MAP.put(Blocks.WEATHERED_CUT_COPPER_SLAB, "minecraft:block/weathered_cut_copper");
        TEXTURE_MAP.put(Blocks.OXIDIZED_CUT_COPPER_SLAB, "minecraft:block/oxidized_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_CUT_COPPER_SLAB, "minecraft:block/cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, "minecraft:block/exposed_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, "minecraft:block/weathered_cut_copper");
        TEXTURE_MAP.put(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, "minecraft:block/oxidized_cut_copper");
    }

    public static String getTexturePath(Block slab) {
        String path = TEXTURE_MAP.get(slab);
        if (path == null) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(slab).getPath();
            return "minecraft:block/" + name.replace("_slab", "");
        }
        return path;
    }
}