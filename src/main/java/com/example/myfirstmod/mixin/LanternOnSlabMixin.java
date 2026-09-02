package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
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

    // 给灯笼增加 btsdhz_on_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.ON_SLAB);
        builder.add(ModBlockStateProperties.UNDER_TOP_SLAB);
    }

    // 放置时：非悬挂灯笼放在下台阶上置 ON_SLAB=true；悬挂灯笼放在上台阶下置 UNDER_TOP_SLAB=true
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        boolean onSlab = !state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && SlabSupport.isBottomSlab(context.getLevel(), context.getClickedPos().below());
        boolean underTopSlab = state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.LANTERN_UNDER_TOP_SLAB.get()
                && SlabSupport.isTopSlab(context.getLevel(), context.getClickedPos().above())
                && !SlabSupport.isLavaSlab(context.getLevel(), context.getClickedPos().above());
        cir.setReturnValue(state
                .setValue(ModBlockStateProperties.ON_SLAB, onSlab)
                .setValue(ModBlockStateProperties.UNDER_TOP_SLAB, underTopSlab));
    }

    // 支撑判定：非悬挂灯笼放在下台阶上、或悬挂灯笼放在上台阶下，均认定为有效支撑
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canSurvive(BlockState state, LevelReader level, BlockPos pos,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModTags.ON_SLAB_LANTERN)
                && !state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && SlabSupport.isBottomSlab(level, pos.below())
                && !SlabSupport.isLavaSlab(level, pos.below())) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        if (state.is(ModTags.ON_SLAB_LANTERN)
                && state.getValue(LanternBlock.HANGING)
                && BtsdhzConfig.LANTERN_UNDER_TOP_SLAB.get()
                && SlabSupport.isTopSlab(level, pos.above())
                && !SlabSupport.isLavaSlab(level, pos.above())) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    // 非悬挂灯笼下移半格（对齐下台阶上表面）；悬挂灯笼在上台阶下则上移半格
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.UNDER_TOP_SLAB)
                && state.getValue(ModBlockStateProperties.UNDER_TOP_SLAB)) {
            cir.setReturnValue(SHAPE_UNDER_TOP_SLAB);
        } else if (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            cir.setReturnValue(SHAPE_ON_SLAB);
        }
    }
}
