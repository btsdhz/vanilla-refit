package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.myfirstmod.util.ModTags;

@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin {

    @Unique
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 0, 16, 16, 8);
    @Unique
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 8, 16, 16, 16);
    @Unique
    private static final VoxelShape SHAPE_WEST  = Block.box(0, 0, 0, 8, 16, 16);
    @Unique
    private static final VoxelShape SHAPE_EAST  = Block.box(8, 0, 0, 16, 16, 16);
    @Unique
    private static final VoxelShape SHAPE_FULL = Block.box(0, 0, 0, 16, 16, 16);

    /**
     * “舒适框”：当普通下半台阶上方有一个下移（ON_SLAB=true）的火把/灯笼时，
     * 在其半格以上区域并入一个与火把立柱等宽的框。这样原版逐格拾取在“台阶格”
     * 里也能命中（不会再像红线那样跳过），配合 LeftClickBlock 改判，点击这个区域
     * 会拆掉上方火把，而不是半砖。
     * 坐标：X/Z 6~10（火把立柱），Y 8~16（台阶格上半格）。
     */
    @Unique
    private static final VoxelShape COMFORT_TORCH = Block.box(6.0, 8.0, 6.0, 10.0, 16.0, 10.0);

    /**
     * 灯笼舒适框：上方是下移灯笼时使用。灯笼比火把宽（主体 X/Z 5~11、颈部 6~10），
     * 其下移后的碰撞箱在台阶格坐标系里为：主体 Y 8~15、颈部 Y 15~17。
     */
    @Unique
    private static final VoxelShape COMFORT_LANTERN = Shapes.or(
            Block.box(5.0, 8.0, 5.0, 11.0, 15.0, 11.0),
            Block.box(6.0, 15.0, 6.0, 10.0, 17.0, 10.0));

    // ===== 1. 注册属性 =====
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.MODE);
        builder.add(ModBlockStateProperties.FLUID_TYPE);
    }

    // ===== 2. 放置状态 =====
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        if (original == null) return;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        FluidState fluidState = level.getFluidState(pos);
        FluidType fluidType = FluidType.NONE;
        if (fluidState.getType() == Fluids.WATER) {
            fluidType = FluidType.WATER;
        } else if (fluidState.getType() == Fluids.LAVA) {
            // 黑名单台阶不能含熔岩（不捕获，由原版放置逻辑挤走）
            if (original.is(ModTags.LAVA_BLACKLIST_SLABS)) {
                fluidType = FluidType.NONE;
            } else {
                fluidType = FluidType.LAVA;
            }
        }

        Direction clickedFace = context.getClickedFace();
        BlockState newState;

        if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
            Direction facing = getHorizontalDirectionFromClick(context);
            VerticalSlabMode mode;
            SlabType type;
            if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                mode = VerticalSlabMode.VERTICAL_NS;
                type = (facing == Direction.NORTH) ? SlabType.BOTTOM : SlabType.TOP;
            } else {
                mode = VerticalSlabMode.VERTICAL_EW;
                type = (facing == Direction.WEST) ? SlabType.BOTTOM : SlabType.TOP;
            }
            newState = original.setValue(ModBlockStateProperties.MODE, mode)
                    .setValue(SlabBlock.TYPE, type)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluidType)
                    .setValue(BlockStateProperties.WATERLOGGED, false);
        } else {
            newState = original.setValue(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluidType)
                    .setValue(BlockStateProperties.WATERLOGGED, false);
        }

        // 原版逻辑：放置即含液体时，安排一次流体 tick，让液体源向外流动
        if (fluidType != FluidType.NONE && !level.isClientSide()) {
            Fluid fluid = fluidType == FluidType.WATER ? Fluids.WATER : Fluids.LAVA;
            level.scheduleTick(pos, fluid, fluid.getTickDelay(level));
        }

        cir.setReturnValue(newState);
    }

    // ===== 3. 返回流体状态 =====
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getFluidState(BlockState state, CallbackInfoReturnable<FluidState> cir) {
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            FluidType fluidType = state.getValue(ModBlockStateProperties.FLUID_TYPE);
            if (fluidType == FluidType.WATER) {
                cir.setReturnValue(Fluids.WATER.getSource(false));
                return;
            } else if (fluidType == FluidType.LAVA) {
                cir.setReturnValue(Fluids.LAVA.getSource(false));
                return;
            }
        }
        cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    // ===== 4. 辅助方法：计算水平方向 =====
    @Unique
    private Direction getHorizontalDirectionFromClick(BlockPlaceContext context) {
        double x = context.getClickLocation().x - context.getClickedPos().getX() - 0.5;
        double z = context.getClickLocation().z - context.getClickedPos().getZ() - 0.5;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    // ===== 5. 碰撞箱 =====
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.MODE)) {
            VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
            if (mode == VerticalSlabMode.VERTICAL_NS || mode == VerticalSlabMode.VERTICAL_EW) {
                SlabType type = state.getValue(SlabBlock.TYPE);
                VoxelShape shape;
                if (type == SlabType.DOUBLE) {
                    shape = SHAPE_FULL;
                } else if (mode == VerticalSlabMode.VERTICAL_NS) {
                    shape = (type == SlabType.BOTTOM) ? SHAPE_NORTH : SHAPE_SOUTH;
                } else {
                    shape = (type == SlabType.BOTTOM) ? SHAPE_WEST : SHAPE_EAST;
                }
                cir.setReturnValue(shape);
            } else if (mode == VerticalSlabMode.SLAB
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                BlockState above = getLoweredTorchOrLantern(level, pos);
                if (above != null) {
                    // 普通下半台阶 + 上方下移火把/灯笼：拾取形状并入对应舒适框
                    boolean lantern = above.getBlock() instanceof LanternBlock;
                    cir.setReturnValue(Shapes.or(cir.getReturnValue(),
                            lantern ? COMFORT_LANTERN : COMFORT_TORCH));
                }
            }
        }
    }

    // 返回 pos 上方是否为“下移（ON_SLAB=true）的普通火把/灵魂火把/灯笼/灵魂灯笼”；
    // 是则返回该上方方块态，否则返回 null。
    @Unique
    private static BlockState getLoweredTorchOrLantern(BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        Block b = above.getBlock();
        boolean isTorch = b instanceof TorchBlock && !(b instanceof WallTorchBlock);
        boolean isLantern = b instanceof LanternBlock;
        if ((isTorch || isLantern)
                && above.hasProperty(ModBlockStateProperties.ON_SLAB)
                && above.getValue(ModBlockStateProperties.ON_SLAB)) {
            return above;
        }
        return null;
    }

    // ===== 6. 不可替换 =====
    @Inject(method = "canBeReplaced", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canBeReplaced(BlockState state, BlockPlaceContext context, CallbackInfoReturnable<Boolean> cir) {
        if (state.hasProperty(ModBlockStateProperties.MODE)) {
            VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
            if (mode != VerticalSlabMode.SLAB) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }

    // ===== 7. 流体流入（原版 waterlogging 路径） =====
    @Inject(method = "canPlaceLiquid", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            // 黑名单台阶不能容纳熔岩
            if (fluid == Fluids.LAVA && state.is(ModTags.LAVA_BLACKLIST_SLABS)) {
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
            FluidType fluidType = state.getValue(ModBlockStateProperties.FLUID_TYPE);
            cir.setReturnValue(fluidType == FluidType.NONE && (fluid == Fluids.WATER || fluid == Fluids.LAVA));
            cir.cancel();
        }
    }

    @Inject(method = "placeLiquid", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            FluidType fluidType = state.getValue(ModBlockStateProperties.FLUID_TYPE);
            if (fluidType == FluidType.NONE && (fluidState.getType() == Fluids.WATER || fluidState.getType() == Fluids.LAVA)) {
                if (!level.isClientSide()) {
                    FluidType newType = fluidState.getType() == Fluids.WATER ? FluidType.WATER : FluidType.LAVA;
                    level.setBlock(pos, state.setValue(ModBlockStateProperties.FLUID_TYPE, newType), 3);
                    // 与原版含水方块完全一致：placeLiquid 后安排流体 tick，让液体源向外流动
                    level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
                }
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
