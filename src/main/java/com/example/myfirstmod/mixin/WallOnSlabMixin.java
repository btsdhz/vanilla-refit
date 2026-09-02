package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/**
 * 墙放在“下台阶”（下半台阶）上时，新增 btsdhz_on_slab 属性并让模型与碰撞箱下移半格，
 * 使墙贴齐下台阶的上表面，不再悬空。
 *
 * 只做“墙是否在下台阶上”的判定与位移；墙与墙/栅栏之间的连接关系不受影响。
 */
@Mixin(WallBlock.class)
public abstract class WallOnSlabMixin {

    // WallBlock 用“完整 BlockState → 形状”的预计算 ImmutableMap。该 map 在构造时用
    // defaultBlockState() 构建，而 ON_SLAB 默认 false，所以 map 键只含 ON_SLAB=false。
    // 为让 ON_SLAB=true（上台阶）的墙也能命中，查询前统一把 ON_SLAB 归一化到 false 再查，
    // 命中后再按实际 isOnSlab 决定是否位移。
    @Shadow
    private Map<BlockState, VoxelShape> shapeByIndex;

    @Shadow
    private Map<BlockState, VoxelShape> collisionShapeByIndex;

    // 给墙增加 btsdhz_on_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.ON_SLAB);
    }

    // 放置时：下方是下台阶则置 ON_SLAB=true
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        boolean onSlab = SlabSupport.isBottomSlab(context.getLevel(), context.getClickedPos().below());
        cir.setReturnValue(state.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
    }

    // 邻居更新时（含下方放台阶/拆台阶）重新同步 ON_SLAB，保证反向场景（先放墙后放台阶）也生效
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            boolean onSlab = SlabSupport.isBottomSlab(level, currentPos.below());
            cir.setReturnValue(cir.getReturnValue().setValue(ModBlockStateProperties.ON_SLAB, onSlab));
        }
    }

    /**
     * 命中结果：ON_SLAB=false（普通墙）→ 基础形状；ON_SLAB=true（墙上台阶）→ 基础形状下移半格。
     */
    private VoxelShape resolveShape(Map<BlockState, VoxelShape> map, BlockState state) {
        boolean onSlab = SlabSupport.isOnSlab(state);
        // ON_SLAB 默认 false，故 map 键只登记 ON_SLAB=false；统一用 false 命中基础形状
        BlockState lookup = state.setValue(ModBlockStateProperties.ON_SLAB, false);
        VoxelShape base = map.get(lookup);
        if (base == null) {
            return null;
        }
        return onSlab ? SlabSupport.shiftDownHalf(base) : base;
    }

    // 交互形状：墙上台阶则下移半格（与下移后的模型对齐）
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape result = resolveShape(this.shapeByIndex, state);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    // 物理碰撞形状：墙上台阶则下移半格（保留墙的碰撞阻挡）
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape result = resolveShape(this.collisionShapeByIndex, state);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
