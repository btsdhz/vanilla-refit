package com.example.myfirstmod.client;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.ModEntities;
import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.client.renderer.FenceKnotRenderer;
import com.example.myfirstmod.client.renderer.SitEntityRenderer;
import com.example.myfirstmod.network.CrawlStatePayload;
import com.example.myfirstmod.network.PlacementModePayload;
import com.example.myfirstmod.network.SitTogglePayload;
import com.example.myfirstmod.util.PlacementMode;
import com.example.myfirstmod.util.PlacementModeState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class ModClientEvents {
    private static boolean crawlPoseApplied;
    private static boolean crawlStateSent;

    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FENCE_KNOT.get(), FenceKnotRenderer::new);
        event.registerEntityRenderer(ModEntities.SIT.get(), SitEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        // 资源重载换好"状态→模型"缓存之后，验证顶层模型表去重没有把模型查丢
        ModelKeyPruner.tickChecks();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            MixedSlabBlock.cachedPlayer = null;
            crawlPoseApplied = false;
            crawlStateSent = false;
            return;
        }
        MixedSlabBlock.cachedPlayer = mc.player;

        if (mc.screen == null && ModKeyBindings.consumeSit()) {
            PacketDistributor.sendToServer(new SitTogglePayload());
        }

        if (mc.screen == null && ModKeyBindings.consumePlacementMode()) {
            togglePlacementMode(mc);
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
            // 爬行时不能冲刺, 否则会叠加游泳冲刺的速度, 视角也会莫名抖动。
            mc.player.setSprinting(false);
        } else if (crawlPoseApplied) {
            mc.player.setForcedPose(null);
            crawlPoseApplied = false;
        }

        // 把爬行状态同步给服务端: 否则服务端仍按站立姿态算眼高,
        // 射箭/投掷物会从站立高度飞出去, 别的玩家也看不到爬行模型。
        if (wantCrawl != crawlStateSent) {
            crawlStateSent = wantCrawl;
            PacketDistributor.sendToServer(new CrawlStatePayload(wantCrawl));
        }
    }

    /**
     * 切换“当前手持那种方块”的放置逻辑：拿着台阶只改台阶的，拿着楼梯只改楼梯的，两者互不影响。
     * 切换后同时写本地(供客户端预测与提示线使用)并发给服务端(供权威放置判定使用)。
     */
    private static void togglePlacementMode(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        boolean isSlab = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
        boolean isStair = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StairBlock;
        if (!isSlab && !isStair) {
            return;
        }

        PlacementMode slabMode = PlacementModeState.slab(mc.player);
        PlacementMode stairMode = PlacementModeState.stair(mc.player);
        PlacementMode newMode;
        if (isSlab) {
            slabMode = slabMode.next(false);
            newMode = slabMode;
        } else {
            stairMode = stairMode.next(true);
            newMode = stairMode;
        }

        PlacementModeState.setClient(mc.player, slabMode, stairMode);
        PacketDistributor.sendToServer(PlacementModePayload.of(slabMode, stairMode));

        Component modeName = Component.translatable("placement_mode.btsdhz_original." + newMode.getSerializedName());
        mc.gui.setOverlayMessage(
                Component.translatable(
                        isSlab ? "message.btsdhz_original.placement_mode.slab"
                                : "message.btsdhz_original.placement_mode.stair",
                        modeName),
                false);
    }

}
