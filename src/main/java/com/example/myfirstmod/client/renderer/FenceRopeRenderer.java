package com.example.myfirstmod.client.renderer;

import com.example.myfirstmod.entity.FenceRopeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 在两根栅栏之间渲染一条“悬链线”拴绳。
 *
 * <p>曲线表达式为 y = a·cosh(x/a)。这里选择的悬链线参数 a 为水平跨距的 3 倍,
 * 这样绳子只会微微下垂(约跨距的 4%),不会拉得很直也不会垂到地面。</p>
 */
public class FenceRopeRenderer extends EntityRenderer<FenceRopeEntity> {
    /** 悬链线参数 a(以“跨距”为单位)。a = 3*跨距 时,中央下垂约为跨距的 4%。 */
    private static final double CATENARY_A = 3.0D;
    private static final int STEPS = 24;
    private static final float ROPE_THICKNESS = 0.025F;
    private static final ResourceLocation KNOT_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/entity/lead_knot.png");

    public FenceRopeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FenceRopeEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getFrom() == null || entity.getTo() == null) {
            return;
        }

        Vec3 worldA = entity.getAnchorA();
        Vec3 worldB = entity.getAnchorB();

        // PoseStack 的原点已被 EntityRenderDispatcher 平移到实体渲染位置,故换算成局部坐标。
        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();
        double ax = worldA.x - ox;
        double ay = worldA.y - oy;
        double az = worldA.z - oz;
        double bx = worldB.x - ox;
        double by = worldB.y - oy;
        double bz = worldB.z - oz;

        double fx = bx - ax;
        double fy = by - ay;
        double fz = bz - az;
        double span = Math.sqrt(fx * fx + fz * fz);

        // 端点光照(悬链线两端分别取各自所在方块的光照)。
        int blockLightA = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(worldA));
        int blockLightB = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(worldB));
        int skyA = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(worldA));
        int skyB = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(worldB));

        float halfW;
        double dxWidth;
        double dzWidth;
        if (span < 1.0E-6D) {
            // 完全竖直的退化情形:改用水平横向作为宽度方向。
            halfW = ROPE_THICKNESS / 2.0F;
            dxWidth = halfW;
            dzWidth = 0.0D;
        } else {
            halfW = (float) (Mth.invSqrt((float) (fx * fx + fz * fz)) * (double) ROPE_THICKNESS / 2.0D);
            dxWidth = fz * halfW;
            dzWidth = fx * halfW;
        }

        // 给悬链线参数设下限,避免竖直绳(跨距≈0)时 cosh(0/0) 出现 NaN。
        double aParam = Math.max(CATENARY_A * span, 1.0E-4D);
        double halfSpan = span / 2.0D;
        double endYRel = aParam * Math.cosh(halfSpan / aParam);

        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.leash());
        Matrix4f matrix4f = poseStack.last().pose();

        // 前向:0 -> STEPS
        for (int i = 0; i <= STEPS; i++) {
            emitVertexPair(vertexconsumer, matrix4f, fx, fy, fz, ax, ay, az, span, aParam, endYRel,
                    blockLightA, blockLightB, skyA, skyB, dxWidth, dzWidth, i, false);
        }
        // 反向:STEPS -> 0,让细带两面都可见
        for (int i = STEPS; i >= 0; i--) {
            emitVertexPair(vertexconsumer, matrix4f, fx, fy, fz, ax, ay, az, span, aParam, endYRel,
                    blockLightA, blockLightB, skyA, skyB, dxWidth, dzWidth, i, true);
        }
    }

    private void emitVertexPair(VertexConsumer buffer, Matrix4f pose,
                                double fx, double fy, double fz,
                                double ax, double ay, double az,
                                double span, double aParam, double endYRel,
                                int blockLightA, int blockLightB, int skyA, int skyB,
                                double dx, double dz, int index, boolean reverse) {
        float f = (float) index / (float) STEPS;
        int blockLight = (int) Mth.lerp(f, (float) blockLightA, (float) blockLightB);
        int skyLight = (int) Mth.lerp(f, (float) skyA, (float) skyB);
        int light = net.minecraft.client.renderer.LightTexture.pack(blockLight, skyLight);

        float shade = index % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
        float colorR = 0.5F * shade;
        float colorG = 0.4F * shade;
        float colorB = 0.3F * shade;

        double xc = (f - 0.5D) * span;
        double yDrop = aParam * Math.cosh(xc / aParam) - endYRel;

        double x = ax + fx * f;
        double y = ay + fy * f + yDrop;
        double z = az + fz * f;

        buffer.addVertex(pose, (float) (x - dx), (float) (y + ROPE_THICKNESS), (float) (z + dz))
                .setColor(colorR, colorG, colorB, 1.0F).setLight(light);
        buffer.addVertex(pose, (float) (x + dx), (float) y, (float) (z - dz))
                .setColor(colorR, colorG, colorB, 1.0F).setLight(light);
    }

    @Override
    public ResourceLocation getTextureLocation(FenceRopeEntity entity) {
        return KNOT_LOCATION;
    }
}
