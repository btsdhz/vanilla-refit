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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正“贴台阶的方块”的中键拾取（下台阶上的下移方块、上台阶下的上移方块）。
 *
 * <p>背景：半砖为了能点到上方方块，在 {@code getShape}（OUTLINE）里并入了“舒适框”，
 * 因此准星指向上方方块区域时，拾取射线命中的其实是<b>半砖</b>（而非上方方块），
 * 中键拾取会返回半砖物品。这里在 {@code Block.getCloneItemStack} 里把该情况改判：
 * 瞄准“下移方块”命中半砖时，返还上方的方块物品，而不是半砖。
 *
 * <p>上台阶一侧是镜像：上半台阶下方有上移方块（灯笼/墙）时，台阶格下半格并入了该方块的形状，
 * 此时中键返还下方方块，而不是台阶。
 *
 * <p>只对“普通水平台阶 + 贴着的位移方块 + 玩家准星落在方块/舒适框区域”生效；
 * 瞄准台阶本体仍返回台阶，其余方块保持默认行为。
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
        if (!level.isClientSide()) {
            return;
        }

        Player player = MixedSlabBlock.cachedPlayer;
        if (player == null) {
            return;
        }

        // 四种情况：下台阶上贴着的下移方块、上台阶下贴着的上移方块、
        // 以及“方块的方块叠着放”（下移方块上再放火把/灯笼、上移方块下再挂灯笼）。
        BlockState attached;
        boolean aboveHalf;
        if (SlabSupport.isBottomSlab(level, pos) && isLoweredOnSlabBlock(level.getBlockState(pos.above()))) {
            attached = level.getBlockState(pos.above());
            aboveHalf = true;
        } else if (SlabSupport.isTopSlab(level, pos) && SlabSupport.isRaisedUnderTopSlabBelow(level, pos)) {
            attached = level.getBlockState(pos.below());
            aboveHalf = false;
        } else if (SlabSupport.hasLoweredBlockAbove(level, pos)) {
            attached = level.getBlockState(pos.above());
            aboveHalf = true;
        } else if (SlabSupport.hasRaisedBlockBelow(level, pos)) {
            attached = level.getBlockState(pos.below());
            aboveHalf = false;
        } else {
            return;
        }

        BlockPos attachedPos = aboveHalf ? pos.above() : pos.below();
        if (!aimsAtAttachedBlock(level, pos, player, attachedPos, aboveHalf)) {
            return;
        }
        cir.setReturnValue(new ItemStack(attached.getBlock()));
    }

    /** 方块是否为“下移（ON_SLAB=true）的方块”。 */
    private static boolean isLoweredOnSlabBlock(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB);
    }

    /**
     * 玩家准星是否落在“贴着台阶的那个方块 / 台阶里并入的舒适框”区域。
     *
     * @param aboveHalf true=下移方块在台阶上方（看台阶格上半格）；false=上移方块在台阶下方（看台阶格下半格）
     */
    private static boolean aimsAtAttachedBlock(LevelReader level, BlockPos pos, Player player,
                                               BlockPos attachedPos, boolean aboveHalf) {
        if (!(level instanceof Level l)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(6.0));
        BlockHitResult hit = l.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockPos hitPos = hit.getBlockPos();
        if (hitPos.equals(attachedPos)) {
            return true;
        }
        if (!hitPos.equals(pos)) {
            return false;
        }
        // 台阶自身的形状只覆盖自己那半格；另一半格属于并入的舒适框（也就是贴着的方块）。
        // 用严格不等号，避免把台阶自己的那个面（y=0.5）也误判成贴着的方块。
        double half = hit.getLocation().y - pos.getY();
        return aboveHalf ? half > 0.5 : half < 0.5;
    }
}
