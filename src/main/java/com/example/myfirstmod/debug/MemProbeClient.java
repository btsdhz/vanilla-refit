package com.example.myfirstmod.debug;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.util.ModBlockStateProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 临时内存测量工具的客户端半边（测完即删，不随版本发布）。
 *
 * <p>到主菜单后等 {@value #SETTLE_TICKS} tick，连做几次 GC，然后把“进程号 + GC 后堆占用 +
 * 五类方块的状态数统计”写进 {@code run/btsdhz-memprobe-ready.txt}；进程故意不退出，
 * 留给外部脚本抓 {@code jcmd GC.class_histogram} 之后再杀。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class MemProbeClient {

    /** 到主菜单后再等这么久（tick），确保资源重载、模型烘焙都结束了。 */
    private static final int SETTLE_TICKS = 200;

    private static int ticks;

    private MemProbeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MemProbe.active() || MemProbe.alreadyReported()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null || minecraft.getOverlay() != null
                || !(minecraft.screen instanceof TitleScreen)) {
            ticks = 0;
            return;
        }
        if (++ticks < SETTLE_TICKS) {
            return;
        }
        report();
    }

    private static void report() {
        MemProbe.markReported();

        List<Long> heaps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            System.gc();
            sleep(400);
            Runtime runtime = Runtime.getRuntime();
            heaps.add(runtime.totalMemory() - runtime.freeMemory());
        }

        StringBuilder out = new StringBuilder();
        out.append("off=").append(MemProbe.offList()).append('\n');
        out.append("pid=").append(ProcessHandle.current().pid()).append('\n');
        out.append("usedHeapAfterGc=").append(String.join(",", heaps.stream().map(String::valueOf).toList()))
                .append('\n');

        Map<MemProbe.Cat, int[]> stats = new EnumMap<>(MemProbe.Cat.class);
        for (MemProbe.Cat cat : MemProbe.Cat.values()) {
            stats.put(cat, new int[]{0, 0, 0, 0});   // 方块数、状态数、带属性方块数、带属性状态数
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            MemProbe.Cat cat = categoryOf(block);
            if (cat == null) {
                continue;
            }
            int states = block.getStateDefinition().getPossibleStates().size();
            boolean injected = hasInjectedProperty(block, cat);
            int[] row = stats.get(cat);
            row[0]++;
            row[1] += states;
            if (injected) {
                row[2]++;
                row[3] += states;
            }
        }

        for (MemProbe.Cat cat : MemProbe.Cat.values()) {
            int[] row = stats.get(cat);
            out.append(cat.name()).append(" blocks=").append(row[0])
                    .append(" states=").append(row[1])
                    .append(" injectedBlocks=").append(row[2])
                    .append(" injectedStates=").append(row[3])
                    .append('\n');
        }

        // 逐方块明细：方块 id 与状态数，方便核对“哪个方块吃了多少状态”
        for (Block block : BuiltInRegistries.BLOCK) {
            MemProbe.Cat cat = categoryOf(block);
            if (cat == null) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            out.append("block=").append(id).append(' ').append(cat.name())
                    .append(" states=").append(block.getStateDefinition().getPossibleStates().size())
                    .append(" injected=").append(hasInjectedProperty(block, cat))
                    .append('\n');
        }

        try {
            Files.writeString(MemProbe.readyPath(), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static MemProbe.Cat categoryOf(Block block) {
        if (block instanceof SlabBlock) {
            return MemProbe.Cat.SLAB;
        }
        if (block instanceof StairBlock) {
            return MemProbe.Cat.STAIR;
        }
        if (block instanceof IronBarsBlock) {
            return MemProbe.Cat.PANE;
        }
        if (block instanceof WallBlock) {
            return MemProbe.Cat.WALL;
        }
        if (block instanceof FenceBlock) {
            return MemProbe.Cat.FENCE;
        }
        return null;
    }

    private static boolean hasInjectedProperty(Block block, MemProbe.Cat cat) {
        return switch (cat) {
            case SLAB -> block.defaultBlockState().hasProperty(ModBlockStateProperties.MODE);
            case STAIR -> block.defaultBlockState().hasProperty(ModBlockStateProperties.VERTICAL);
            case PANE -> block.defaultBlockState().hasProperty(ModBlockStateProperties.PANE_NORTH_EAST);
            case WALL, FENCE -> block.defaultBlockState().hasProperty(ModBlockStateProperties.SLAB_OFFSET);
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
