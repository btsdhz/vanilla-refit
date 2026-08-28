package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.MixedSlabPlacement;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.example.myfirstmod.util.ModTags;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockItemMixin");

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        // 检查手持物品是否为台阶
        ItemStack stack = context.getItemInHand();
        if (!(stack.getItem() instanceof BlockItem)) {
            return;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (!(block instanceof SlabBlock)) {
            return;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        // 创造模式：不消耗物品
        Player player = context.getPlayer();
        boolean creative = player != null && player.getAbilities().instabuild;

        // ---- 0. 往已有半砖放“不同材质”时，合并成一格混合半砖 ----
        // 触发条件：点击“朝向空余半砖空间的那一面”。分两种情况：
        //  a) 直接点击半砖本体朝空余空间的面；
        //  b) 点击空余半砖空间旁边某个方块、且该方块的面朝向半砖的空余空间。
        BlockState targetAtPos = level.getBlockState(clickedPos);
        if (targetAtPos.getBlock() instanceof SlabBlock
                && targetAtPos.hasProperty(ModBlockStateProperties.MODE)
                && targetAtPos.getBlock() != block) {
            // 直接点半砖本体，只认“朝空余空间的面”
            if (MixedSlabPlacement.tryMerge(level, clickedPos, targetAtPos, block, clickedFace, false)) {
                if (!creative) {
                    stack.shrink(1);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
                return;
            }
        }
        // 点击相邻方块的面，若该面朝向某个半砖的空余空间，则从那边合并
        BlockPos neighborPos = clickedPos.relative(clickedFace);
        if (!neighborPos.equals(clickedPos)) {
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof SlabBlock
                    && neighborState.hasProperty(ModBlockStateProperties.MODE)
                    && neighborState.getBlock() != block) {
                // 点击面朝该半砖空余空间
                if (MixedSlabPlacement.tryMergeFromNeighbor(level, neighborPos, neighborState,
                        clickedPos, block, clickedFace)) {
                    if (!creative) {
                        stack.shrink(1);
                    }
                    cir.setReturnValue(InteractionResult.SUCCESS);
                    cir.cancel();
                    return;
                }
            }
        }

        // ---- 1. 处理点击顶面或底面 ----
        if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
            // 首先检查点击的目标方块本身是否为平放台阶
            BlockState targetState = level.getBlockState(clickedPos);
            if (targetState.getBlock() instanceof SlabBlock && targetState.hasProperty(ModBlockStateProperties.MODE)) {
                if (targetState.getBlock() != block) {
                    return;
                }
                VerticalSlabMode targetMode = targetState.getValue(ModBlockStateProperties.MODE);
                SlabType targetType = targetState.getValue(SlabBlock.TYPE);
                if (targetMode == VerticalSlabMode.SLAB && targetType != SlabType.DOUBLE) {
                    LOGGER.info("BlockItemMixin: merging flat slab at {}", clickedPos);
                    // 复制 FLUID_TYPE
                    FluidType fluidType = targetState.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                            ? targetState.getValue(ModBlockStateProperties.FLUID_TYPE)
                            : FluidType.NONE;
                    BlockState doubleState = targetState.setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                            .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE)
                            .setValue(BlockStateProperties.WATERLOGGED, false);
                    if (level.setBlock(clickedPos, doubleState, 3)) {
                        level.playSound(null, clickedPos, doubleState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!creative) {
                            stack.shrink(1);
                        }
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        cir.cancel();
                        return;
                    }
                }
            }

            // 否则，处理竖半砖的合并/放置（在上方或下方）
            BlockPos newPos = clickedFace == Direction.UP ? clickedPos.above() : clickedPos.below();
            BlockState existingState = level.getBlockState(newPos);

            if (existingState.getBlock() instanceof SlabBlock && existingState.hasProperty(ModBlockStateProperties.MODE)) {
                if (existingState.getBlock() != block) {
                    return;
                }
                SlabType existingType = existingState.getValue(SlabBlock.TYPE);
                if (existingType != SlabType.DOUBLE) {
                    VerticalSlabMode existingMode = existingState.getValue(ModBlockStateProperties.MODE);
                    // 复制 FLUID_TYPE
                    FluidType fluidType = existingState.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                            ? existingState.getValue(ModBlockStateProperties.FLUID_TYPE)
                            : FluidType.NONE;
                    BlockState doubleState = existingState.setValue(ModBlockStateProperties.MODE, existingMode)
                            .setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                            .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE)
                            .setValue(BlockStateProperties.WATERLOGGED, false);
                    if (level.setBlock(newPos, doubleState, 3)) {
                        level.playSound(null, newPos, doubleState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!creative) {
                            stack.shrink(1);
                        }
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        cir.cancel();
                        return;
                    }
                } else {
                    return;
                }
            }

            // 关键修改：目标位置允许是空气 / 水 / 熔岩，且按真实放置位检测流体
            if (existingState.isAir()
                    || existingState.getFluidState().getType() == Fluids.WATER
                    || existingState.getFluidState().getType() == Fluids.LAVA) {
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
                // 检测 newPos（真实放置位）的流体，而不是硬编码 NONE
                FluidType fluidType = getFluidTypeAt(level, newPos, block);
                BlockState newState = block.defaultBlockState()
                        .setValue(ModBlockStateProperties.MODE, mode)
                        .setValue(SlabBlock.TYPE, type)
                        .setValue(ModBlockStateProperties.FLUID_TYPE, fluidType);
                if (level.setBlock(newPos, newState, 3)) {
                    level.playSound(null, newPos, newState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (!creative) {
                        stack.shrink(1);
                    }
                    cir.setReturnValue(InteractionResult.SUCCESS);
                    cir.cancel();
                    return;
                }
            }
            return;
        }

        // ---- 2. 处理点击侧面（水平方向）的竖半砖薄面合并 ----
        BlockState targetState = level.getBlockState(clickedPos);
        if (targetState.getBlock() instanceof SlabBlock && targetState.hasProperty(ModBlockStateProperties.MODE)) {
            if (targetState.getBlock() != block) {
                return;
            }
            VerticalSlabMode mode = targetState.getValue(ModBlockStateProperties.MODE);
            SlabType type = targetState.getValue(SlabBlock.TYPE);
            if (mode != VerticalSlabMode.SLAB && type != SlabType.DOUBLE) {
                Direction thinFace = getThinFace(mode, type);
                if (clickedFace == thinFace) {
                    LOGGER.info("BlockItemMixin: merging via thin face at {}", clickedPos);
                    // 复制 FLUID_TYPE
                    FluidType fluidType = targetState.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                            ? targetState.getValue(ModBlockStateProperties.FLUID_TYPE)
                            : FluidType.NONE;
                    BlockState doubleState = targetState.setValue(ModBlockStateProperties.MODE, mode)
                            .setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                            .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE)
                            .setValue(BlockStateProperties.WATERLOGGED, false);
                    if (level.setBlock(clickedPos, doubleState, 3)) {
                        level.playSound(null, clickedPos, doubleState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!creative) {
                            stack.shrink(1);
                        }
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        cir.cancel();
                        return;
                    }
                }
            }
        }
        // 其他情况放行
    }

    // ---- 辅助方法 ----

    // 检测指定位置的流体类型
    private FluidType getFluidTypeAt(Level level, BlockPos pos, Block block) {
        FluidState fluidState = level.getFluidState(pos);
        if (fluidState.getType() == Fluids.WATER) {
            return FluidType.WATER;
        } else if (fluidState.getType() == Fluids.LAVA) {
            // 黑名单台阶不能含熔岩
            if (ModTags.canHoldFluid(block.defaultBlockState(), FluidType.LAVA)) {
                return FluidType.LAVA;
            }
            return FluidType.NONE;
        }
        return FluidType.NONE;
    }

    private Direction getHorizontalDirectionFromClick(UseOnContext context) {
        double x = context.getClickLocation().x - context.getClickedPos().getX() - 0.5;
        double z = context.getClickLocation().z - context.getClickedPos().getZ() - 0.5;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private Direction getThinFace(VerticalSlabMode mode, SlabType type) {
        if (mode == VerticalSlabMode.VERTICAL_NS) {
            return (type == SlabType.BOTTOM) ? Direction.SOUTH : Direction.NORTH;
        } else if (mode == VerticalSlabMode.VERTICAL_EW) {
            return (type == SlabType.BOTTOM) ? Direction.EAST : Direction.WEST;
        }
        return null;
    }
}
