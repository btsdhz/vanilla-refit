package com.example.myfirstmod.client.renderer;

import com.example.myfirstmod.entity.FenceRopeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.LeashKnotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 在两根栅栏之间渲染一条“悬链线”拴绳,并在两端各放一个原版绕绳结。
 *
 * <p>曲线用双曲余弦 y = a·cosh(x/a),a 取跨距的 3 倍(中央下垂约跨距的 4%)。
 * 绳体用四边形截面(交叉双面)铺成,从各个视角看起来粗细一致;两端复用原版拴绳结模型。</p>
 */
public class FenceRopeRenderer extends EntityRenderer<FenceRopeEntity> {
    /** 悬链线参数 a(以“跨距”为单位)。 */
    private static final double CATENARY_A = 3.0D;
    private static final int STEPS = 24;
    private static final float ROPE_THICKNESS = 0.025F;
    private static final float HALF_THICKNESS = ROPE_THICKNESS / 2.0F;
    private static final ResourceLocation KNOT_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/entity/lead_knot.png");

    private final LeashKnotModel<FenceRopeEntity> knotModel;

    public FenceRopeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.knotModel = new LeashKnotModel<>(context.bakeLayer(ModelLayers.LEASH_KNOT));
    }

    @Override
    public void render(FenceRopeEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getFrom() == null || entity.getTo() == null) {
            return;
        }

        Vec3 worldA = entity.getAnchorA();
        Vec3 worldB = entity.getAnchorB();

        // PoseStack 原点已被 EntityRenderDispatcher 平移到实体渲染位置,换算成局部坐标。
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

        // 绳体方向(单位向量)与其两个相互垂直的截面方向。
        Vec3 tangent = new Vec3(fx, fy, fz).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 perpU = tangent.cross(up);
        if (perpU.lengthSqr() < 1.0E-8D) {
            perpU = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            perpU = perpU.normalize();
        }
        Vec3 perpV = tangent.cross(perpU).normalize();

        // 两端光照。
        int blockLightA = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(worldA));
        int blockLightB = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(worldB));
        int skyA = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(worldA));
        int skyB = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(worldB));

        // 悬链线参数(竖直退化时设下限,避免 cosh(0/0) 得 NaN)。
        double aParam = Math.max(CATENARY_A * span, 1.0E-4D);
        double endYRel = aParam * Math.cosh(span / (2.0D * aParam));

        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.leash());
        Matrix4f matrix4f = poseStack.last().pose();

        // 预计算绳体中心线与每步光照。
        double[] cx = new double[STEPS + 1];
        double[] cy = new double[STEPS + 1];
        double[] cz = new double[STEPS + 1];
        int[] lights = new int[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            float f = (float) i / (float) STEPS;
            double xc = (f - 0.5D) * span;
            double yDrop = aParam * Math.cosh(xc / aParam) - endYRel;
            int blockLight = (int) Mth.lerp(f, (float) blockLightA, (float) blockLightB);
            int skyLight = (int) Mth.lerp(f, (float) skyA, (float) skyB);
            cx[i] = ax + fx * f;
            cy[i] = ay + fy * f + yDrop;
            cz[i] = az + fz * f;
            lights[i] = LightTexture.pack(blockLight, skyLight);
        }

        // 交叉双面:水平面(perpU)正向,竖直面(perpV)反向,拼成一条条带且交点在端点处。
        for (int i = 0; i <= STEPS; i++) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            float colorR = 0.5F * shade;
            float colorG = 0.4F * shade;
            float colorB = 0.3F * shade;
            addVertex(vertexconsumer, matrix4f,
                    cx[i] + perpU.x * HALF_THICKNESS, cy[i] + perpU.y * HALF_THICKNESS, cz[i] + perpU.z * HALF_THICKNESS,
                    colorR, colorG, colorB, lights[i]);
            addVertex(vertexconsumer, matrix4f,
                    cx[i] - perpU.x * HALF_THICKNESS, cy[i] - perpU.y * HALF_THICKNESS, cz[i] - perpU.z * HALF_THICKNESS,
                    colorR, colorG, colorB, lights[i]);
        }
        for (int i = STEPS; i >= 0; i--) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            float colorR = 0.5F * shade;
            float colorG = 0.4F * shade;
            float colorB = 0.3F * shade;
            addVertex(vertexconsumer, matrix4f,
                    cx[i] + perpV.x * HALF_THICKNESS, cy[i] + perpV.y * HALF_THICKNESS, cz[i] + perpV.z * HALF_THICKNESS,
                    colorR, colorG, colorB, lights[i]);
            addVertex(vertexconsumer, matrix4f,
                    cx[i] - perpV.x * HALF_THICKNESS, cy[i] - perpV.y * HALF_THICKNESS, cz[i] - perpV.z * HALF_THICKNESS,
                    colorR, colorG, colorB, lights[i]);
        }

        // 两端绕绳结(复用原版拴绳结模型与纹理)。
        renderKnot(entity, poseStack, buffer, packedLight, ox, oy, oz, entity.getKnotAnchorA());
        renderKnot(entity, poseStack, buffer, packedLight, ox, oy, oz, entity.getKnotAnchorB());
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose,
                           double x, double y, double z,
                           float r, float g, float b, int light) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(r, g, b, 1.0F).setLight(light);
    }

    private void renderKnot(FenceRopeEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                            int packedLight, double ox, double oy, double oz, Vec3 knotAnchor) {
        poseStack.pushPose();
        poseStack.translate((float) (knotAnchor.x - ox), (float) (knotAnchor.y - oy), (float) (knotAnchor.z - oz));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.knotModel.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        VertexConsumer vertexconsumer = buffer.getBuffer(this.knotModel.renderType(KNOT_LOCATION));
        this.knotModel.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(FenceRopeEntity entity) {
        return KNOT_LOCATION;
    }
}
