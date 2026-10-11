package com.example.myfirstmod.mixin;

import com.example.myfirstmod.entity.SitEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 坐在本模组的座位上时,把视角眼高略微降低,让坐姿更自然。 */
@Mixin(Entity.class)
public abstract class PlayerEyeHeightMixin {
    @ModifyReturnValue(method = "getEyeHeight", at = @At("RETURN"))
    private float btsdhz$lowerEyeWhileSitting(float original) {
        Entity self = (Entity) (Object) this;
        if (self instanceof Player player && player.getVehicle() instanceof SitEntity) {
            return original - 0.35F;
        }
        return original;
    }
}
