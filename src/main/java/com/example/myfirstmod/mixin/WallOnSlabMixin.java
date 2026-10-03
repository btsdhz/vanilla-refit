package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.WallSlabConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

/**
 * 墙与台阶的贴合：
 *
 * <ul>
 *     <li>墙放在“下台阶”（下半台阶）上：btsdhz_on_slab=true，模型与碰撞箱下移半格，
 *         贴齐下台阶的上表面；</li>
 *     <li>墙放在“上台阶”（上半台阶）下方：btsdhz_under_top_slab=true，模型与碰撞箱上移半格，
 *         贴齐上台阶的底面。</li>
 * </ul>
 *
 * 除了位移，还负责“跨台阶”的墙连接：位移后的墙与相邻台阶格四周的墙互相连接，
 * 具体判定见 {@link WallSlabConnection}。
 */
@Mixin(WallBlock.class)
public abstract class WallOnSlabMixin {

    // WallBlock 用“完整 BlockState → 形状”的预计算 ImmutableMap。该 map 在构造时用
    // defaultBlockState() 构建，ON_SLAB / UNDER_TOP_SLAB 的默认值都是 false，所以 map 键只含
    // 两个属性都为 false 的组合。为让位移形态的墙也能命中，查询前统一把这两个属性归一化到
    // false 再查，命中后再按实际状态决定是否位移、往哪个方向位移。
    @Shadow
    private Map<BlockState, VoxelShape> shapeByIndex;

    @Shadow
    private Map<BlockState, VoxelShape> collisionShapeByIndex;

    // 给墙增加 btsdhz_on_slab / btsdhz_under_top_slab 属性
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
        cir.setReturnValue(WallSlabConnection.withSlabConnections(withSlab, level, pos));
    }

    // 邻居更新时（含上下放台阶/拆台阶）重新同步两个标记，保证反向场景（先放墙后放台阶）也生效
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        BlockState result = cir.getReturnValue();
        if (result == null || !result.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            return;
        }
        // 只有正上/正下方变化（也就是这一格贴着的台阶被放置或拆除）才重新判定位移标记。
        // 否则邻格随便放个方块都会把老存档里原本贴着下台阶的墙改判成下移形态（凭空下沉半格）。
        BlockState withSlab = result;
        if (facing == Direction.UP || facing == Direction.DOWN) {
            boolean onSlab = SlabSupport.isBottomSlab(level, currentPos.below());
            boolean underTopSlab = !onSlab && SlabSupport.isTopSlab(level, currentPos.above());
            withSlab = result
                    .setValue(ModBlockStateProperties.ON_SLAB, onSlab)
                    .setValue(ModBlockStateProperties.UNDER_TOP_SLAB, underTopSlab);
        }
        cir.setReturnValue(WallSlabConnection.withSlabConnections(withSlab, level, currentPos));
    }

    /**
     * 命中结果：普通墙 → 基础形状；下台阶上的墙 → 基础形状下移半格；上台阶下的墙 → 上移半格。
     */
    private VoxelShape resolveShape(Map<BlockState, VoxelShape> map, BlockState state) {
        boolean onSlab = SlabSupport.isOnSlab(state);
        boolean underTopSlab = SlabSupport.isUnderTopSlab(state);
        // 两个属性默认都是 false，map 键只登记 false；统一归一化到 false 命中基础形状
        BlockState lookup = state;
        if (lookup.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            lookup = lookup.setValue(ModBlockStateProperties.ON_SLAB, false);
        }
        if (lookup.hasProperty(ModBlockStateProperties.UNDER_TOP_SLAB)) {
            lookup = lookup.setValue(ModBlockStateProperties.UNDER_TOP_SLAB, false);
        }
        VoxelShape base = map.get(lookup);
        if (base == null) {
            return null;
        }
        if (onSlab) {
            return SlabSupport.shiftDownHalf(state, base);
        }
        return underTopSlab ? SlabSupport.shiftUpHalf(state, base) : base;
    }

    // 交互形状：下台阶上的墙下移半格、上台阶下的墙上移半格（与位移后的模型对齐）
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape result = resolveShape(this.shapeByIndex, state);
        // 上方/下方还有同样位移了半格的方块时，把它伸进本格的那部分并进拾取形状（舒适框），
        // 碰撞形状不受影响。
        VoxelShape stacked = SlabSupport.stackedNeighbourShape(level, pos, context);
        if (stacked != null && result != null) {
            cir.setReturnValue(Shapes.or(result, stacked));
            return;
        }
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    // 物理碰撞形状：与交互形状同样位移（保留墙的碰撞阻挡）
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape result = resolveShape(this.collisionShapeByIndex, state);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
