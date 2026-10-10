package com.example.myfirstmod.command;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.config.Blocklist;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 自查命令：{@code /btsdhz_original blocklist}
 *
 * <p>打印当前生效的方块名单，以及半砖/楼梯里有多少个被名单禁用。
 * 玩家反馈"某个方块不能竖放了"时，先用它看一眼是不是名单把它关掉了
 * （配置写错的行会被忽略并打警告，所以这里显示的就是真正生效的部分）。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID)
public final class BtsdhzCommands {

    private BtsdhzCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("btsdhz_original")
                        .then(Commands.literal("blocklist")
                                .executes(BtsdhzCommands::reportBlocklist)));
    }

    private static int reportBlocklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Blocklist.Entry> entries = Blocklist.entries();

        source.sendSuccess(() -> Component.literal("方块名单（从上往下匹配，第一条命中生效，都没命中则允许）："), false);
        for (Blocklist.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal("  " + entry.describe()), false);
        }

        int slabs = 0;
        int slabsDenied = 0;
        int stairs = 0;
        int stairsDenied = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean allowed = Blocklist.allows(block);
            if (block instanceof SlabBlock) {
                slabs++;
                if (!allowed) {
                    slabsDenied++;
                }
            } else if (block instanceof StairBlock) {
                stairs++;
                if (!allowed) {
                    stairsDenied++;
                }
            }
        }
        int totalSlabs = slabs;
        int deniedSlabs = slabsDenied;
        int totalStairs = stairs;
        int deniedStairs = stairsDenied;
        source.sendSuccess(() -> Component.literal(
                "半砖 " + totalSlabs + " 个：允许 " + (totalSlabs - deniedSlabs) + "，禁用 " + deniedSlabs), false);
        source.sendSuccess(() -> Component.literal(
                "楼梯 " + totalStairs + " 个：允许 " + (totalStairs - deniedStairs) + "，禁用 " + deniedStairs), false);
        return totalSlabs + totalStairs;
    }
}
