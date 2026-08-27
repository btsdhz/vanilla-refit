package com.example.myfirstmod;

import com.example.myfirstmod.client.ModKeyBindings;
import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.network.SitTogglePayload;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(BtsdhzOriginal.MOD_ID)
public class BtsdhzOriginal {
    public static final String MOD_ID = "btsdhz_original";

    public BtsdhzOriginal(IEventBus modEventBus, ModContainer modContainer) {
        System.out.println("猴子的原版更改模组已加载！");
        // 注册配置文件（默认开启的火把/灯笼等下台阶贴合功能开关）
        modContainer.registerConfig(ModConfig.Type.COMMON, BtsdhzConfig.SPEC);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabItems);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ModKeyBindings::register);
        }
        modEventBus.addListener(this::registerPayloads);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.SMOOTH_STONE_STAIRS_ITEM.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                SitTogglePayload.TYPE,
                SitTogglePayload.STREAM_CODEC,
                SitTogglePayload::handle
        );
    }
}
