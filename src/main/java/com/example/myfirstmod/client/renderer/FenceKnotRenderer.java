package com.example.myfirstmod.client.renderer;

import com.example.myfirstmod.entity.FenceKnotEntity;
import com.example.myfirstmod.util.FenceKnotOffset;
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

    /**
     * 复用的临时缓冲区与取光用的可变坐标：渲染在主线程串行进行，
     * 复用它们可以避免“每条绳每帧”都分配 3 个 double 数组、1 个 int 数组和几个 BlockPos。
     */
    private static final double[] ROPE_X = new double[STEPS + 1];
    private static final double[] ROPE_Y = new double[STEPS + 1];
    private static final double[] ROPE_Z = new double[STEPS + 1];
    private static final int[] ROPE_LIGHT = new int[STEPS + 1];
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();

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
                double endX = partner.getX() + 0.5D;
                // 伙伴栅栏可能也被下移/上移了半格，端点要落在它的绕绳结上。
                double endY = FenceKnotOffset.ropeHoldY(entity.level(), partner);
                double endZ = partner.getZ() + 0.5D;
                if (self.distanceToSqr(endX, endY, endZ) > 1.0E-6D) {
                    renderRope(entity, self.x, self.y, self.z, endX, endY, endZ, poseStack, buffer);
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
                if (self.distanceToSqr(hand.x, hand.y, hand.z) > 1.0E-6D) {
                    renderRope(entity, self.x, self.y, self.z, hand.x, hand.y, hand.z, poseStack, buffer);
                }
            }
        }
    }

    private void renderRope(FenceKnotEntity entity, double startX, double startY, double startZ,
                            double endX, double endY, double endZ,
                            PoseStack poseStack, MultiBufferSource buffer) {
        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();
        double ax = startX - ox;
        double ay = startY - oy;
        double az = startZ - oz;
        double bx = endX - ox;
        double by = endY - oy;
        double bz = endZ - oz;

        double fx = bx - ax;
        double fy = by - ay;
        double fz = bz - az;
        double span = Math.sqrt(fx * fx + fz * fz);

        // 方向向量全部用 double 直接算，避免每帧为每条绳分配 Vec3。
        double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (length < 1.0E-6D) {
            return;
        }
        double tx = fx / length;
        double ty = fy / length;
        double tz = fz / length;

        // perpU = tangent × (0,1,0) = (-tz, 0, tx)
        double perpUx = -tz;
        double perpUy = 0.0D;
        double perpUz = tx;
        double perpULength = Math.sqrt(perpUx * perpUx + perpUz * perpUz);
        if (perpULength < 1.0E-4D) {
            perpUx = 1.0D;
            perpUy = 0.0D;
            perpUz = 0.0D;
        } else {
            perpUx /= perpULength;
            perpUz /= perpULength;
        }
        // perpV = tangent × perpU（两者已正交，长度接近 1，这里仍做一次归一化兜底）
        double perpVx = ty * perpUz - tz * perpUy;
        double perpVy = tz * perpUx - tx * perpUz;
        double perpVz = tx * perpUy - ty * perpUx;
        double perpVLength = Math.sqrt(perpVx * perpVx + perpVy * perpVy + perpVz * perpVz);
        if (perpVLength > 1.0E-4D) {
            perpVx /= perpVLength;
            perpVy /= perpVLength;
            perpVz /= perpVLength;
        }

        LIGHT_POS.set(Mth.floor(startX), Mth.floor(startY), Mth.floor(startZ));
        int blockLightA = entity.level().getBrightness(LightLayer.BLOCK, LIGHT_POS);
        int skyA = entity.level().getBrightness(LightLayer.SKY, LIGHT_POS);
        LIGHT_POS.set(Mth.floor(endX), Mth.floor(endY), Mth.floor(endZ));
        int blockLightB = entity.level().getBrightness(LightLayer.BLOCK, LIGHT_POS);
        int skyB = entity.level().getBrightness(LightLayer.SKY, LIGHT_POS);

        double aParam = Math.max(CATENARY_A * span, 1.0E-4D);
        double endYRel = aParam * Math.cosh(span / (2.0D * aParam));

        VertexConsumer vc = buffer.getBuffer(RenderType.leash());
        Matrix4f m = poseStack.last().pose();

        double[] cx = ROPE_X;
        double[] cy = ROPE_Y;
        double[] cz = ROPE_Z;
        int[] lights = ROPE_LIGHT;
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
            addVertex(vc, m, cx[i] + perpUx * HALF_THICKNESS, cy[i] + perpUy * HALF_THICKNESS, cz[i] + perpUz * HALF_THICKNESS, shade, lights[i]);
            addVertex(vc, m, cx[i] - perpUx * HALF_THICKNESS, cy[i] - perpUy * HALF_THICKNESS, cz[i] - perpUz * HALF_THICKNESS, shade, lights[i]);
        }
        for (int i = STEPS; i >= 0; i--) {
            float shade = i % 2 == 0 ? 1.0F : 0.7F;
            addVertex(vc, m, cx[i] + perpVx * HALF_THICKNESS, cy[i] + perpVy * HALF_THICKNESS, cz[i] + perpVz * HALF_THICKNESS, shade, lights[i]);
            addVertex(vc, m, cx[i] - perpVx * HALF_THICKNESS, cy[i] - perpVy * HALF_THICKNESS, cz[i] - perpVz * HALF_THICKNESS, shade, lights[i]);
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
