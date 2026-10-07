package com.example.myfirstmod.util;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum VerticalSlabMode implements StringRepresentable {
    SLAB("slab"),
    VERTICAL_NS("vertical_ns"),
    VERTICAL_EW("vertical_ew");

    private final String name;

    VerticalSlabMode(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}