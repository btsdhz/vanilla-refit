package com.example.myfirstmod.util;

import com.example.myfirstmod.entity.FenceKnotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 栅栏拴绳功能的共享逻辑:待连接状态、查找/创建绳结、建立连接。
 */
public final class FenceRopeLogic {
    public static final String PENDING_KEY = "FenceKnotPendingPos";

    private FenceRopeLogic() {
    }

    public static BlockPos getPending(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(PENDING_KEY)) {
            return null;
        }
        return BlockPos.of(tag.getLong(PENDING_KEY));
    }

    public static void setPending(Player player, BlockPos pos) {
        player.getPersistentData().putLong(PENDING_KEY, pos.asLong());
    }

    public static void clearPending(Player player) {
        player.getPersistentData().remove(PENDING_KEY);
    }

    public static boolean hasPlayerLeashedMobs(Player player, Level level, BlockPos pos) {
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

    public static FenceKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        FenceKnotEntity existing = findKnot(level, pos);
        if (existing != null) {
            return existing;
        }
        FenceKnotEntity knot = new FenceKnotEntity(level, pos);
        level.addFreshEntity(knot);
        return knot;
    }

    public static FenceKnotEntity findKnot(Level level, BlockPos pos) {
        AABB search = new AABB(pos).inflate(1.0);
        for (FenceKnotEntity knot : level.getEntitiesOfClass(FenceKnotEntity.class, search)) {
            if (knot.getPos().equals(pos)) {
                return knot;
            }
        }
        return null;
    }

    /** 在 from 与 to 两根栅栏之间建立(或追加)一条拴绳连接。 */
    public static void connect(Level level, BlockPos from, BlockPos to, boolean consumed) {
        FenceKnotEntity a = getOrCreateKnot(level, from);
        FenceKnotEntity b = getOrCreateKnot(level, to);
        a.setPartner(to, consumed);
        b.setPartner(from, consumed);
        a.setPendingPlayer(null);
        b.setPendingPlayer(null);
    }
}
