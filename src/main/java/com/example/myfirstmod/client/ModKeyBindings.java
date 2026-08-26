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

    private ModKeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CRAWL_KEY);
    }

    public static boolean isCrawlDown() {
        return CRAWL_KEY.isDown();
    }
}
