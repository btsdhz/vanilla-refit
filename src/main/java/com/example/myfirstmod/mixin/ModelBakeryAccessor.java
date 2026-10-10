package com.example.myfirstmod.mixin;

import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@code ModelBakery} 里那张"未烘焙的顶层模型表"。它没有公开 getter，而
 * {@link com.example.myfirstmod.client.ModelKeyPruner} 必须同时清掉这张表与烘焙结果表，
 * 否则两边的键对象互相引用着，删一半等于没删。
 */
@Mixin(ModelBakery.class)
public interface ModelBakeryAccessor {

    @Accessor("topLevelModels")
    Map<ModelResourceLocation, UnbakedModel> btsdhz$topLevelModels();
}
