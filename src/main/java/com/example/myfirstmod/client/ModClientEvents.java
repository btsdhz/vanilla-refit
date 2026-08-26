package com.example.myfirstmod.client;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.ModEntities;
import com.example.myfirstmod.client.renderer.FenceKnotRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class ModClientEvents {
    private static boolean crawlPoseApplied;

    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FENCE_KNOT.get(), FenceKnotRenderer::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        boolean wantCrawl = ModKeyBindings.isCrawlDown()
                && mc.player.isAlive()
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isSleeping();

        if (wantCrawl) {
            mc.player.setForcedPose(Pose.SWIMMING);
            crawlPoseApplied = true;
        } else if (crawlPoseApplied) {
            mc.player.setForcedPose(null);
            crawlPoseApplied = false;
        }
    }
}
