package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 红石火把放在下台阶上时，粒子特效下移半格（与下移后的模型对齐）。
 */
@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchParticleMixin {

    @Unique
    private boolean btsdhz_original$onSlab = false;

    @Inject(method = "animateTick", at = @At("HEAD"), remap = false)
    private void btsdhz_original$capture(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        this.btsdhz_original$onSlab = state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB);
    }

    @ModifyArg(method = "animateTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
            index = 2, remap = false, require = 0)
    private double btsdhz_original$shiftParticleY(double y) {
        return this.btsdhz_original$onSlab ? y - 0.5 : y;
    }
}
