package com.example.myfirstmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Owns the custom key bindings of the mod.
 */
public final class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.categories.btsdhz_original";

    private static final KeyMapping CRAWL_KEY = new KeyMapping(
            "key.btsdhz_original.crawl",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            KEY_CATEGORY
    );

    private static final KeyMapping SIT_KEY = new KeyMapping(
            "key.btsdhz_original.sit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY
    );

    private ModKeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CRAWL_KEY);
        // 之前漏注册了 SIT_KEY: 按键不在注册表里时 consumeClick() 永远为 false,
        // 也不能在设置里改键, 坐下按键等于失效。
        event.register(SIT_KEY);
        // 原版的按键查表是在 Options.load() 里建的, 模组按键注册在它之后,
        // 这里补一次刷新, 让本模组的按键也能收到点击事件。
        KeyMapping.resetMapping();
    }

    public static boolean isCrawlDown() {
        return CRAWL_KEY.isDown();
    }

    public static boolean consumeSit() {
        return SIT_KEY.consumeClick();
    }
}
