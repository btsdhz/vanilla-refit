package com.example.myfirstmod.client;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.ModEntities;
import com.example.myfirstmod.client.renderer.FenceRopeRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class ModClientEvents {
    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FENCE_ROPE.get(), FenceRopeRenderer::new);
    }
}
