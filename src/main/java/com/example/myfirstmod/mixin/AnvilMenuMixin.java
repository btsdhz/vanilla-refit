package com.example.myfirstmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修改铁砧的「用材料修复」逻辑：
 * <ul>
 *     <li>每次用材料修复恢复 90{@literal %} 耐久(原版为 25%)</li>
 *     <li>用材料修复不再让物品的累计修理代价(RepairCost)翻倍</li>
 * </ul>
 * 不改变其它情形(物品合并、附魔书、重命名等)对 RepairCost 的影响。
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Unique
    private boolean btsdhz_original$materialRepair;

    @Inject(method = "createResult", at = @At("HEAD"), remap = false)
    private void btsdhz_original$resetMaterialRepair(CallbackInfo ci) {
        this.btsdhz_original$materialRepair = false;
    }

    // 检测是否走了「用材料修复」分支
    @Redirect(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"), remap = false)
    private boolean btsdhz_original$markMaterialRepair(Item receiver, ItemStack toRepair, ItemStack material) {
        boolean valid = receiver.isValidRepairItem(toRepair, material);
        if (valid) {
            this.btsdhz_original$materialRepair = true;
        }
        return valid;
    }

    // 单件材料修复恢复到 90% 耐久(原版 25%)
    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), remap = false)
    private int btsdhz_original$repairRatio(int damagedValue, int oldCap, Operation<Integer> original, @Local(type = ItemStack.class, ordinal = 0) ItemStack itemstack) {
        return Math.min(damagedValue, itemstack.getMaxDamage() * 90 / 100);
    }

    // 材料修复不增加累计修理代价(RepairCost)
    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;calculateIncreasedRepairCost(I)I"), remap = false)
    private int btsdhz_original$skipRepairCost(int oldRepairCost, Operation<Integer> original) {
        if (this.btsdhz_original$materialRepair) {
            return oldRepairCost;
        }
        return original.call(oldRepairCost);
    }
}
