package com.example.myfirstmod.util;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

/**
 * 把原版"每个方块状态各存一张邻居表"换成"每个方块存一份属性顺序 + 按下标现算"。
 *
 * <p>背景：原版 {@code StateDefinition} 在注册方块时会构造一张
 * {@code Map<属性表, 状态>}（每个状态一条），把它交给
 * {@code StateHolder#populateNeighbours} 之后**就丢掉了**；而 {@code populateNeighbours}
 * 会为**每一个状态**再物化一张 {@code ArrayTable}（行 = 属性、列 = 该属性的其它取值），
 * 外加两张不可变索引表和每个属性一行 {@code Object[]}。这张表整局游戏都留着，
 * 唯一的用途就是让 {@code setValue} / {@code trySetValue} 能把"某个属性换个值"映射到目标状态。
 *
 * <p>实测（1.21.1，纯 NeoForge、不加任何模组）：26,686 个方块状态对应 26,350 张 ArrayTable——
 * 也就是说这是**原版机制**，不是本模组存了什么；但本模组给玻璃板/楼梯/墙/台阶乘出来的 18.9 万个状态，
 * 把这份开销一起乘了 18.9 万份，占本模组堆内存增量的 **53%**（干净环境约 230 MiB）。
 *
 * <p>状态在 {@code StateDefinition} 里是按属性名排序后做**混合进制**展开的，所以状态下标 =
 * {@code Σ 该属性取值序号 × 步长}；于是"把第 i 个属性从 a 换成 b"= 下标加
 * {@code (b - a) × 步长[i]}，一次加法就能定位目标状态，根本不需要那张表。
 * 这里只保留每个方块一份 {@code byIndex}（下标 → 状态）和小小的步长数组。
 *
 * <p>安全性：建表时做双射自检（每个状态必须落到唯一且合法的小标上），一旦对不上就整个方块
 * 回退原版实现，绝不半途改行为。与 FerriteCore 功能重叠，检测到它时自动跳过。
 */
public final class CompactNeighbours {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code transition} 的哨兵：这个方块没装紧凑表，调用方要放行原版逻辑。 */
    public static final Object NO_TABLE = new Object();

    /** 自检失败过的方块（避免每次都重试）。 */
    private static final Object FAILED = new Object();

    private static final boolean ENABLED = computeEnabled();

    /** key = StateHolder 的 owner（Block / Fluid 实例，同一个方块的所有状态共用一份）。 */
    private static final Map<Object, Object> TABLES = new ConcurrentHashMap<>();

    private static int installedBlocks;
    private static long installedStates;
    private static long verifiedCells;
    private static boolean summaryLogged;

    private CompactNeighbours() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    private static boolean computeEnabled() {
        // 安全阀：引擎级改动，必要时用 -Dbtsdhz_original.compactStateNeighbours=false 关掉
        if ("false".equalsIgnoreCase(System.getProperty("btsdhz_original.compactStateNeighbours"))) {
            return false;
        }
        // FerriteCore 的 replaceNeighborLookup 做的是同一件事，两边同时改 StateHolder 会打架
        try {
            Class<?> loadingModList = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Object list = loadingModList.getMethod("get").invoke(null);
            Object file = loadingModList.getMethod("getModFileById", String.class).invoke(list, "ferritecore");
            if (file != null) {
                LOGGER.info("[原版精修/状态] 检测到 FerriteCore，跳过程序自带的紧凑状态跳转表（功能重叠）");
                return false;
            }
        } catch (Throwable ignored) {
            // 拿不到模组列表就按"没装"处理
        }
        return true;
    }

    /**
     * 为这个方块装紧凑跳转表（同一个方块只会真正建一次），并返回当前状态的下标。
     *
     * @return 状态下标；返回负数表示这个方块不能走紧凑表，调用方必须放行原版逻辑
     */
    public static int install(Object owner, Map<Map<Property<?>, Comparable<?>>, ?> statesByValues,
                              Map<Property<?>, Comparable<?>> values) {
        Object existing = TABLES.get(owner);
        Table table;
        if (existing == null) {
            table = Table.build(statesByValues);
            TABLES.put(owner, table != null ? table : FAILED);
            if (table == null) {
                return -1;
            }
            installedBlocks++;
            installedStates += table.byIndex.length;
        } else if (existing instanceof Table found) {
            table = found;
        } else {
            return -1;
        }
        return table.indexOf(values);
    }

    /**
     * 把"当前状态 + 某个属性换个值"映射到目标状态。
     *
     * @return 目标状态；{@link #NO_TABLE} 表示这个方块没装紧凑表（调用方走原版）；
     *         {@code null} 表示这个属性/取值在这块方块上不合法（原版会抛 IAE）
     */
    public static Object transition(Object owner, int stateIndex, Property<?> property,
                                    Comparable<?> current, Comparable<?> value) {
        if (!(TABLES.get(owner) instanceof Table table)) {
            return NO_TABLE;
        }
        int position = table.positionOf(property);
        if (position < 0) {
            return null;
        }
        int from = ordinal(property, current);
        int to = ordinal(property, value);
        if (from < 0 || to < 0) {
            return null;
        }
        int index = stateIndex + (to - from) * table.stride[position];
        if (index < 0 || index >= table.byIndex.length) {
            return null;
        }
        return table.byIndex[index];
    }

    /** 属性取值在 {@code getPossibleValues()} 里的序号；找不到返回 -1。 */
    private static int ordinal(Property<?> property, @Nullable Comparable<?> value) {
        if (value == null) {
            return -1;
        }
        // 直接遍历集合，不复制——这个函数在 setValue 的热路径上，一次复制就是一笔多余分配
        int i = 0;
        for (Object possible : property.getPossibleValues()) {
            if (possible.equals(value)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** 全部方块注册完之后打一行自检结论。 */
    public static void logSummary() {
        if (summaryLogged) {
            return;
        }
        summaryLogged = true;
        if (!ENABLED) {
            return;
        }
        LOGGER.info("[原版精修/状态] 紧凑状态跳转表已启用：{} 个方块 / {} 个状态不再各存一张邻居表（ArrayTable），"
                        + "改为按属性下标现算；启动时抽样校验了 {} 处跳转、与原版逐一对上；FerriteCore 存在时会自动让路",
                installedBlocks, installedStates, verifiedCells);
    }

    /** 一个方块的跳转元数据：属性顺序 + 每个属性的取值数/步长 + 下标到状态的映射。 */
    private static final class Table {

        private final Property<?>[] properties;
        private final List<Property<?>> propertyList;
        private final int[] stride;
        private final Object[] byIndex;

        private Table(Property<?>[] properties, int[] stride, Object[] byIndex) {
            this.properties = properties;
            this.propertyList = List.of(properties);
            this.stride = stride;
            this.byIndex = byIndex;
        }

        /**
         * 从原版那张"属性表 → 状态"的 map 建表。
         *
         * <p>插入前先按属性名排序（和 {@code StateDefinition} 的 propertiesByName 同一顺序），
         * 再按混合进制展开；最后要求每个状态都落到唯一且合法的小标上（双射自检），
         * 对不上就返回 null 让这个方块回退原版。
         */
        @Nullable
        private static Table build(Map<Map<Property<?>, Comparable<?>>, ?> statesByValues) {
            if (statesByValues.isEmpty()) {
                return null;
            }
            Map<Property<?>, Comparable<?>> sample = statesByValues.keySet().iterator().next();
            List<Property<?>> ordered = new ArrayList<>(sample.keySet());
            ordered.sort(Comparator.comparing(Property::getName));
            int count = ordered.size();
            int[] sizes = new int[count];
            int[] stride = new int[count];
            long total = 1L;
            for (int i = 0; i < count; i++) {
                int size = ordered.get(i).getPossibleValues().size();
                if (size <= 0) {
                    return null;
                }
                sizes[i] = size;
                total *= size;
                if (total > Integer.MAX_VALUE) {
                    return null;
                }
            }
            if (total != statesByValues.size()) {
                return null;
            }
            int step = 1;
            for (int i = count - 1; i >= 0; i--) {
                stride[i] = step;
                step *= sizes[i];
            }
            Object[] byIndex = new Object[(int) total];
            for (Map.Entry<Map<Property<?>, Comparable<?>>, ?> entry : statesByValues.entrySet()) {
                int index = indexOf(ordered, stride, entry.getKey());
                if (index < 0 || index >= byIndex.length || byIndex[index] != null) {
                    // 自检失败：这张表的顺序和本模组的下标算法对不上，这个方块整体回退原版
                    return null;
                }
                byIndex[index] = entry.getValue();
            }
            if (!sampleVerify(ordered, stride, byIndex, statesByValues)) {
                return null;
            }
            return new Table(ordered.toArray(new Property<?>[0]), stride, byIndex);
        }

        /**
         * 抽样校验：随机挑若干「状态 + 属性 + 新取值」，用本模组的下标加法算出目标状态，
         * 再和原版那张 {@code Map<属性表, 状态>} 查出来的结果比对。对不上就整个方块回退原版。
         *
         * <p>固定种子，结果可复现；每个方块最多 24 处，启动期开销可以忽略。
         */
        private static boolean sampleVerify(List<Property<?>> ordered, int[] stride, Object[] byIndex,
                                            Map<Map<Property<?>, Comparable<?>>, ?> statesByValues) {
            // 零属性的方块（空气、空流体等）只有一个状态、没有可换的属性，没什么可校验的
            if (ordered.isEmpty()) {
                return true;
            }
            List<Map<Property<?>, Comparable<?>>> states = new ArrayList<>(statesByValues.keySet());
            Random random = new Random(0x51DF9EEDL);
            int samples = Math.min(24, states.size());
            for (int sample = 0; sample < samples; sample++) {
                Map<Property<?>, Comparable<?>> values = states.get(random.nextInt(states.size()));
                int position = random.nextInt(ordered.size());
                Property<?> property = ordered.get(position);
                List<? extends Comparable<?>> possible = List.copyOf(property.getPossibleValues());
                Comparable<?> newValue = possible.get(random.nextInt(possible.size()));
                Map<Property<?>, Comparable<?>> modified = new Reference2ObjectArrayMap<>(values);
                modified.put(property, newValue);
                Object expected = statesByValues.get(modified);
                if (expected == null) {
                    continue;
                }
                int from = ordinal(property, values.get(property));
                int to = ordinal(property, newValue);
                int index = indexOf(ordered, stride, values) + (to - from) * stride[position];
                if (index < 0 || index >= byIndex.length || byIndex[index] != expected) {
                    return false;
                }
                verifiedCells++;
            }
            return true;
        }

        private int indexOf(Map<Property<?>, Comparable<?>> values) {
            return indexOf(this.propertyList, this.stride, values);
        }

        private static int indexOf(List<Property<?>> ordered, int[] stride,
                                   Map<Property<?>, Comparable<?>> values) {
            int index = 0;
            for (int i = 0; i < ordered.size(); i++) {
                int ordinal = ordinal(ordered.get(i), values.get(ordered.get(i)));
                if (ordinal < 0) {
                    return -1;
                }
                index += ordinal * stride[i];
            }
            return index;
        }

        private int positionOf(Property<?> property) {
            for (int i = 0; i < this.properties.length; i++) {
                if (this.properties[i] == property) {
                    return i;
                }
            }
            return -1;
        }
    }
}
