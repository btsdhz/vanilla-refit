package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.block.MixedSlabBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端“到底在挖哪个方块”的改判。
 *
 * <p>半砖的拾取形状里并入了一个“舒适框”，让鼠标指着半砖上半格时也命中半砖。此时玩家真正
 * 想挖的是半砖上方的下移方块（火把/灯笼/栅栏/墙）。改判放在“选择挖掘目标”的阶段
 * （{@code startDestroyBlock} / {@code continueDestroyBlock}）而不是破坏瞬间：
 * <ul>
 *     <li>挖掘进度、裂纹动画、破坏音效都按上方方块正常走，生存模式不会“秒破”；</li>
 *     <li>客户端直接把目标改判成上方方块并发包，服务端无需介入，行为与原版一致。</li>
 * </ul>
 *
 * <p>上台阶一侧是镜像：上半台阶下方有上移方块（灯笼/墙）时，台阶格下半格并入了该方块的形状，
 * 指着这半格时挖掘目标改判成台阶<em>下方</em>的方块。
 *
 * <p>右键（使用物品）走的是同一条改判：指着并入的那半格时，命中结果本来是台阶，
 * 拿拴绳点栅栏伸进台阶那半格就什么都不会发生（{@code LeadItem} 看到的是台阶）。
 * 这里把 {@code useItemOn} 的命中结果也改判成贴着的那个方块，并按对方自己的形状重算命中点；
 * 如果那半格里正好有栅栏上的绕绳结、射线也真的碰到它，则按“点到绳结”处理（解绳/继续连接）。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow(remap = false)
    private Minecraft minecraft;

    @Shadow(remap = false)
    public abstract boolean destroyBlock(BlockPos pos);

    @Shadow(remap = false)
    public abstract boolean startDestroyBlock(BlockPos pos, Direction face);

    @Shadow(remap = false)
    public abstract boolean continueDestroyBlock(BlockPos pos, Direction face);

    @Shadow(remap = false)
    public abstract InteractionResult useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult result);

    @Shadow(remap = false)
    public abstract InteractionResult interact(Player player, Entity target, InteractionHand hand);

    /** 防重入：改判后要再走一遍 {@code useItemOn}，不能又被自己拦一次。 */
    @Unique
    private boolean btsdhz_original$redirectingUse;

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$redirectStartTarget(BlockPos pos, Direction face,
                                                     CallbackInfoReturnable<Boolean> cir) {
        BlockPos target = btsdhz_original$redirectTarget(pos);
        if (target != pos) {
            // 目标不是下台阶本体（而是它上方的下移方块），按新目标重新走一遍；
            // 新目标不会再次命中改判，所以不会递归下去。
            cir.setReturnValue(this.startDestroyBlock(target, face));
            cir.cancel();
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$redirectContinueTarget(BlockPos pos, Direction face,
                                                        CallbackInfoReturnable<Boolean> cir) {
        BlockPos target = btsdhz_original$redirectTarget(pos);
        if (target != pos) {
            cir.setReturnValue(this.continueDestroyBlock(target, face));
            cir.cancel();
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void btsdhz_original$redirectDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level == null) {
            return;
        }
        // 潜行拆堆叠半砖：客户端不预测“拆半格”，交给服务端权威返回“保留的那半”，避免闪回。
        // 非潜行走原版整个拆除，客户端预测与服务端一致。
        var blockState = this.minecraft.level.getBlockState(pos);
        boolean isStackedSlab = blockState.getBlock() instanceof MixedSlabBlock
                || (blockState.getBlock() instanceof SlabBlock
                && blockState.hasProperty(SlabBlock.TYPE)
                && blockState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE);
        if (this.minecraft.player != null && this.minecraft.player.isShiftKeyDown() && isStackedSlab) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
    }

    /**
     * 指着“并入的舒适框”那半格右键时，把这次使用改判到贴着的那个方块上。
     *
     * <p>不改判的话，本次使用会落在台阶（或下移方块）本身上：拿拴绳点栅栏伸出去的那半格、
     * 点栅栏上的绕绳结都不会有反应。改判与挖掉一侧共用同一套判定（{@code redirectTarget}），
     * 所以只在“指着确实属于那个方块的那半格”时生效，指着台阶本体、台阶顶面（y=0.5）都不受影响。
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$redirectUseTarget(LocalPlayer player, InteractionHand hand,
                                                   BlockHitResult hitResult,
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (this.btsdhz_original$redirectingUse || this.minecraft.level == null) {
            return;
        }
        BlockPos pos = hitResult.getBlockPos();
        BlockPos target = btsdhz_original$redirectTarget(pos, hitResult);
        if (target.equals(pos)) {
            return;
        }
        LeashFenceKnotEntity knot = btsdhz_original$knotHitBy(player, target, hitResult);
        this.btsdhz_original$redirectingUse = true;
        try {
            cir.setReturnValue(knot != null
                    ? this.interact(player, knot, hand)
                    : this.useItemOn(player, hand, btsdhz_original$reclipToBlock(player, target, hitResult)));
        } finally {
            this.btsdhz_original$redirectingUse = false;
        }
        cir.cancel();
    }

    /**
     * 指向台阶“舒适框”时，把挖掘目标换成贴在台阶上的那个方块；
     * 指向台阶本体时原样返回，保持原版“挖台阶”。
     */
    @Unique
    private BlockPos btsdhz_original$redirectTarget(BlockPos pos) {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return pos;
        }
        HitResult hit = this.minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return pos;
        }
        return btsdhz_original$redirectTarget(pos, blockHit);
    }

    /**
     * 同 {@link #btsdhz_original$redirectTarget(BlockPos)}，但用调用方给的命中结果判断
     * （右键走的是 {@code useItemOn} 传进来的那个，不一定是 {@code minecraft.hitResult}）。
     */
    @Unique
    private BlockPos btsdhz_original$redirectTarget(BlockPos pos, BlockHitResult blockHit) {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return pos;
        }
        // 下台阶 + 上方下移方块：指向台阶格上半格（舒适框）时改判为上方方块
        if (SlabSupport.isBottomSlab(this.minecraft.level, pos)
                && SlabSupport.isLoweredOnSlabAbove(this.minecraft.level, pos)
                && btsdhz_original$aimsAtSlabHalf(blockHit, pos, true)) {
            return pos.above();
        }
        // 上台阶 + 下方上移方块：指向台阶格下半格（舒适框）时改判为下方方块
        if (SlabSupport.isTopSlab(this.minecraft.level, pos)
                && SlabSupport.isRaisedUnderTopSlabBelow(this.minecraft.level, pos)
                && btsdhz_original$aimsAtSlabHalf(blockHit, pos, false)) {
            return pos.below();
        }
        // 下移方块叠着放（如下台阶上的栅栏/墙上再放火把、灯笼）：上方方块整体下移后有一部分伸进本格，
        // 指向本格上半格时改判为上方方块
        if (SlabSupport.hasLoweredBlockAbove(this.minecraft.level, pos)
                && btsdhz_original$aimsAtSlabHalf(blockHit, pos, true)) {
            return pos.above();
        }
        // 镜像：上移方块叠着放（如上台阶下的墙下面再挂灯笼），指向本格下半格时改判为下方方块
        if (SlabSupport.hasRaisedBlockBelow(this.minecraft.level, pos)
                && btsdhz_original$aimsAtSlabHalf(blockHit, pos, false)) {
            return pos.below();
        }
        return pos;
    }

    /**
     * 当前瞄的是不是台阶格的“舒适框”那一半格。
     *
     * @param aboveHalf true=看上半格（下移方块在台阶上方）；false=看下半格（上移方块在台阶下方）
     */
    @Unique
    private boolean btsdhz_original$aimsAtSlabHalf(BlockHitResult blockHit, BlockPos pos, boolean aboveHalf) {
        if (blockHit == null || !blockHit.getBlockPos().equals(pos)) {
            return false;
        }
        double half = blockHit.getLocation().y - pos.getY();
        return aboveHalf ? half > 0.5 : half < 0.5;
    }

    /**
     * 把命中点重新算到目标方块自己的形状上。
     *
     * <p>服务端会拒绝“命中点离被命中方块中心超过 1 格”的右键包，所以不能只把方块坐标换掉、
     * 点还留在台阶格那边。这里用玩家视线与目标方块当前形状（含本模组位移）求交，拿到真实的面与点；
     * 万一求不到（例如形状是空心的），退回原命中点——相邻格的距离仍在 1 格以内，服务端照收。
     */
    @Unique
    private BlockHitResult btsdhz_original$reclipToBlock(Player player, BlockPos target, BlockHitResult original) {
        Level level = this.minecraft.level;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(eye.distanceTo(original.getLocation()) + 0.1));
        BlockState state = level.getBlockState(target);
        VoxelShape shape = state.getShape(level, target, CollisionContext.of(player));
        if (!shape.isEmpty()) {
            BlockHitResult reclipped = shape.clip(eye, end, target);
            if (reclipped != null) {
                return reclipped;
            }
        }
        return new BlockHitResult(original.getLocation(), original.getDirection(), target, original.isInside());
    }

    /**
     * @return 改判目标栅栏上、视线真的碰到的那一个绕绳结；没有则 null
     *
     * <p>绳结整体位移半格后，它有一半落在台阶那一格里，那半格的点会被台阶的舒适框先接住。
     * 如果玩家其实是在点绳结（拴绳的线圈），这里按“点到绳结”处理，而不是改成点栅栏方块。
     */
    @Unique
    private LeashFenceKnotEntity btsdhz_original$knotHitBy(Player player, BlockPos target, BlockHitResult hitResult) {
        Level level = this.minecraft.level;
        if (!level.getBlockState(target).is(BlockTags.FENCES)) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(eye.distanceTo(hitResult.getLocation()) + 0.1));
        for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(target).inflate(0.5))) {
            if (knot.getPos().equals(target)
                    && knot.getBoundingBox().inflate(0.05).clip(eye, end).isPresent()) {
                return knot;
            }
        }
        return null;
    }
}
