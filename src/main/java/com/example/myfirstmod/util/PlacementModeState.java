package com.example.myfirstmod.util;

import com.example.myfirstmod.network.PlacementModeSyncPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 每个玩家、每种方块（台阶/楼梯）当前的放置逻辑。
 *
 * <p><b>服务端是权威且持久化的</b>：值写进玩家的持久数据（NeoForgeData），随存档保存，
 * 所以重新打开存档后仍然记得上次选的模式。客户端只持有一份缓存，用于客户端预测与提示线渲染：
 * 登录时由服务端下发一次，之后每次按键切换时客户端本地更新并同步给服务端。
 *
 * <p>不能反过来让客户端在登录时上报——那会用客户端刚启动的默认值把存档里的记忆覆盖掉。
 */
@EventBusSubscriber(modid = "btsdhz_original")
public final class PlacementModeState {
    /** 存进玩家持久数据的键。 */
    private static final String SLAB_KEY = "BtsdhzSlabPlacementMode";
    private static final String STAIR_KEY = "BtsdhzStairPlacementMode";

    /** 客户端缓存（服务端不用它）。 */
    private static final Map<UUID, PlacementMode> CLIENT_SLAB = new ConcurrentHashMap<>();
    private static final Map<UUID, PlacementMode> CLIENT_STAIR = new ConcurrentHashMap<>();

    private PlacementModeState() {
    }

    public static PlacementMode slab(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_SLAB.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return read(player, SLAB_KEY);
    }

    public static PlacementMode stair(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_STAIR.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return read(player, STAIR_KEY);
    }

    /** 客户端本地更新：按键切换、或收到服务端下发的权威值时调用。 */
    public static void setClient(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        put(CLIENT_SLAB, player.getUUID(), slabMode);
        put(CLIENT_STAIR, player.getUUID(), stairMode);
    }

    /** 服务端写入玩家持久数据（随存档保存）。 */
    public static void setServer(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        write(player, SLAB_KEY, slabMode);
        write(player, STAIR_KEY, stairMode);
    }

    private static PlacementMode read(Player player, String key) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(key) ? PlacementMode.byOrdinal(tag.getInt(key)) : PlacementMode.MOD;
    }

    private static void write(Player player, String key, PlacementMode mode) {
        if (mode == null) {
            return;
        }
        // 只存非默认值，默认值就把键去掉（存档更干净，缺键即默认）。
        if (mode == PlacementMode.MOD) {
            player.getPersistentData().remove(key);
        } else {
            player.getPersistentData().putInt(key, mode.ordinal());
        }
    }

    private static void put(Map<UUID, PlacementMode> map, UUID uuid, PlacementMode mode) {
        if (mode == null || mode == PlacementMode.MOD) {
            map.remove(uuid);
        } else {
            map.put(uuid, mode);
        }
    }

    /** 玩家进服时把存档里记的模式下发给他的客户端（客户端预测与提示线需要）。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new PlacementModeSyncPayload(slab(serverPlayer).ordinal(), stair(serverPlayer).ordinal()));
        }
    }
}
