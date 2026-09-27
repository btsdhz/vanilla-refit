package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.WallSlabConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 墙/台阶被放置或移除后，刷新“跨下台阶连接”波及的墙。
 *
 * 这些连接是斜向关系（台阶格四周的墙 ↔ 台阶上方的下移墙），原版的方块更新只会通知
 * 六方向邻居，通知不到，所以这里在方块落位/移除后主动补一次刷新。
 *
 * 注入点在 BlockBehaviour 的默认空实现上，只有墙（WallBlock）与台阶（SlabBlock）会继续
 * 往下走，其余方块只是一次廉价判断。
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourSlabWallMixin {

    @Inject(method = "onPlace", at = @At("RETURN"), remap = false)
    private void btsdhz_original$onPlace(BlockState state, Level level, BlockPos pos,
                                         BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
        WallSlabConnection.refreshAround(level, pos, state);
    }

    @Inject(method = "onRemove", at = @At("RETURN"), remap = false)
    private void btsdhz_original$onRemove(BlockState state, Level level, BlockPos pos,
                                          BlockState newState, boolean movedByPiston, CallbackInfo ci) {
        WallSlabConnection.refreshAround(level, pos, state);
    }
}
