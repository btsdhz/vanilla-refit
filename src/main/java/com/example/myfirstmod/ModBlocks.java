package com.example.myfirstmod;

import com.example.myfirstmod.block.MixedSlabBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, BtsdhzOriginal.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, BtsdhzOriginal.MOD_ID);

    public static final Supplier<Block> SMOOTH_STONE_STAIRS =
            BLOCKS.register("smooth_stone_stairs",
                    () -> new StairBlock(Blocks.SMOOTH_STONE.defaultBlockState(),
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)));

    public static final Supplier<Item> SMOOTH_STONE_STAIRS_ITEM =
            ITEMS.register("smooth_stone_stairs",
                    () -> new BlockItem(SMOOTH_STONE_STAIRS.get(), new Item.Properties()));

    // 混合半砖：一格子里放两块不同材质的半砖（用方块实体存材质）
    public static final Supplier<Block> MERGED_SLAB =
            BLOCKS.register("merged_slab",
                    // 不复制石头的 requiresCorrectToolForDrops（默认 false），
                    // 使混合半砖任何工具都能采集掉落两块半砖，避免整挖无掉落。
                    () -> new MixedSlabBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.STONE)
                                    .strength(1.5F, 6.0F)
                                    .sound(SoundType.STONE)));

    public static final Supplier<Item> MERGED_SLAB_ITEM =
            ITEMS.register("merged_slab",
                    () -> new BlockItem(MERGED_SLAB.get(), new Item.Properties()));
}
