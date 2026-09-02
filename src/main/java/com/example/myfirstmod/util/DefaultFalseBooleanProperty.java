package com.example.myfirstmod.util;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 取值顺序为 [false, true] 的布尔方块状态属性。
 *
 * 原版 BooleanProperty 的取值顺序为 [true, false]，因此 {@code stateDefinition.any()}
 * 的默认值是 true。对“是否贴合下台阶”这类默认为否定的标记，若沿用 BooleanProperty，
 * 旧存档/未显式保存该属性的方块读取时会默认成 true（即下移形态），导致所有老方块被误判。
 * 此处用 [false, true] 让默认值为 false。
 */
public class DefaultFalseBooleanProperty extends Property<Boolean> {
    private final ImmutableSet<Boolean> values = ImmutableSet.of(false, true);

    public DefaultFalseBooleanProperty(String name) {
        super(name, Boolean.class);
    }

    @Override
    public Collection<Boolean> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<Boolean> getValue(String value) {
        return !"true".equals(value) && !"false".equals(value) ? Optional.empty() : Optional.of(Boolean.valueOf(value));
    }

    @Override
    public String getName(Boolean value) {
        return value.toString();
    }
}
