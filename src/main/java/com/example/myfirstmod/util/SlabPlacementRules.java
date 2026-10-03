package com.example.myfirstmod.util;

import com.example.myfirstmod.config.BtsdhzConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * 台阶“竖放还是平放”的判定，放置逻辑（BlockItemMixin）与状态生成
 * （SlabBlockMixin.getStateForPlacement）共用，保证两边结论一致。
 */
public final class SlabPlacementRules {

    private SlabPlacementRules() {
    }

    /**
     * 这次点击是否应该放竖半砖。
     *
     * <ul>
     *     <li>原版逻辑：恒不竖放；</li>
     *     <li>逻辑 1：点顶/底面就竖放；</li>
     *     <li>逻辑 2：点顶/底面时，落在中央正方形内不竖放（改平放），正方形外仍竖放。</li>
     * </ul>
     */
    public static boolean wantsVertical(UseOnContext context, PlacementMode mode) {
        // 原版交给原版；逻辑 3 是“按面决定朝向”，也不走逻辑 1 的竖放分支。
        if (mode == PlacementMode.VANILLA || mode == PlacementMode.MOD_3) {
            return false;
        }
        Direction face = context.getClickedFace();
        if (face != Direction.UP && face != Direction.DOWN) {
            return false;
        }
        return mode != PlacementMode.MOD_2 || !isInCenterSquare(context);
    }

    /** 逻辑 3 里，某个点击面对应的“紧贴该面”的半砖朝向。 */
    public static Orientation flushAgainst(Direction face) {
        return switch (face) {
            // 点顶面 -> 放在上方那一格，占下半 => 下台阶
            case UP -> new Orientation(VerticalSlabMode.SLAB, SlabType.BOTTOM);
            // 点底面 -> 放在下方那一格，占上半 => 上台阶
            case DOWN -> new Orientation(VerticalSlabMode.SLAB, SlabType.TOP);
            // 点北面 -> 放在北边那一格，占南半 => 南竖台阶
            case NORTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
            // 点南面 -> 放在南边那一格，占北半 => 北竖台阶
            case SOUTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM);
            // 点西面 -> 放在西边那一格，占东半 => 东竖台阶
            case WEST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
            // 点东面 -> 放在东边那一格，占西半 => 西竖台阶
            case EAST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM);
        };
    }

    /** 半砖朝向：形态（平放 / 竖南北 / 竖东西）+ 占哪一半。 */
    public record Orientation(VerticalSlabMode mode, SlabType type) {
    }

    /** 点击位置是否落在被点方块面的中央正方形内。 */
    public static boolean isInCenterSquare(UseOnContext context) {
        double ratio = centerSquareRatio();
        if (ratio <= 0.0D) {
            return false;
        }
        // 面内相对坐标。顶/底面点击时，放置格(BlockPlaceContext)与被点方块的 x/z 相同，
        // 所以这里直接用 getClickedPos() 即可，两种情况都对。
        BlockPos facePos = context.getClickedPos();
        Vec3 loc = context.getClickLocation();
        double fx = loc.x - facePos.getX();
        double fz = loc.z - facePos.getZ();
        double half = ratio / 2.0D;
        return Math.abs(fx - 0.5D) <= half && Math.abs(fz - 0.5D) <= half;
    }

    /** 配置里的中央正方形边长比例；读取失败时按默认 0.5 处理。 */
    public static double centerSquareRatio() {
        try {
            return BtsdhzConfig.SLAB_CENTER_SQUARE_RATIO.get();
        } catch (RuntimeException exception) {
            return 0.5D;
        }
    }
}
