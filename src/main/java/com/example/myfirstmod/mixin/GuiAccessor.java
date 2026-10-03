package com.example.myfirstmod.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取原版叠加提示文本, 用来判断是不是"按 Shift 脱离"那条提示。 */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageString")
    Component btsdhz$getOverlayMessageString();

    @Accessor("overlayMessageString")
    void btsdhz$setOverlayMessageString(Component message);
}
