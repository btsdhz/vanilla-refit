package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.CompactNeighbours;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Map;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 干掉"每个方块状态各存一张邻居表"这份原版开销，见 {@link CompactNeighbours} 的说明。
 *
 * <p>原版流程：{@code StateDefinition} 构造时建一张 {@code Map<属性表, 状态>}，
 * 对每个状态调一次 {@code populateNeighbours}；那个方法把表里的每一项展开成
 * {@code (属性, 取值) → 目标状态}，再物化成 {@code ArrayTable} 存在状态上——整局游戏都留着。
 *
 * <p>这里做两件事：
 * <ol>
 *   <li>{@code populateNeighbours} 开头不再建表，只把"当前状态的下标"记下来；</li>
 *   <li>{@code setValue} / {@code trySetValue} 改成按下标现算目标状态（一次加法），
 *       行为和报错信息与原版逐字一致。</li>
 * </ol>
 *
 * <p>只要有一处对不上（属性顺序、取值序号、下标越界），{@link CompactNeighbours#install}
 * 会返回负数，这条注入就什么都不做、原版照常建表——宁可多花内存也不改行为。
 */
@Mixin(StateHolder.class)
public abstract class StateHolderNeighboursMixin {

    @Shadow
    @Final
    private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;

    @Shadow
    @Final
    protected Object owner;

    /** 当前状态在所属方块状态表里的下标（混合进制展开）。 */
    @Unique
    private int btsdhz$stateIndex;

    @Inject(method = "populateNeighbours", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz$useCompactTable(Map<Map<Property<?>, Comparable<?>>, ?> possibleStateMap, CallbackInfo ci) {
        if (!CompactNeighbours.isEnabled()) {
            return;
        }
        int index = CompactNeighbours.install(this.owner, possibleStateMap, this.values);
        if (index < 0) {
            return;   // 自检没过：放行原版，照旧建表
        }
        this.btsdhz$stateIndex = index;
        ci.cancel();
    }

    @Inject(method = "setValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz$setValue(Property<?> property, Comparable<?> value, CallbackInfoReturnable<StateHolder<?, ?>> cir) {
        Object result = this.btsdhz$resolve(property, value, true);
        if (result != CompactNeighbours.NO_TABLE) {
            cir.setReturnValue((StateHolder<?, ?>) result);
        }
    }

    @Inject(method = "trySetValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz$trySetValue(Property<?> property, Comparable<?> value, CallbackInfoReturnable<StateHolder<?, ?>> cir) {
        Object result = this.btsdhz$resolve(property, value, false);
        if (result != CompactNeighbours.NO_TABLE) {
            cir.setReturnValue((StateHolder<?, ?>) result);
        }
    }

    /**
     * 复刻原版 {@code setValue} / {@code trySetValue} 的语义：
     * 属性不在这块方块上、取值相同、取值非法这三种情况的返回/报错都和原版一致。
     *
     * @param strict true = {@code setValue}（缺属性直接抛错），false = {@code trySetValue}（原样返回）
     * @return 目标状态；{@link CompactNeighbours#NO_TABLE} 表示没装紧凑表，调用方放行原版
     */
    @Unique
    private Object btsdhz$resolve(Property<?> property, Comparable<?> value, boolean strict) {
        Comparable<?> current = this.values.get(property);
        if (current == null) {
            if (strict) {
                throw new IllegalArgumentException(
                        "Cannot set property " + property + " as it does not exist in " + this.owner);
            }
            return this;
        }
        if (current.equals(value)) {
            return this;
        }
        Object target = CompactNeighbours.transition(this.owner, this.btsdhz$stateIndex, property, current, value);
        if (target == CompactNeighbours.NO_TABLE) {
            return CompactNeighbours.NO_TABLE;
        }
        if (target == null) {
            throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on " + this.owner
                            + ", it is not an allowed value");
        }
        return target;
    }
}
