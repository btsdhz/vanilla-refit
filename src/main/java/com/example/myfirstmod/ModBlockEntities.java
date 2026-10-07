package com.example.myfirstmod;

import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BtsdhzOriginal.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixedSlabBlockEntity>> MIXED_SLAB =
            BLOCK_ENTITIES.register("mixed_slab",
                    () -> BlockEntityType.Builder.of(MixedSlabBlockEntity::create, ModBlocks.MERGED_SLAB.get()).build(null));
}
