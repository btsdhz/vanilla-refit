package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class BucketMixin {

    @Shadow(remap = false)
    private Fluid content;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$use(Level level, Player player, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        BlockHitResult hitResult = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // 判断是否是"我们的流体容器"：台阶，或楼梯（平放/竖放都算）
        boolean isSlab = state.getBlock() instanceof SlabBlock && state.hasProperty(ModBlockStateProperties.FLUID_TYPE);
        boolean isStair = state.getBlock() instanceof StairBlock
                && state.hasProperty(ModBlockStateProperties.VERTICAL)
                && state.hasProperty(ModBlockStateProperties.FLUID_TYPE);
        if (!isSlab && !isStair) {
            return; // 放行原版
        }

        ItemStack bucket = player.getItemInHand(hand);
        FluidType currentFluid = state.getValue(ModBlockStateProperties.FLUID_TYPE);
        Fluid fluid = this.content;
        boolean creative = player.getAbilities().instabuild;

        // ===== 空桶：取出流体 =====
        if (bucket.is(Items.BUCKET)) {
            if (currentFluid == FluidType.NONE) {
                return; // 没装我们的流体，放行原版（处理 WATERLOGGED 的水）
            }
            BlockState newState = state.setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            level.setBlock(pos, newState, 3);

            ItemStack filledBucket = currentFluid == FluidType.WATER
                    ? new ItemStack(Items.WATER_BUCKET)
                    : new ItemStack(Items.LAVA_BUCKET);
            level.playSound(null, pos,
                    currentFluid == FluidType.WATER ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_FILL_LAVA,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(creative ? bucket : filledBucket, level.isClientSide()));
            cir.cancel();
            return;
        }

        // ===== 水桶：倒水 =====
        if (fluid == Fluids.WATER && currentFluid == FluidType.NONE) {
            BlockState newState = state.setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.WATER);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            level.setBlock(pos, newState, 3);
            if (!level.isClientSide()) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(creative ? bucket : new ItemStack(Items.BUCKET), level.isClientSide()));
            cir.cancel();
            return;
        }

        // ===== 熔岩桶：倒熔岩 =====
        if (fluid == Fluids.LAVA && currentFluid == FluidType.NONE) {
            if (!ModTags.canHoldFluid(state, FluidType.LAVA)) {
                return; // 黑名单楼梯/台阶：放行原版，原版会在边上放出熔岩源
            }
            BlockState newState = state.setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.LAVA);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);
            }
            level.setBlock(pos, newState, 3);
            if (!level.isClientSide()) {
                level.scheduleTick(pos, Fluids.LAVA, Fluids.LAVA.getTickDelay(level));
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(creative ? bucket : new ItemStack(Items.BUCKET), level.isClientSide()));
            cir.cancel();
            return;
        }
    }
}