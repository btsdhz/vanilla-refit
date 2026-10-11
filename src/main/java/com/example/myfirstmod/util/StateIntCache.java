package com.example.myfirstmod.util;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.ToIntFunction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 极小的"方块状态 → int"直接映射缓存（无锁、线程安全）。
 *
 * <p>用途：把一个需要读十几个属性才能算出来的整数（比如玻璃板的 12 位部件掩码）按状态记住。
 * 原版把方块几何烘焙进区块网格时，是**对同一个状态连续问 6 个方向 + 1 次不带方向**，
 * 7 次调用读的是同一批属性——缓存之后每次重建只算一遍。
 *
 * <p>实现要点：
 * <ul>
 *   <li>槽位用 {@link System#identityHashCode} 直接映射，命中就是一次数组读，没有哈希表遍历；</li>
 *   <li>每个槽里放一个**不可变的** {@code Entry(state, value)}，读到的引用一定是配对的——
 *       区块网格是在多个工作线程上并行重建的，用两个并行数组会出现"读到新状态配旧值"的错配；</li>
 *   <li>冲突/被别的线程顶掉时只是重新算一遍，不写状态、不分配常驻对象
 *       （只有算的时候产生一个小 Entry，旧的立刻变垃圾）。</li>
 * </ul>
 */
public final class StateIntCache {

    private static final int DEFAULT_SLOTS = 256;

    /** 混合用的大奇数（斐波那契散列），把连续分配的 identity hash 打散到槽位上。 */
    private static final int MIX = 0x9E3779B1;

    private final AtomicReferenceArray<Entry> slots;
    private final ToIntFunction<BlockState> compute;
    private final int shift;

    public StateIntCache(ToIntFunction<BlockState> compute) {
        this(compute, DEFAULT_SLOTS);
    }

    /**
     * @param slots 槽位数，必须是 2 的幂；大一点命中率高、内存按槽位线性增长（每槽几字节）
     */
    public StateIntCache(ToIntFunction<BlockState> compute, int slots) {
        if (Integer.bitCount(slots) != 1) {
            throw new IllegalArgumentException("槽位数必须是 2 的幂：" + slots);
        }
        this.compute = compute;
        this.slots = new AtomicReferenceArray<>(slots);
        this.shift = Integer.numberOfTrailingZeros(slots);
    }

    /** 取这个状态对应的整数；没缓存过（或被顶掉）就算一遍。 */
    public int get(BlockState state) {
        int slot = (System.identityHashCode(state) * MIX) >>> (32 - this.shift);
        Entry cached = this.slots.get(slot);
        if (cached != null && cached.state() == state) {
            return cached.value();
        }
        int value = this.compute.applyAsInt(state);
        this.slots.set(slot, new Entry(state, value));
        return value;
    }

    private record Entry(BlockState state, int value) {
    }
}
