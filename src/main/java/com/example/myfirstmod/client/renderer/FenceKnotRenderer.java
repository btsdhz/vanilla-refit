package com.example.myfirstmod.client.renderer;

import com.example.myfirstmod.entity.FenceKnotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 渲染栅栏上的绕绳结,以及它连接的所有悬链线拴绳;处于待连接状态时还会渲染到玩家手部的拴绳。
 */
public class FenceKnotRenderer extends EntityRenderer<FenceKnotEntity> {
    private static final double CATENARY_A = 3.0D;
    private static final int STEPS = 24;
    private static final float ROPE_THICKNESS = 0.05F;
    private static final float HALF_THICKNESS = ROPE_THICKNESS / 2.0F;
    private static final ResourceLocation KNOT_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/entity/lead_knot.png");

    private final LeashKnotModel<FenceKnotEntity> knotModel;

    public FenceKnotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.knotModel = new LeashKnotModel<>(context.bakeLayer(ModelLayers.LEASH_KNOT));
    }

    @Override
    public void render(FenceKnotEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 绕绳结本体。
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.knotModel.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        VertexConsumer knotBuffer = buffer.getBuffer(this.knotModel.renderType(KNOT_LOCATION));
        this.knotModel.renderToBuffer(poseStack, knotBuffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        Vec3 self = entity.getRopeHoldPosition(partialTicks);

        // 为每个伙伴绘制通往伙伴栅栏的悬链线(只由位置“较小”的一侧绘制,避免重复)。
        for (Map.Entry<BlockPos, Boolean> entry : entity.getPartners().entrySet()) {
            BlockPos partner = entry.getKey();
            if (entity.getPos().compareTo(partner) < 0) {
                Vec3 end = new Vec3(partner.getX() + 0.5, partner.getY() + 0.575, partner.getZ() + 0.5);
                if (end.distanceToSqr(self) > 1.0E-6D) {
                    renderRope(entity, self, end, partialTicks, poseStack, buffer);
                }
            }
        }

        // 待连接状态:从本绳结渲染一条拴绳到玩家手部。
        UUID pending = entity.getPendingPlayer();
        if (pending != null) {
            Player target = null;
            for (Player player : entity.level().players()) {
                if (player.getUUID().equals(pending)) {
                    target = player;
                    break;
                }
            }
            if (target != null) {
                Vec3 hand = target.getRopeHoldPosition(partialTicks);
                if (hand.distanceToSqr(self) > 1.0E-6D) {
                    renderRope(entity, self, hand, partialTicks, poseStack, buffer);
                }
            }
        }
    }

    private void renderRope(FenceKnotEntity entity, Vec3 startWorld, Vec3 endWorld,
                            float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();
        double ax = startWorld.x - ox;
        double ay = startWorld.y - oy;
        double az = startWorld.z - oz;
        double bx = endWorld.x - ox;
        double by = endWorld.y - oy;
        double bz = endWorld.z - oz;

        double fx = bx - ax;
        double fy = by - ay;
        double fz = bz - az;
        double span = Math.sqrt(fx * fx + fz * fz);

        Vec3 tangent = new Vec3(fx, fy, fz).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 perpU = tangent.cross(up);
        if (perpU.lengthSqr() < 1.0E-8D) {
            perpU = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            perpU = perpU.normalize();
        }
        Vec3 perpV = tangent.cross(perpU).normalize();

        int blockLightA = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(startWorld));
        int blockLightB = entity.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(endWorld));
        int skyA = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(startWorld));
        int skyB = entity.level().getBrightness(LightLayer.SKY, BlockPos.containing(endWorld));

        double aParam = Math.max(CATENARY_A * span, 1.0E-4D);
        double endYRel = aParam * Math.cosh(span / (2.0D * aParam));

        VertexConsumer vc = buffer.getBuffer(RenderType.leash());
        Matrix4f m = poseStack.last().pose();

        double[] cx = new double[STEPS + 1];
        double[] cy = new double[STEPS + 1];
        double[] cz = new double[STEPS + 1];
        int[] lights = new int[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            float f = (float) i / (float) STEPS;
            double xc = (f - 0.5D) * span;
            double yDrop = aParam * Math.cosh(xc / aParam) - endYRel;
            cx[i] = ax + fx * f;
            cy[i] = ay + fy * f + yDrop;
            cz[i] = az + fz * f;
            lights[i] = LightTexture.pack(
                    (int) Mth.lerp(f, (float) blockLightA, (float) blockLightB),
                    (int) Mth.lerp(f, (float) skyA, (float) skyB));
        }

        for (int i = 0; i <= STEPS; i++) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            addVertex(vc, m, cx[i] + perpU.x * HALF_THICKNESS, cy[i] + perpU.y * HALF_THICKNESS, cz[i] + perpU.z * HALF_THICKNESS, shade, lights[i]);
            addVertex(vc, m, cx[i] - perpU.x * HALF_THICKNESS, cy[i] - perpU.y * HALF_THICKNESS, cz[i] - perpU.z * HALF_THICKNESS, shade, lights[i]);
        }
        for (int i = STEPS; i >= 0; i--) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            addVertex(vc, m, cx[i] + perpV.x * HALF_THICKNESS, cy[i] + perpV.y * HALF_THICKNESS, cz[i] + perpV.z * HALF_THICKNESS, shade, lights[i]);
            addVertex(vc, m, cx[i] - perpV.x * HALF_THICKNESS, cy[i] - perpV.y * HALF_THICKNESS, cz[i] - perpV.z * HALF_THICKNESS, shade, lights[i]);
        }
        flushLeash(buffer);
    }

    private static void flushLeash(MultiBufferSource buffer) {
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.leash());
        }
    }

    private void addVertex(VertexConsumer buffer, Matrix4f pose,
                           double x, double y, double z, float shade, int light) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(0.5F * shade, 0.4F * shade, 0.3F * shade, 1.0F)
                .setLight(light);
    }

    @Override
    public ResourceLocation getTextureLocation(FenceKnotEntity entity) {
        return KNOT_LOCATION;
    }
}
