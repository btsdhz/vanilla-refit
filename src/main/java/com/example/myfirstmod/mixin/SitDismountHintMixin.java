package com.example.myfirstmod.mixin;

import com.example.myfirstmod.entity.SitEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坐上本模组的隐形座位时, 原版会在乘客数据包处理里立刻发一条 "按 Shift 脱离" 的提示。
 * 我们的座位是用坐下键切换下车的, 潜行键按不动, 这条提示属于误导。
 *
 * 这里直接在原版构造这条提示的指令处插桩并取消(此时不在构造那一帧之后),
 * 所以提示根本不会被创建、也就不会渲染, 不会像"下一 tick 再清掉"那样闪一下。
 *
 * 取消的位置是"构造提示文本"这一条指令, 发生在 startRiding(玩家已经坐上去)
 * 之后、也是该数据包处理的最后一段; 取消它只会跳过这条提示本身(叠加提示与
 * 旁白), 不影响乘客挂载。座位数据包是一次性的, 玩家装好就完事。
 */
@Mixin(ClientPacketListener.class)
public abstract class SitDismountHintMixin {
    @Inject(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
            ),
            cancellable = true,
            remap = false
    )
    private void btsdhz$skipDismountHintForSeat(CallbackInfo ci, @Local(ordinal = 1) Entity passenger) {
        if (passenger instanceof SitEntity) {
            ci.cancel();
        }
    }
}
