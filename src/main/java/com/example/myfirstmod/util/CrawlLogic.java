package com.example.myfirstmod.util;

import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端记录玩家是否处于手动爬行状态。
 *
 * 爬行在客户端是靠 forcedPose(俯卧) 实现的, 但姿态是同步实体数据:
 * 只改客户端的话, 服务端仍按站立姿态算眼高, 于是射箭/投掷物的出生点
 * 会停在站立高度。这里让服务端也进入俯卧姿态, 两边的眼高就一致了。
 */
public final class CrawlLogic {
    private static final Set<UUID> CRAWLING = ConcurrentHashMap.newKeySet();

    private CrawlLogic() {
    }

    public static void setCrawling(Player player, boolean crawling) {
        if (crawling) {
            CRAWLING.add(player.getUUID());
        } else {
            CRAWLING.remove(player.getUUID());
        }
    }

    public static boolean isCrawling(Player player) {
        return CRAWLING.contains(player.getUUID());
    }
}
