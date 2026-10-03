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
import net.minecraft.world.level.BlockGetter;
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
 * <p><b>只在完整、紧贴、露出的整方块表面画。</b>提示线的坐标是按“方块边界所在的平面”
 * （0 或 1）算的，只有整方块的六个面才正好落在那里；台阶/楼梯这类非整方块的上表面在
 * 方块内部，线会浮空，所以直接不画（不去花力气适配形状）。另外要求面正前方那一格没有实体，
 * 保证这个面是完整露出来的、也确实能往上放方块。
 *
 * <p>颜色与线宽和原版高亮外框完全一致（顶点色 0,0,0,0.4，线宽 1）。只额外画线，
 * 原版外框仍然照常绘制。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class PlacementGuideRenderer {

    /**
     * 与原版外框完全一致的顶点色。
     * 见 {@code LevelRenderer#renderHitOutline}：0,0,0,0.4。
     * 原版看起来是灰色，是因为半透明黑与原版高亮混合的结果；直接抄同一个值才会一致。
     */
    private static final float R = 0.0F;
    private static final float G = 0.0F;
    private static final float B = 0.0F;
    private static final float A = 0.4F;

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

        // 1. 命中方块必须是整方块：只有这样它的六个面才和方块边界平面重合，线才不会浮空。
        if (!isFullCube(mc.level, pos)) {
            return;
        }
        // 2. 面正前方那一格必须是空的（无实体碰撞），保证这个面完整露出、也放得下新方块。
        BlockPos frontPos = pos.relative(face);
        if (!mc.level.getBlockState(frontPos).getCollisionShape(mc.level, frontPos).isEmpty()) {
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
            addEdge(consumer, pose, face, edge[0], edge[1], edge[2], edge[3]);
        }
        poseStack.popPose();
    }

    /**
     * 方块是否为整方块：碰撞形状与单位立方体完全相同。
     *
     * <p>注意这里必须用 {@link BooleanOp#NOT_SAME}（两边不一样才算非空）。之前误用了
     * {@link BooleanOp#ONLY_FIRST}（“A 减去 B”），而台阶/楼梯的形状本来就完全包含在单位立方体内，
     * 相减结果恒为空，于是“任何在方块内的形状”都被判成了整方块，等于没判。
     */
    private static boolean isFullCube(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        try {
            VoxelShape shape = state.getCollisionShape(level, pos);
            return !shape.isEmpty() && !Shapes.joinIsNotEmpty(shape, Shapes.block(), BooleanOp.NOT_SAME);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 画一条边。输入是面内的两个 2D 坐标（0~1），按面朝向映射到方块局部 3D 坐标。
     * 顶/底：u=x, v=z；南北侧：u=x, v=y；东西侧：u=z, v=y。
     */
    private static void addEdge(VertexConsumer consumer, PoseStack.Pose pose, Direction face,
                                float u1, float v1, float u2, float v2) {
        float[] a = mapFace(face, u1, v1);
        float[] b = mapFace(face, u2, v2);
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

    private static float[] mapFace(Direction face, float u, float v) {
        return switch (face) {
            case UP -> new float[]{u, 1.0F, v};
            case DOWN -> new float[]{u, 0.0F, v};
            case NORTH -> new float[]{u, v, 0.0F};
            case SOUTH -> new float[]{u, v, 1.0F};
            case WEST -> new float[]{0.0F, v, u};
            case EAST -> new float[]{1.0F, v, u};
        };
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p, float nx, float ny, float nz) {
        consumer.addVertex(pose, p[0], p[1], p[2])
                .setColor(R, G, B, A)
                .setNormal(pose, nx, ny, nz);
    }
}
