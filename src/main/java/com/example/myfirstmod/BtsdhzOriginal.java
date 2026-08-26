package com.example.myfirstmod;

import com.example.myfirstmod.client.ModKeyBindings;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(BtsdhzOriginal.MOD_ID)
public class BtsdhzOriginal {
    public static final String MOD_ID = "btsdhz_original";

    public BtsdhzOriginal(IEventBus modEventBus) {
        System.out.println("猴子的原版更改模组已加载！");
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabItems);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ModKeyBindings::register);
        }
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.SMOOTH_STONE_STAIRS_ITEM.get());
        }
    }
}
