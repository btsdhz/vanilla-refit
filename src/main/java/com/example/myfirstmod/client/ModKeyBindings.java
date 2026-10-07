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

    /** 切换当前手持台阶/楼梯的放置逻辑(本模组竖放 <-> 原版)。 */
    private static final KeyMapping PLACEMENT_MODE_KEY = new KeyMapping(
            "key.btsdhz_original.placement_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY
    );

    private ModKeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CRAWL_KEY);
        // 按键必须注册进按键表，否则 consumeClick() 永远为 false，也无法在设置里改键。
        event.register(SIT_KEY);
        event.register(PLACEMENT_MODE_KEY);
        // 原版的按键查表在 Options.load() 里建立，模组按键注册在它之后，
        // 需要补一次刷新才能收到点击事件。
        KeyMapping.resetMapping();
    }

    public static boolean isCrawlDown() {
        return CRAWL_KEY.isDown();
    }

    public static boolean consumeSit() {
        return SIT_KEY.consumeClick();
    }

    public static boolean consumePlacementMode() {
        return PLACEMENT_MODE_KEY.consumeClick();
    }
}
