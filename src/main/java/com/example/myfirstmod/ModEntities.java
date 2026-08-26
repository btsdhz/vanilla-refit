package com.example.myfirstmod;

import com.example.myfirstmod.entity.FenceRopeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BtsdhzOriginal.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FenceRopeEntity>> FENCE_ROPE =
            ENTITY_TYPES.register("fence_rope",
                    () -> EntityType.Builder.<FenceRopeEntity>of(FenceRopeEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("fence_rope"));
}
