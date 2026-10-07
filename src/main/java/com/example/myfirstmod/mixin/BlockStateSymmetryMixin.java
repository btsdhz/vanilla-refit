package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SymmetrySupport;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让竖半砖 / 竖楼梯跟着原版的镜像与旋转一起变换（机械动力创造之杖的对称模式、
 * 结构方块的镜像、WorldEdit 等都会走这两个方法）。
 *
 * <p>注入点是 {@code BlockState} 的公开入口（实现在 {@code BlockBehaviour.BlockStateBase}），
 * 也就是所有方块（含原版、含别的模组）都必经的一层：原版自己的 mirror/rotate 先算完，
 * 这里再把本模组注入的属性补上，不需要给每个方块类型单独打补丁。
 *
 * <p>没装本模组属性的方块在这里只做两次 hasProperty 判断，没有额外开销。
 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSymmetryMixin {

    @ModifyReturnValue(method = "mirror", at = @At("RETURN"), remap = false)
    private BlockState btsdhz_original$mirror(BlockState mirrored, Mirror mirror) {
        // this 就是“镜像前”的那份状态（BlockStateBase#mirror 是实例方法），
        // 原版的 StairBlock#mirror 会先把 facing 转 180°，必须拿镜像前的状态来换算本模组属性。
        if (!((Object) this instanceof BlockState self)) {
            return mirrored;
        }
        return SymmetrySupport.mirror(self, mirrored, mirror);
    }

    @ModifyReturnValue(method = "rotate", at = @At("RETURN"), remap = false)
    private BlockState btsdhz_original$rotate(BlockState rotated, Rotation rotation) {
        if (!((Object) this instanceof BlockState self)) {
            return rotated;
        }
        return SymmetrySupport.rotate(self, rotated, rotation);
    }
}
