package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.FenceSlabConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 栅栏与台阶的贴合：
 *
 * <ul>
 *     <li>栅栏放在“下台阶”（下半台阶）上：btsdhz_on_slab=true，模型与碰撞箱下移半格，
 *         贴齐下台阶的上表面；</li>
 *     <li>栅栏放在“上台阶”（上半台阶）下方：btsdhz_under_top_slab=true，模型与碰撞箱上移半格，
 *         贴齐上台阶的底面。</li>
 * </ul>
 *
 * 除了位移，还负责“跨台阶”的栅栏连接（栅栏的跨格连接与墙一样是斜向关系，原版不会通知），
 * 具体判定见 {@link FenceSlabConnection}。栅栏没有墙那种“高度 TALL/LOW”和立柱开关，
 * 连接状态只有连/不连。
 */
@Mixin(FenceBlock.class)
public abstract class FenceOnSlabMixin {

    // 给栅栏增加 btsdhz_on_slab / btsdhz_under_top_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.ON_SLAB);
        builder.add(ModBlockStateProperties.UNDER_TOP_SLAB);
    }

    // 放置时：下方是下台阶则下移；否则上方是上台阶则上移
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean onSlab = SlabSupport.isBottomSlab(level, pos.below());
        // 一个方块不可能同时贴合上下两个台阶（位移方向互斥），下台阶优先
        boolean underTopSlab = !onSlab && SlabSupport.isTopSlab(level, pos.above());
        BlockState withSlab = state
                .setValue(ModBlockStateProperties.ON_SLAB, onSlab)
                .setValue(ModBlockStateProperties.UNDER_TOP_SLAB, underTopSlab);
        cir.setReturnValue(FenceSlabConnection.withSlabConnections(withSlab, level, pos));
    }

    // 邻居更新时（含上下放台阶/拆台阶）重新同步两个标记，保证反向场景（先放栅栏后放台阶）也生效
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null || !result.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            return;
        }
        boolean onSlab = SlabSupport.isBottomSlab(level, currentPos.below());
        boolean underTopSlab = !onSlab && SlabSupport.isTopSlab(level, currentPos.above());
        BlockState withSlab = result
                .setValue(ModBlockStateProperties.ON_SLAB, onSlab)
                .setValue(ModBlockStateProperties.UNDER_TOP_SLAB, underTopSlab);
        cir.setReturnValue(FenceSlabConnection.withSlabConnections(withSlab, level, currentPos));
    }

}
