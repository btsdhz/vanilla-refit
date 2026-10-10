package com.example.myfirstmod;

import com.example.myfirstmod.client.ModKeyBindings;
import com.example.myfirstmod.client.ModelKeyPruner;
import com.example.myfirstmod.client.PaneModelEvents;
import com.example.myfirstmod.client.SlabbedModelEvents;
import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.network.CrawlStatePayload;
import com.example.myfirstmod.network.PlacementModePayload;
import com.example.myfirstmod.network.PlacementModeSyncPayload;
import com.example.myfirstmod.network.SitTogglePayload;
import com.example.myfirstmod.util.CompactNeighbours;
import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(BtsdhzOriginal.MOD_ID)
public class BtsdhzOriginal {
    public static final String MOD_ID = "btsdhz_original";

    public BtsdhzOriginal(IEventBus modEventBus, ModContainer modContainer) {
        System.out.println("原版精修模组已加载！");
        // 注册配置文件（默认开启的火把/灯笼等下台阶贴合功能开关）
        modContainer.registerConfig(ModConfig.Type.COMMON, BtsdhzConfig.SPEC);
        // 客户端：把 btsdhz_on_slab=true 的方块模型包装成下移半格（支持继承原版类的火把/灯笼）
        modEventBus.addListener(SlabbedModelEvents::onModifyBakingResult);
        // 客户端：玻璃板/铁栏杆改成“一个变体 + 按状态拼装”，省掉每个状态各一份的模型数据
        modEventBus.addListener(PaneModelEvents::onRegisterAdditional);
        modEventBus.addListener(PaneModelEvents::onModifyBakingResult);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabItems);
        // 方块注册完之后打一行"紧凑状态跳转表"的自检结论（替代了每个状态一张邻居表）
        modEventBus.addListener(FMLLoadCompleteEvent.class, event -> CompactNeighbours.logSummary());
        // 顺便打一行玻璃板部件属性的注入范围（原版注入、第三方不注入），确认没有把属性漏给第三方玻璃板
        modEventBus.addListener(FMLLoadCompleteEvent.class, event -> PaneCornerSupport.logSummary());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ModKeyBindings::register);
            // 客户端：烘焙完成后把"每个状态一条顶层模型位置"的冗余键删到每块一条（省内存，外观不变）
            modEventBus.addListener(ModelKeyPruner::onBakingCompleted);
        }
        modEventBus.addListener(this::registerPayloads);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.SMOOTH_STONE_STAIRS_ITEM.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(
                        SitTogglePayload.TYPE,
                        SitTogglePayload.STREAM_CODEC,
                        SitTogglePayload::handle
                )
                .playToServer(
                        CrawlStatePayload.TYPE,
                        CrawlStatePayload.STREAM_CODEC,
                        CrawlStatePayload::handle
                )
                .playToServer(
                        PlacementModePayload.TYPE,
                        PlacementModePayload.STREAM_CODEC,
                        PlacementModePayload::handle
                )
                .playToClient(
                        PlacementModeSyncPayload.TYPE,
                        PlacementModeSyncPayload.STREAM_CODEC,
                        PlacementModeSyncPayload::handle
                );
    }
}
