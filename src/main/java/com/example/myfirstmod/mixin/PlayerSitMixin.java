package com.example.myfirstmod.mixin;

import com.example.myfirstmod.entity.SitEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 坐在本模组的座位上时,禁止用潜行键下车(下车一律靠再次按键切换)。 */
@Mixin(Player.class)
public abstract class PlayerSitMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz$blockDismountWhileSitting(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getVehicle() instanceof SitEntity) {
            cir.setReturnValue(false);
        }
    }
}
