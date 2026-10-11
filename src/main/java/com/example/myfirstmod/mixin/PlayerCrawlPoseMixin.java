package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.CrawlLogic;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端姿态强制: 玩家按住爬行键期间, 姿态定为俯卧 (SWIMMING)。
 *
 * Entity#setPose 会写入同步实体数据, 所以服务端一改, 其它玩家看到的模型
 * 与客户端自己的模型就一致了; 同时 getEyeHeight() 会变成俯卧的 0.4,
 * 弓箭/投掷物在服务端生成时也就落在正确的高度。
 */
@Mixin(Player.class)
public abstract class PlayerCrawlPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz$forceCrawlPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (CrawlLogic.isCrawling(self)) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
