package com.example.myfirstmod.mixin;

import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正“下台阶上的下移方块（火把/灵魂火把/红石火把/灯笼/栅栏/墙）”的中键拾取。
 *
 * <p>背景：半砖为了能点到上方方块，在 {@code getShape}（OUTLINE）里并入了“舒适框”，
 * 因此准星指向上方方块区域时，拾取射线命中的其实是<b>半砖</b>（而非上方方块），
 * 中键拾取会返回半砖物品。这里在 {@code Block.getCloneItemStack} 里把该情况改判：
 * 瞄准“下移方块”命中半砖时，返还上方的方块物品，而不是半砖。
 *
 * <p>只对“普通水平下半台阶 + 上方下移方块 + 玩家准星落在方块/舒适框区域”生效；
 * 瞄准半砖下半部分仍返回半砖，其余方块保持默认行为。
 */
@Mixin(Block.class)
public abstract class OnSlabPickBlockMixin {

    @Inject(
            method = "getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void btsdhz_original$getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !SlabSupport.isBottomSlab(level, pos)
                || !level.isClientSide()) {
            return;
        }

        BlockState above = level.getBlockState(pos.above());
        if (!isLoweredOnSlabBlock(above)) {
            return;
        }

        Player player = MixedSlabBlock.cachedPlayer;
        if (player == null || !aimsAtOnSlabBlock(level, pos, player)) {
            return;
        }

        cir.setReturnValue(new ItemStack(above.getBlock()));
    }

    /** 上方方块是否为“下移（ON_SLAB=true）的方块”。 */
    private static boolean isLoweredOnSlabBlock(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB);
    }

    /**
     * 玩家准星是否落在半砖上方的“方块/舒适框”区域。
     * 命中上方方块本身，或命中半砖的上半格（舒适框）都算。
     */
    private static boolean aimsAtOnSlabBlock(LevelReader level, BlockPos pos, Player player) {
        if (!(level instanceof Level l)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(6.0));
        BlockHitResult hit = l.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.equals(pos.above())) {
            return true;
        }
        // 半砖自身的碰撞箱为下半格 [0, 0.5]；只有其上方的“舒适框”（y>0.5）才属于火把/灯笼。
        // 用严格 >0.5，避免把半砖顶面（y=0.5）也误判为火把。
        return hitPos.equals(pos) && hit.getLocation().y - pos.getY() > 0.5;
    }
}
