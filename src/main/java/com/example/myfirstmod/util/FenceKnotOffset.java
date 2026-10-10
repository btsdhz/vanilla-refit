package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 栅栏贴台阶位移后，栅栏上“绕绳结”该有的位置。
 *
 * <p>原版绕绳结（{@link LeashFenceKnotEntity}，本模组的 {@code FenceKnotEntity} 也继承它）
 * 在构造时由 {@code recalculateBoundingBox} 摆到“方块中心 + 0.375”，握绳点再高 0.2。
 * 栅栏整体下移/上移半格后，绳结、它的判定框以及拴绳的握绳点都还停在原格中心，
 * 表现就是“拴绳还拴在栅栏原来（未位移）的位置”。这里统一算位移量，
 * 并让绳结在贴台阶状态变化后重新摆位。</p>
 */
public final class FenceKnotOffset {

    /** 原版 {@code LeashFenceKnotEntity.OFFSET_Y}：方块中心到绳结实体的高度，照抄原版数值。 */
    private static final double KNOT_Y = 0.375;
    /** 原版 {@code LeashFenceKnotEntity.getRopeHoldPosition} 在实体位置上再加的高度。 */
    private static final double ROPE_HOLD_Y = 0.2;

    private FenceKnotOffset() {
    }

    /**
     * @return 栅栏因贴台阶而整体位移的纵向距离：贴下台阶 -0.5，贴上台阶下 +0.5，不贴 0
     */
    public static double verticalOffset(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (SlabSupport.isOnSlab(state)) {
            return -0.5;
        }
        return SlabSupport.isUnderTopSlab(state) ? 0.5 : 0.0;
    }

    /**
     * @return 绳结实体该在的高度（与原版 {@code recalculateBoundingBox} 一致，含贴台阶位移）
     */
    public static double knotY(BlockGetter level, BlockPos pos) {
        return pos.getY() + KNOT_Y + verticalOffset(level, pos);
    }

    /**
     * @return 绳结的握绳点高度（原版 {@code getRopeHoldPosition} 的 y，含贴台阶位移）。
     * 栅栏之间那几条悬链线的端点也用它，保证和动物拴绳的端点一致。
     */
    public static double ropeHoldY(BlockGetter level, BlockPos pos) {
        return knotY(level, pos) + ROPE_HOLD_Y;
    }

    /**
     * 栅栏的贴台阶状态变化（上下放/拆台阶）后把绳结重新摆位。
     *
     * <p>服务端要用它做准星与交互判定，客户端要用它渲染绳结与拴绳，
     * 所以两边都按自己这一格的方块状态现算，不依赖位置同步包。</p>
     */
    public static void refresh(LeashFenceKnotEntity knot) {
        BlockPos pos = knot.getPos();
        if (Math.abs(knot.getY() - knotY(knot.level(), pos)) < 1.0E-4) {
            return;
        }
        knot.setPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
