package com.example.myfirstmod.mixin;

import com.example.myfirstmod.entity.FenceRopeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让拴绳也能在两根栅栏之间连接。
 *
 * <p>用法:手持拴绳点击第一根栅栏记为“待连接端”,再点击另一根栅栏即生成一条悬链线拴绳;
 * 未拴着动物时才生效,以免覆盖原版“把动物拴到栅栏”的行为。</p>
 */
@Mixin(LeadItem.class)
public abstract class LeadItemMixin {
    private static final String PENDING_KEY = "FenceRopePendingPos";

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.FENCES)) {
            return;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }

        // 客户端只负责播放挥动手臂动画,实际逻辑(生成实体/持久化待连接端)在服务端执行。
        if (level.isClientSide) {
            return;
        }

        // 玩家正牵着动物时保持原版行为:把动物拴到这只栅栏上。
        if (hasPlayerLeashedMobs(player, level, pos)) {
            clearPending(player);
            return;
        }

        BlockPos pending = getPending(player);
        if (pending == null) {
            setPending(player, pos);
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        } else if (pending.equals(pos)) {
            clearPending(player);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        } else {
            // 待连接端若已不再是当前维度的栅栏(跨维度/已被拆),则把这次点击当成新的端点。
            if (!level.getBlockState(pending).is(BlockTags.FENCES)) {
                setPending(player, pos);
                level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            } else {
                createRope(level, pending, pos);
                clearPending(player);
                if (!context.getItemInHand().isEmpty()) {
                    context.getItemInHand().shrink(1);
                }
                level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
        }
    }

    private static boolean hasPlayerLeashedMobs(Player player, Level level, BlockPos pos) {
        double r = 7.0D;
        AABB aabb = new AABB(pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                pos.getX() + r, pos.getY() + r, pos.getZ() + r);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, aabb)) {
            if (entity instanceof Leashable leashable && leashable.getLeashHolder() == player) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos getPending(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(PENDING_KEY)) {
            return null;
        }
        return BlockPos.of(tag.getLong(PENDING_KEY));
    }

    private static void setPending(Player player, BlockPos pos) {
        player.getPersistentData().putLong(PENDING_KEY, pos.asLong());
    }

    private static void clearPending(Player player) {
        player.getPersistentData().remove(PENDING_KEY);
    }

    private static void createRope(Level level, BlockPos from, BlockPos to) {
        FenceRopeEntity rope = new FenceRopeEntity(level, from, to);
        level.addFreshEntity(rope);
    }
}
