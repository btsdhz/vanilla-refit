package com.example.myfirstmod.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 临时内存测量工具（手动触发，测完即删，不随版本发布）。
 *
 * <p>用途：把本模组给原版方块注入属性的五类功能（竖半砖 / 竖楼梯 / 玻璃板铁栏杆 / 墙 / 栅栏）
 * 分别“关掉”，跑留一法对比，用 GC 后的堆占用 + 类直方图把内存归因到每一类。
 *
 * <p>触发方式：客户端运行目录（{@code run/}）放一个 {@code btsdhz-memprobe.txt}，
 * 内容写要关掉注入的分类名（{@code slab} / {@code stair} / {@code pane} / {@code wall} / {@code fence}，
 * 逗号或换行分隔，可以留空表示“全开”）。文件不存在时这个工具完全不生效，行为与本工具不存在时一致。
 *
 * <p>本体（这个类）不引用任何 Minecraft 类：它会在早期被 mixin 调用，必须能被安全加载。
 */
public final class MemProbe {

    /** 要测量的五个分类。 */
    public enum Cat {
        SLAB,
        STAIR,
        PANE,
        WALL,
        FENCE
    }

    private static final Path TRIGGER = Paths.get("btsdhz-memprobe.txt");
    private static final Path READY = Paths.get("btsdhz-memprobe-ready.txt");

    /** 非 null 表示工具已启用；内容是要关掉注入的分类。 */
    private static final Set<String> OFF = readTrigger();

    private static volatile boolean reported;

    private MemProbe() {
    }

    /** 工具是否启用（触发文件存在）。 */
    public static boolean active() {
        return OFF != null;
    }

    /** 该分类的属性注入是否被关掉。 */
    public static boolean disabled(Cat cat) {
        return OFF != null && OFF.contains(cat.name().toLowerCase(Locale.ROOT));
    }

    /** 被关掉的分类列表，写进报告便于核对。 */
    public static String offList() {
        return OFF == null ? "<未启用>" : String.join(",", OFF);
    }

    public static Path readyPath() {
        return READY;
    }

    public static boolean alreadyReported() {
        return reported;
    }

    public static void markReported() {
        reported = true;
    }

    private static Set<String> readTrigger() {
        try {
            if (!Files.isRegularFile(TRIGGER)) {
                return null;
            }
            Set<String> out = new LinkedHashSet<>();
            for (String line : Files.readAllLines(TRIGGER, StandardCharsets.UTF_8)) {
                for (String part : line.split("[,;\\s]+")) {
                    String token = part.trim().toLowerCase(Locale.ROOT);
                    if (!token.isEmpty()) {
                        out.add(token);
                    }
                }
            }
            return out;
        } catch (IOException e) {
            return Set.of();
        }
    }
}
