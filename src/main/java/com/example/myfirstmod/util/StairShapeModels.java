package com.example.myfirstmod.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * 竖楼梯「状态 → 模型 + 旋转」的唯一映射表。
 *
 * <p>映射表统一收在这里：
 *
 * <ul>
 *   <li>{@link #vertical}：竖放（VERTICAL=true）时，连接形态 × 朝向 × 上下 → 用哪种模型、转多少度</li>
 *   <li>{@link #flat}：平放（VERTICAL=false）时，原版形状 × 朝向 × 上下 → 转多少度</li>
 * </ul>
 *
 * <p>关于角度：{@code yRot} 是已经算好的最终 Y 角度（含朝向），{@code xRot} 是绕 X 轴的翻转
 * （0 或 180）。竖放的基准角度是逐个校过的值，修改前先在游戏里朝四个方向各看一眼。
 */
public final class StairShapeModels {

    /** 需要的模型种类；具体用哪个 ModelFile 由调用方（datagen）决定。 */
    public enum ModelKind {
        /** 竖楼梯本体（连接形态 none）。 */
        VERTICAL,
        /** 原版 inner 模型（连接形态 corner 的拐角）。 */
        VANILLA_INNER,
        /** 单大面连接右。 */
        CONN_RIGHT,
        /** 单大面连接左。 */
        CONN_LEFT,
        /** 双大面连接。 */
        CONN_DOUBLE,
        /** 平放：直楼梯。 */
        FLAT_STRAIGHT,
        /** 平放：内角。 */
        FLAT_INNER,
        /** 平放：外角。 */
        FLAT_OUTER
    }

    /**
     * 一个状态该怎么画。
     *
     * @param kind       用哪种模型
     * @param xRot       绕 X 轴的角度
     * @param yRot       绕 Y 轴的角度
     * @param uvLock     是否锁 UV
     * @param topVariant 模型是否分上下两种几何（竖放的大面连接在 half=TOP 时用 {@code *_top}）
     */
    public record Shape(ModelKind kind, int xRot, int yRot, boolean uvLock, boolean topVariant) {}

    /** 四个水平朝向的遍历顺序（与 blockstate 里 facing 的顺序一致）。 */
    public static final Direction[] HORIZONTAL_FACINGS =
            {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** 上面四个朝向对应的基准 Y 角度，沿用已调好的方向。 */
    private static final int[] FACING_Y = {270, 0, 90, 180};

    /** 平放形状的遍历顺序（与 blockstate 里 shape 的顺序一致）。 */
    private static final StairsShape[] FLAT_SHAPES = {
            StairsShape.STRAIGHT, StairsShape.INNER_LEFT, StairsShape.INNER_RIGHT,
            StairsShape.OUTER_LEFT, StairsShape.OUTER_RIGHT
    };

    /** 平放 half=BOTTOM 时的 Y 角度：行是形状，列是朝向。 */
    private static final int[][] FLAT_Y_BOTTOM = {
            {270, 0, 90, 180},   // straight
            {180, 270, 0, 90},   // inner_left
            {270, 0, 90, 180},   // inner_right
            {180, 270, 0, 90},   // outer_left
            {270, 0, 90, 180}    // outer_right
    };

    /** 平放 half=TOP 时的 Y 角度（绕 X 翻 180 之后，部分形状要补 180 才能对上贴图）。 */
    private static final int[][] FLAT_Y_TOP = {
            {270, 0, 90, 180},   // straight
            {270, 0, 90, 180},   // inner_left
            {0, 90, 180, 270},   // inner_right
            {270, 0, 90, 180},   // outer_left
            {0, 90, 180, 270}    // outer_right
    };

    /** 平放 half=BOTTOM 时是否锁 UV；half=TOP 一律锁，所以这里只记下半的。 */
    private static final boolean[][] FLAT_UV_BOTTOM = {
            {true, false, true, true},   // straight（east 不锁）
            {true, true, false, true},   // inner_left（south 不锁）
            {true, false, true, true},   // inner_right（east 不锁）
            {true, true, false, true},   // outer_left（south 不锁）
            {true, false, true, true}    // outer_right（east 不锁）
    };

    private StairShapeModels() {
    }

    public static int flatShapeCount() {
        return FLAT_SHAPES.length;
    }

    public static StairsShape flatShape(int index) {
        return FLAT_SHAPES[index];
    }

    /**
     * 竖放：连接形态 × 朝向 × 上下 → 模型与旋转。
     *
     * <p>几个容易踩的点写在这里：none 的 L 形上下翻转后几何一致，所以固定 x=0；corner 用的是
     * 原版 inner——补块在下半时基准角比别的形态多 270 度，补块在上半时靠绕 X 翻 180 得到，不能再
     * 换成 {@code *_top} 变体（那样会出现前后镜像）。大面连接三种才需要上下两套几何。
     */
    public static Shape vertical(StairConnection connection, Half half, int facingIndex) {
        int y = FACING_Y[facingIndex];
        boolean top = half == Half.TOP;
        return switch (connection) {
            case NONE -> new Shape(ModelKind.VERTICAL, 0, y, true, false);
            case CORNER -> half == Half.BOTTOM
                    ? new Shape(ModelKind.VANILLA_INNER, 0, (y + 270) % 360, true, false)
                    : new Shape(ModelKind.VANILLA_INNER, 180, y, true, false);
            case CONN_RIGHT -> new Shape(ModelKind.CONN_RIGHT, 0, y, true, top);
            case CONN_LEFT -> new Shape(ModelKind.CONN_LEFT, 0, y, true, top);
            case CONN_DOUBLE -> new Shape(ModelKind.CONN_DOUBLE, 0, y, true, top);
        };
    }

    /** 平放：原版形状 × 朝向 × 上下 → 模型与旋转。 */
    public static Shape flat(int shapeIndex, Half half, int facingIndex) {
        boolean top = half == Half.TOP;
        int y = top ? FLAT_Y_TOP[shapeIndex][facingIndex] : FLAT_Y_BOTTOM[shapeIndex][facingIndex];
        boolean uvLock = top || FLAT_UV_BOTTOM[shapeIndex][facingIndex];
        ModelKind kind = switch (shapeIndex) {
            case 0 -> ModelKind.FLAT_STRAIGHT;
            case 1, 2 -> ModelKind.FLAT_INNER;
            default -> ModelKind.FLAT_OUTER;
        };
        return new Shape(kind, top ? 180 : 0, y, uvLock, false);
    }
}
