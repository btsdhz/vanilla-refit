package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 火把/灵魂火把放在下台阶上时，碰撞箱整体下移半格（8/16 单位），
 * 使其与下台阶的上表面对齐。
 *
 * 只需要处理 BaseTorchBlock（普通火把、灵魂火把都直接是 TorchBlock 实例）。
 * 红石火把没有 btsdhz_on_slab 属性，所以不影响；墙火把单独覆盖 getShape，
 * 也不会走到这里。
 */
@Mixin(BaseTorchBlock.class)
public abstract class TorchOnSlabMixin {

    // 火把放在下台阶上：碰撞/拾取形状整体下移半格（8/16），与模型一致，
    // 并与下面半砖并入的“舒适框”重叠（不同方块各自判定，互不冲突）
    private static final VoxelShape SHAPE_ON_SLAB = Block.box(6.0, -8.0, 6.0, 10.0, 2.0, 10.0);

    // 火把放在普通水平下半台阶上、或放在被下移半格的方块（下台阶上的栅栏/墙）上时，
    // 认定为有效支撑，允许放置
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canSurvive(BlockState state, LevelReader level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModTags.ON_SLAB_TORCH)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(level, pos.below())
                    || SlabSupport.isLoweredBlock(level, pos.below()))
                && !SlabSupport.isLavaSlab(level, pos.below())) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // 支撑面（正下方）变化时重新同步 ON_SLAB：支撑物变高/变矮后火把跟着回到对应形态，
    // 不会出现火把沉进栅栏里或悬空的情况。只认正下方，避免邻格无关改动影响已有建筑。
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (facing != Direction.DOWN || result == null || result.isAir()
                || !result.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            return;
        }
        boolean onSlab = BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(level, currentPos.below())
                    || SlabSupport.isLoweredBlock(level, currentPos.below()));
        if (result.getValue(ModBlockStateProperties.ON_SLAB) != onSlab) {
            cir.setReturnValue(result.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
        }
    }

    // 火把/灵魂火把自身碰撞箱下移半格（对齐到半砖舒适框）
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            cir.setReturnValue(SHAPE_ON_SLAB);
        }
    }
}
