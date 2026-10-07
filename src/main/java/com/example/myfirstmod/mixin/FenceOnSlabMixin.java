package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.SlabOffset;
import com.example.myfirstmod.util.FenceSlabConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    // 给栅栏增加 btsdhz_slab_offset 属性（不贴台阶 / 贴下台阶下移 / 贴上台阶下上移）
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.SLAB_OFFSET);
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
        BlockState withSlab = state.setValue(ModBlockStateProperties.SLAB_OFFSET,
                SlabSupport.offsetAt(level, pos));
        cir.setReturnValue(FenceSlabConnection.withSlabConnections(withSlab, level, pos));
    }

    // 邻居更新时（含上下放台阶/拆台阶）重新同步两个标记，保证反向场景（先放栅栏后放台阶）也生效
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null || !result.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            return;
        }
        // 只有正上/正下方变化（也就是这一格贴着的台阶被放置或拆除）才重新判定位移标记。
        // 邻格随便放个方块就重算的话，中途加入模组时会把老存档里原本就贴着下台阶的栅栏
        // 一起改判成下移形态（凭空下沉半格）。跨台阶连接照旧每次都刷新，它只改连接位。
        BlockState withSlab = result;
        if (facing == Direction.UP || facing == Direction.DOWN) {
            withSlab = result.setValue(ModBlockStateProperties.SLAB_OFFSET,
                    SlabSupport.offsetAt(level, currentPos));
        }
        cir.setReturnValue(FenceSlabConnection.withSlabConnections(withSlab, level, currentPos));
    }

    /**
     * 遮挡形状（face occlusion shape）跟着位移。
     *
     * <p>栅栏的遮挡形状来自构造期算好的固定表 {@code occlusionByIndex}，不随本模组的位移变化；
     * 而墙的遮挡形状取自被位移过的 {@code getShape}，所以“墙上面再放方块”不会缺面。
     * 这里让栅栏的遮挡形状一起位移半格，行为与墙一致：上面再放方块时，栅栏自己的顶面
     * （以及上方方块朝向栅栏的那一面）不会被错误地当成“紧贴”而剔除，不再出现缺面。
     */
    @Inject(method = "getOcclusionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape shape = cir.getReturnValue();
        if (shape == null || shape.isEmpty()) {
            return;
        }
        if (SlabSupport.isOnSlab(state)) {
            cir.setReturnValue(SlabSupport.shiftDownHalf(state, shape));
        } else if (SlabSupport.isUnderTopSlab(state)) {
            cir.setReturnValue(SlabSupport.shiftUpHalf(state, shape));
        }
    }

}
