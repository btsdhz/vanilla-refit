package com.example.myfirstmod.client;

import com.example.myfirstmod.BtsdhzOriginal;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * 手持台阶/楼梯时，在准星对着的方块面上额外画“放置区域”提示线。
 *
 * <p>本模组的竖台阶/竖楼梯是“看着方块面点上去就判定成竖直版”，判定只看点击落在面内的位置，
 * 原版那圈形状外框看不出会被切成哪几块，所以这里把切分线画出来：
 * <ul>
 *     <li>水平面（顶/底）：台阶画两条对角线（X），对应四个三角形空位；</li>
 *     <li>水平面：楼梯画十字（+），对应四个象限（决定 FACING）；</li>
 *     <li>侧面：上下等分的那条中线；</li>
 * </ul>
 *
 * <p><b>判定看的是“这个面是不是完整面”，不是“方块是不是整方块”。</b>
 * 很多非整方块也有完整面（下台阶的顶面、上台阶的底面、地毯顶面……）。具体做法：
 * <ol>
 *     <li>取命中方向那一侧的形状极值平面（顶面取 maxY、底面取 minY、南北面取 minZ/maxZ……）。
 *         这一点很关键——下台阶的顶面在 y=0.5 而不是 1.0，直接按方块边界画就会浮空；</li>
 *     <li>把该平面上的整个单位正方形与方块形状求差（ONLY_FIRST），差集为空才算“完整面”。
 *         台阶/楼梯这种缺一块的形状在这一步被排除，不用去挨个适配形状。</li>
 * </ol>
 *
 * <p>另外，台阶“朝空余半砖空间”的那张靠内的面（下台阶的顶面、上台阶的底面、竖半砖的内侧那一面）
 * 不画线：点它是把两块半砖合并成一整块（合并逻辑），不是紧邻放置，画在这里会误导。
 * 这类面的平面坐标都严格落在方块内部（0.5），而外侧那几张面都在 0 或 1，据此区分。
 *
 * <p>颜色与线宽和原版高亮外框完全一致（顶点色 0,0,0,0.4，线宽 1）。只额外画线，
 * 原版外框仍然照常绘制。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class PlacementGuideRenderer {

    /**
     * 与原版外框完全一致的顶点色。
     * 见 {@code LevelRenderer#renderHitOutline}：0,0,0,0.4。
     * 原版看着是灰色，是半透明黑与原版高亮混合的结果；直接抄同一个值才会一致。
     */
    private static final float R = 0.0F;
    private static final float G = 0.0F;
    private static final float B = 0.0F;
    private static final float A = 0.4F;

    /** 用来把“整张面”做成一片极薄的形状再做差集；取 1/1000 格，远小于任何具体形状。 */
    private static final double FACE_EPS = 1.0E-3;

    /** 台阶：两条对角线（X 分割），单位 [0,1]。 */
    private static final float[][] SLAB_EDGES = {
            {0F, 0F, 1F, 1F},
            {1F, 0F, 0F, 1F},
    };

    /** 楼梯：十字分割（+），单位 [0,1]。 */
    private static final float[][] STAIR_EDGES = {
            {0.5F, 0F, 0.5F, 1F},
            {0F, 0.5F, 1F, 0.5F},
    };

    /** 侧面：上下等分的中线，单位 [0,1]（映射到该面的两个轴）。 */
    private static final float[][] SIDE_EDGES = {
            {0F, 0.5F, 1F, 0.5F},
    };

    private PlacementGuideRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        ItemStack held = mc.player.getMainHandItem();
        boolean slab = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
        boolean stair = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StairBlock;
        if (!slab && !stair) {
            return;
        }

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();

        // 只有准星足够近才画，避免远处方块也带线。
        double reach = mc.player.blockInteractionRange() + 1.0D;
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return;
        }

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape;
        try {
            // 用渲染形状（和原版外框同源），玩家看到的面和它一致。
            shape = state.getShape(mc.level, pos);
        } catch (RuntimeException exception) {
            return;
        }
        if (shape.isEmpty()) {
            return;
        }

        // 命中方向那一侧的形状极值平面：顶面 maxY、底面 minY、北面 minZ、南面 maxZ……
        Direction.Axis axis = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(axis)
                : shape.min(axis);

        if (!isFaceComplete(shape, face, plane)) {
            return;
        }

        // 台阶靠内那面（朝空余半砖空间）点击是合并成整块半砖，不是紧邻放置，不画提示线。
        // 平面严格落在方块内部即为此类面；外侧的面都在 0 或 1。
        if (state.getBlock() instanceof SlabBlock && plane > 1.0E-6 && plane < 1.0 - 1.0E-6) {
            return;
        }

        boolean horizontalFace = face == Direction.UP || face == Direction.DOWN;
        // 水平面：台阶画 X，楼梯画十字；侧面：只画中间那条横线。
        float[][] edges = horizontalFace ? (slab ? SLAB_EDGES : STAIR_EDGES) : SIDE_EDGES;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        // 和原版高亮线一致地固定线宽。
        RenderSystem.lineWidth(1.0F);
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        for (float[] edge : edges) {
            addEdge(consumer, pose, face, plane, edge[0], edge[1], edge[2], edge[3]);
        }
        poseStack.popPose();
    }

    /**
     * 该面是不是完整面：把平面上的整个单位正方形做成一片极薄的形状，减去方块形状后为空，
     * 就说明整张面都被方块覆盖（也就是完整面）。
     *
     * <p>注意必须用 {@link BooleanOp#ONLY_FIRST}（“A 减 B”，A 是被减数）——这里 A 是那张整面。
     * 之前判断整方块时把它用反了（拿方块形状当被减数），导致几乎所有方块都被当成整方块。
     */
    private static boolean isFaceComplete(VoxelShape shape, Direction face, double plane) {
        // 薄片必须朝方块“内部”长（正方向的面往负方向让、负方向的面往正方向让）。
        // 之前按面名硬编码，南北/东西写反了：北面写成 z 从 -0.001 到 0，落在方块外面，
        // 于是四个侧面全被判成不完整，只有顶/底能画。
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double from = positive ? plane - FACE_EPS : plane;
        double to = positive ? plane : plane + FACE_EPS;
        VoxelShape fullFace = switch (face.getAxis()) {
            case X -> Shapes.box(from, 0.0, 0.0, to, 1.0, 1.0);
            case Y -> Shapes.box(0.0, from, 0.0, 1.0, to, 1.0);
            case Z -> Shapes.box(0.0, 0.0, from, 1.0, 1.0, to);
        };
        return !Shapes.joinIsNotEmpty(fullFace, shape, BooleanOp.ONLY_FIRST);
    }

    /**
     * 画一条边。输入是面内的两个 2D 坐标（0~1），按面朝向映射到方块局部 3D 坐标，
     * 并用命中面的实际平面坐标（不一定是 0/1，比如下台阶顶面是 0.5）。
     * 顶/底：u=x, v=z；南北侧：u=x, v=y；东西侧：u=z, v=y。
     */
    private static void addEdge(VertexConsumer consumer, PoseStack.Pose pose, Direction face, double plane,
                                float u1, float v1, float u2, float v2) {
        float[] a = mapFace(face, plane, u1, v1);
        float[] b = mapFace(face, plane, u2, v2);
        // 法线取“沿线方向归一化”，和原版 renderShape 一致。
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-6F) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        addVertex(consumer, pose, a, dx, dy, dz);
        addVertex(consumer, pose, b, dx, dy, dz);
    }

    private static float[] mapFace(Direction face, double plane, float u, float v) {
        float p = (float) plane;
        return switch (face) {
            case UP, DOWN -> new float[]{u, p, v};
            case NORTH, SOUTH -> new float[]{u, v, p};
            case WEST, EAST -> new float[]{p, v, u};
        };
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p, float nx, float ny, float nz) {
        consumer.addVertex(pose, p[0], p[1], p[2])
                .setColor(R, G, B, A)
                .setNormal(pose, nx, ny, nz);
    }
}
