package com.example.myfirstmod.client.renderer;

import com.example.myfirstmod.entity.SitEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 隐形座位实体没有外观,渲染时什么都不做。 */
public class SitEntityRenderer extends EntityRenderer<SitEntity> {
    public SitEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SitEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(SitEntity entity) {
        return null;
    }
}
