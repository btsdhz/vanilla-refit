package com.example.myfirstmod;

import com.example.myfirstmod.entity.FenceKnotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BtsdhzOriginal.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FenceKnotEntity>> FENCE_KNOT =
            ENTITY_TYPES.register("fence_knot",
                    () -> EntityType.Builder.<FenceKnotEntity>of(FenceKnotEntity::new, MobCategory.MISC)
                            .sized(0.375F, 0.5F)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("fence_knot"));
}
