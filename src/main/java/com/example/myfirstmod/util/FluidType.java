package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FluidType implements StringRepresentable {
    NONE("none"),
    WATER("water"),
    LAVA("lava");

    private final String name;

    FluidType(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}