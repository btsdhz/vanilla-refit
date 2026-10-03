package com.example.myfirstmod.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 每个玩家、每种方块（台阶/楼梯）当前的放置逻辑。
 *
 * <p>客户端是这一状态的来源：按键切换后同时写本地和服务端（发 {@code PlacementModePayload}），
 * 这样客户端预测和服务端权威判定用的是同一个值；服务端在玩家退出时清掉残留。
 * 默认 {@link PlacementMode#MOD}，所以只存非默认值。
 */
@EventBusSubscriber(modid = "btsdhz_original")
public final class PlacementModeState {
    private static final Map<UUID, PlacementMode> SLAB = new ConcurrentHashMap<>();
    private static final Map<UUID, PlacementMode> STAIR = new ConcurrentHashMap<>();

    private PlacementModeState() {
    }

    public static PlacementMode slab(Player player) {
        return player == null ? PlacementMode.MOD : SLAB.getOrDefault(player.getUUID(), PlacementMode.MOD);
    }

    public static PlacementMode stair(Player player) {
        return player == null ? PlacementMode.MOD : STAIR.getOrDefault(player.getUUID(), PlacementMode.MOD);
    }

    public static void set(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        put(SLAB, player.getUUID(), slabMode);
        put(STAIR, player.getUUID(), stairMode);
    }

    private static void put(Map<UUID, PlacementMode> map, UUID uuid, PlacementMode mode) {
        if (mode == null || mode == PlacementMode.MOD) {
            map.remove(uuid);
        } else {
            map.put(uuid, mode);
        }
    }

    /** 玩家退出时清掉, 避免离线残留(下次进来恢复默认, 客户端登录时也会重新同步一次)。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        SLAB.remove(uuid);
        STAIR.remove(uuid);
    }
}
