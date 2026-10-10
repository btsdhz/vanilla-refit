package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FluidHolder;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.MixedSlabPlacement;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.PlacementMode;
import com.example.myfirstmod.util.PlacementModeState;
import com.example.myfirstmod.util.SlabPlacementRules;
import com.example.myfirstmod.util.SlabSupport;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.myfirstmod.util.ModTags;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

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
        // 其它模组的台阶不应用本模组的堆叠/混合/竖放逻辑，保持原版放置
        if (!SlabSupport.isSupportedSlab(block)) {
            return;
        }
        // 兜底：方块的状态定义没有走 SlabBlock 的（也就没被注入 MODE），直接放行，
        // 免得下面 setValue 抛 IllegalArgument 把放置整个搞崩。
        if (!block.defaultBlockState().hasProperty(ModBlockStateProperties.MODE)) {
            return;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        PlacementMode placementMode = PlacementModeState.slab(context.getPlayer());
        // 台阶切到原版放置逻辑时：不再竖放，但“混合半砖”（不同材质填进空余半砖空间）仍然保留。
        boolean vanillaMode = placementMode == PlacementMode.VANILLA;
        // 创造模式：不消耗物品
        Player player = context.getPlayer();
        boolean creative = player != null && player.getAbilities().instabuild;

        // ---- 0. 统一的“半砖填充”判定：点击面朝向某个半砖的空余半砖空间时，
        //        在该格填入半砖（同材质→DOUBLE，不同材质→混合）。 ----
        BlockState targetAtPos = level.getBlockState(clickedPos);
        if (MixedSlabPlacement.tryFill(level, clickedPos, targetAtPos, block, clickedFace, vanillaMode)) {
            if (!creative) {
                stack.shrink(1);
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
            return;
        }

        // 原版模式：不竖放，剩下的（同材质合成整块等）交给原版。
        if (vanillaMode) {
            return;
        }

        // ---- 1. 点击顶面或底面：往空位（空气/水/熔岩）放一个新竖半砖 ----
        // 逻辑 2 下点在中央正方形内时不竖放，交回原版平放。
        if ((clickedFace == Direction.UP || clickedFace == Direction.DOWN)
                && SlabPlacementRules.wantsVertical(context, placementMode, block)) {
            BlockPos newPos = clickedFace == Direction.UP ? clickedPos.above() : clickedPos.below();
            BlockState existingState = level.getBlockState(newPos);
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
                FluidType fluidType = getFluidTypeAt(level, newPos, block);
                BlockState newState = FluidHolder.with(block.defaultBlockState()
                        .setValue(ModBlockStateProperties.MODE, mode)
                        .setValue(SlabBlock.TYPE, type), fluidType);
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

}
