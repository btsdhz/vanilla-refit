package com.example.myfirstmod.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

@EventBusSubscriber(modid = "btsdhz_original")
public class AnvilEvents {

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        // 取消原版「每次使用铁砧有 12% 概率使其受损」的逻辑，只保留铁砧跌落受损
        event.setBreakChance(0.0F);
    }
}
