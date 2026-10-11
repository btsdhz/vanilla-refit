package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.SlabOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 灯笼、灵魂灯笼（都是 LanternBlock 实例）与台阶的贴合：
 * 新增 btsdhz_on_slab / btsdhz_under_top_slab 属性，并让模型与碰撞箱整体位移半格。
 *
 * - 非悬挂灯笼（HANGING=false）放在“下台阶”（下半台阶）上：模型/碰撞箱整体下移半格。
 * - 悬挂灯笼（HANGING=true）放在“上台阶”（上半台阶）下：模型/碰撞箱整体上移半格。
 */
@Mixin(LanternBlock.class)
public abstract class LanternOnSlabMixin {

    // 灯笼放在下台阶上：碰撞/拾取形状整体下移半格（8/16）
    private static final VoxelShape SHAPE_ON_SLAB = Shapes.or(
            Block.box(5.0, -8.0, 5.0, 11.0, -1.0, 11.0),
            Block.box(6.0, -1.0, 6.0, 10.0, 1.0, 10.0));

    // 灯笼放在上台阶下：碰撞/拾取形状整体上移半格（8/16），
    // 使其悬挂时贴近上台阶底面。相对原版 HANGING 形状整体 +8。
    private static final VoxelShape SHAPE_UNDER_TOP_SLAB = Shapes.or(
            Block.box(5.0, 9.0, 5.0, 11.0, 16.0, 11.0),
            Block.box(6.0, 16.0, 6.0, 10.0, 18.0, 10.0));

    // 给灯笼增加 btsdhz_slab_offset 属性（不贴台阶 / 贴下台阶下移 / 贴上台阶下上移）
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.SLAB_OFFSET);
    }

    // 放置时：非悬挂灯笼放在下台阶上 → LOWERED；悬挂灯笼放在上台阶下 → RAISED。
    // 支撑面也可能不是台阶本身，而是被本模组位移了半格的栅栏/墙（下台阶上的栅栏/墙、上台阶下的栅栏/墙），
    // 这种情况同样要跟着位移，否则灯笼会悬空半格。
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        SlabOffset offset = SlabOffset.NONE;
        if (!state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(context.getLevel(), pos.below())
                    || SlabSupport.isLoweredBlock(context.getLevel(), pos.below()))) {
            offset = SlabOffset.LOWERED;
        } else if (state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.LANTERN_UNDER_TOP_SLAB.get()
                && (SlabSupport.isTopSlab(context.getLevel(), pos.above())
                    || SlabSupport.isRaisedBlock(context.getLevel(), pos.above()))
                && !SlabSupport.isLavaSlab(context.getLevel(), pos.above())) {
            offset = SlabOffset.RAISED;
        }
        cir.setReturnValue(state.setValue(ModBlockStateProperties.SLAB_OFFSET, offset));
    }

    // 支撑判定：非悬挂灯笼放在下台阶上、或悬挂灯笼放在上台阶下，均认定为有效支撑
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canSurvive(BlockState state, LevelReader level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModTags.ON_SLAB_LANTERN)
                && !state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(level, pos.below())
                    || SlabSupport.isLoweredBlock(level, pos.below()))
                && !SlabSupport.isLavaSlab(level, pos.below())) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        if (state.is(ModTags.ON_SLAB_LANTERN)
                && state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.LANTERN_UNDER_TOP_SLAB.get()
                && (SlabSupport.isTopSlab(level, pos.above())
                    || SlabSupport.isRaisedBlock(level, pos.above()))
                && !SlabSupport.isLavaSlab(level, pos.above())) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // 非悬挂灯笼下移半格（对齐下台阶上表面）；悬挂灯笼在上台阶下则上移半格
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (SlabSupport.offset(state) == SlabOffset.RAISED) {
            cir.setReturnValue(SHAPE_UNDER_TOP_SLAB);
        } else if (SlabSupport.offset(state) == SlabOffset.LOWERED) {
            cir.setReturnValue(SHAPE_ON_SLAB);
        }
    }

    // 支撑面变化时重新同步两个位移标记：非悬挂灯笼看正下方、悬挂灯笼看正上方，
    // 支撑物（台阶或下移/上移了半格的栅栏、墙）变高变矮后灯笼跟着贴合，不会悬空或陷进去。
    // 只认直接支撑方向，避免邻格无关改动影响已有建筑。
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null || result.isAir()
                || !result.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            return;
        }
        boolean hanging = result.getValue(LanternBlock.HANGING);
        if (hanging ? facing != Direction.UP : facing != Direction.DOWN) {
            return;
        }
        SlabOffset offset = SlabOffset.NONE;
        if (!hanging
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(level, currentPos.below())
                    || SlabSupport.isLoweredBlock(level, currentPos.below()))) {
            offset = SlabOffset.LOWERED;
        } else if (hanging
                && BtsdhzConfig.LANTERN_UNDER_TOP_SLAB.get()
                && (SlabSupport.isTopSlab(level, currentPos.above())
                    || SlabSupport.isRaisedBlock(level, currentPos.above()))
                && !SlabSupport.isLavaSlab(level, currentPos.above())) {
            offset = SlabOffset.RAISED;
        }
        if (result.getValue(ModBlockStateProperties.SLAB_OFFSET) != offset) {
            cir.setReturnValue(result.setValue(ModBlockStateProperties.SLAB_OFFSET, offset));
        }
    }
}
