package com.example.myfirstmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 客户端：组合半砖的材质变了以后，要求重画它所在的区块。
 *
 * <p>组合半砖的材质是渲染时从方块实体取出来合并的，客户端把它们缓存在区块里（NeoForge 的
 * 模型数据缓存）。如果只是改了方块实体的数据、没有改方块状态，客户端不会重画这一区块，
 * 表现就是材质一直停在旧值（镜像过来时是默认的石头），直到旁边有方块更新把区块标脏才刷新。
 *
 * <p>调用方（{@code MixedSlabBlockEntity}）同时要调 {@code requestModelDataUpdate()} 让缓存失效，
 * 两个一起做区块才会用新材质重画。
 */
public final class MixedSlabClientRefresh {

    private MixedSlabClientRefresh() {
    }

    public static void section(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.levelRenderer == null) {
            return;
        }
        minecraft.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}
