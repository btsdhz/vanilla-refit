package com.example.myfirstmod.util;

import com.example.myfirstmod.config.BtsdhzConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
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
        if (mode == PlacementMode.VANILLA) {
            return false;
        }
        Direction face = context.getClickedFace();
        if (face != Direction.UP && face != Direction.DOWN) {
            return false;
        }
        return mode != PlacementMode.MOD_2 || !isInCenterSquare(context);
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
