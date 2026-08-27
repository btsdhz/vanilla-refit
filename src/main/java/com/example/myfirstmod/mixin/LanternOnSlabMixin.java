package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
 * 灯笼、灵魂灯笼（都是 LanternBlock 实例）放在下台阶上时，
 * 新增 btsdhz_on_slab 属性，并让模型与碰撞箱整体下移半格。
 *
 * 仅对“放地上（HANGING=false）”的灯笼生效；悬挂灯笼不受影响。
 */
@Mixin(LanternBlock.class)
public abstract class LanternOnSlabMixin {

    // 灯笼放在下台阶上：碰撞/拾取形状整体下移半格（8/16）
    private static final VoxelShape SHAPE_ON_SLAB = Shapes.or(
            Block.box(5.0, -8.0, 5.0, 11.0, -1.0, 11.0),
            Block.box(6.0, -1.0, 6.0, 10.0, 1.0, 10.0));

    // 给灯笼增加 btsdhz_on_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.ON_SLAB);
    }

    // 放置时，非悬挂灯笼放在下台阶上则置 ON_SLAB=true
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        boolean onSlab = !state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && SlabSupport.isBottomSlab(context.getLevel(), context.getClickedPos().below());
        cir.setReturnValue(state.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
    }

    // 非悬挂灯笼放在普通水平下半台阶上时，认定为有效支撑，允许放置
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canSurvive(BlockState state, LevelReader level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && SlabSupport.isBottomSlab(level, pos.below())
                && !SlabSupport.isLavaSlab(level, pos.below())) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // 非悬挂灯笼自身碰撞箱下移半格（对齐到半砖舒适框）
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            cir.setReturnValue(SHAPE_ON_SLAB);
        }
    }
}
