package com.example.myfirstmod.client;

import com.example.myfirstmod.BtsdhzOriginal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
 * <p>只画提示线，不改方块本身的高亮（原版外框仍然照常绘制）。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class PlacementGuideRenderer {

    /** 与面之间留的极小偏移，避免和方块面/原版外框 z-fighting。 */
    private static final float OFFSET = 0.002F;

    private static final float R = 0.0F;
    private static final float G = 0.0F;
    private static final float B = 0.0F;
    private static final float A = 1.0F;

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

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();

        // 只有准星足够近才画，避免远处的方块也带一堆线。
        double reach = mc.player.blockInteractionRange() + 1.0D;
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return;
        }

        BlockState hitState = mc.level.getBlockState(pos);
        if (hitState.isAir()) {
            return;
        }

        ItemStack held = mc.player.getMainHandItem();
        boolean slab = held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof SlabBlock;
        boolean stair = held.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock() instanceof StairBlock;
        if (!slab && !stair) {
            return;
        }

        Direction face = hit.getDirection();
        boolean horizontalFace = face == Direction.UP || face == Direction.DOWN;

        // 水平面：台阶画 X，楼梯画十字；侧面：只画中间那条横线。
        float[][] edges = horizontalFace ? (slab ? SLAB_EDGES : STAIR_EDGES) : SIDE_EDGES;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);

        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        for (float[] edge : edges) {
            addEdge(consumer, pose, face, edge[0], edge[1], edge[2], edge[3]);
        }
        poseStack.popPose();
    }

    /**
     * 画一条边。输入是面内的两个 2D 坐标（0~1），按面朝向映射到方块局部 3D 坐标。
     * 顶/底：u=x, v=z；南北侧：u=x, v=y；东西侧：u=z, v=y。
     */
    private static void addEdge(VertexConsumer consumer, PoseStack.Pose pose, Direction face,
                                float u1, float v1, float u2, float v2) {
        float[] a = mapFace(face, u1, v1);
        float[] b = mapFace(face, u2, v2);
        addVertex(consumer, pose, a);
        addVertex(consumer, pose, b);
    }

    private static float[] mapFace(Direction face, float u, float v) {
        return switch (face) {
            case UP -> new float[]{u, 1.0F + OFFSET, v};
            case DOWN -> new float[]{u, -OFFSET, v};
            case NORTH -> new float[]{u, v, -OFFSET};
            case SOUTH -> new float[]{u, v, 1.0F + OFFSET};
            case WEST -> new float[]{-OFFSET, v, u};
            case EAST -> new float[]{1.0F + OFFSET, v, u};
        };
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p) {
        consumer.addVertex(pose, p[0], p[1], p[2])
                .setColor(R, G, B, A)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
