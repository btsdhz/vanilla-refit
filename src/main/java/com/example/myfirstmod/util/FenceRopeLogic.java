package com.example.myfirstmod.util;

import com.example.myfirstmod.entity.FenceKnotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;

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
        BlockPos pos = BlockPos.of(tag.getLong(PENDING_KEY));
        // 客户端这份是镜像（见 mirrorPending），可能过期——比如待连接的那根栅栏被拆了。
        // 区块已加载时顺手校验一次，免得之后点栅栏一直被这段状态吃掉；服务端有绳结实体
        // 自己的 tick 负责清理，不在这里判（服务端也不该因为区块暂时没加载就把状态丢了）。
        Level level = player.level();
        if (level.isClientSide() && level.isLoaded(pos) && !level.getBlockState(pos).is(BlockTags.FENCES)) {
            clearPending(player);
            return null;
        }
        return pos;
    }

    public static void setPending(Player player, BlockPos pos) {
        player.getPersistentData().putLong(PENDING_KEY, pos.asLong());
    }

    public static void clearPending(Player player) {
        player.getPersistentData().remove(PENDING_KEY);
    }

    /**
     * 清空玩家的待连接状态，并把那根栅栏绳结上"另一头在玩家手上"的标记一起清掉。
     *
     * <p>绳结上的那个标记（{@code FenceKnotEntity#setPendingPlayer}）是客户端渲染"挂在手上"那条绳的依据。
     * 只清玩家这边的状态、不清绳结上的标记，就会出现"连接其实已经建好了，手上还挂着一条拴绳"的残留——
     * 所以凡是"这条待连接已经用掉/取消"的地方都该走这里。</p>
     */
    public static void clearPendingWithKnot(Player player, Level level) {
        BlockPos pending = getPending(player);
        clearPending(player);
        // 客户端只有镜像要清;绳结上的标记由服务端改完同步过来
        if (level.isClientSide() || pending == null) {
            return;
        }
        FenceKnotEntity knot = findKnot(level, pending);
        if (knot != null) {
            knot.setPendingPlayer(null);
        }
    }

    /**
     * 客户端镜像一份"待连接"状态，规则与服务端完全一致（没有 → 记下本次点击；已有 → 清空，
     * 因为"再点同一根是取消""点另一根是完成"结果都是清空）。
     *
     * <p>为什么要镜像：状态本身在服务端，但客户端右键要做本地预测。手里拿着方块、又处于待连接
     * 状态时，如果客户端不知道自己在待连接，就会先预测放一个方块，服务端却把它接成一条拴绳，
     * 再把那个方块回滚——看起来就是闪一下。</p>
     */
    public static void mirrorPending(Player player, BlockPos clicked) {
        if (getPending(player) == null) {
            setPending(player, clicked);
        } else {
            clearPending(player);
        }
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
