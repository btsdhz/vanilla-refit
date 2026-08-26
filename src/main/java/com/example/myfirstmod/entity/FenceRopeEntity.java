package com.example.myfirstmod.entity;

import com.example.myfirstmod.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 连接到两根栅栏之间的拴绳。
 *
 * <p>它在两端各挂靠一根栅栏,渲染时画成一条微微下垂的悬链线(双曲余弦函数)。
 * 任一端的栅栏被移除时,绳子会自动断开并掉落一根拴绳。</p>
 */
public class FenceRopeEntity extends Entity implements IEntityWithComplexSpawn {
    /** 绳子挂靠处(拴绳结上沿)相对栅栏方块中心的抬升,与原版拴绳的 rope hold 一致。 */
    private static final double ANCHOR_Y = 0.575;
    /** 绕绳结本体相对栅栏方块中心的抬升,与原版拴绳结一致。 */
    private static final double KNOT_Y = 0.375;

    private BlockPos from;
    private BlockPos to;

    public FenceRopeEntity(EntityType<? extends FenceRopeEntity> entityType, Level level) {
        super(entityType, level);
    }

    public FenceRopeEntity(Level level, BlockPos from, BlockPos to) {
        this(ModEntities.FENCE_ROPE.get(), level);
        this.from = from;
        this.to = to;
        this.reposition();
    }

    private void reposition() {
        if (from == null || to == null) {
            return;
        }
        this.setPos(
                (from.getX() + to.getX()) / 2.0 + 0.5,
                (from.getY() + to.getY()) / 2.0 + ANCHOR_Y,
                (from.getZ() + to.getZ()) / 2.0 + 0.5
        );
        Vec3 a = this.getAnchorA();
        Vec3 b = this.getAnchorB();
        this.setBoundingBox(new AABB(
                Math.min(a.x, b.x) - 0.4, Math.min(a.y, b.y) - 0.4, Math.min(a.z, b.z) - 0.4,
                Math.max(a.x, b.x) + 0.4, Math.max(a.y, b.y) + 0.4, Math.max(a.z, b.z) + 0.4
        ));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public BlockPos getFrom() {
        return this.from;
    }

    public BlockPos getTo() {
        return this.to;
    }

    /** 绳子 A 端挂靠点(世界坐标)。 */
    public Vec3 getAnchorA() {
        return anchorFor(this.from);
    }

    /** 绳子 B 端挂靠点(世界坐标)。 */
    public Vec3 getAnchorB() {
        return anchorFor(this.to);
    }

    /** A 端绕绳结中心(世界坐标)。 */
    public Vec3 getKnotAnchorA() {
        return knotAnchorFor(this.from);
    }

    /** B 端绕绳结中心(世界坐标)。 */
    public Vec3 getKnotAnchorB() {
        return knotAnchorFor(this.to);
    }

    private static Vec3 anchorFor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + ANCHOR_Y, pos.getZ() + 0.5);
    }

    private static Vec3 knotAnchorFor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + KNOT_Y, pos.getZ() + 0.5);
    }

    private boolean isFence(BlockPos pos) {
        return pos != null && this.level().getBlockState(pos).is(BlockTags.FENCES);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && (!isFence(this.from) || !isFence(this.to))) {
            this.breakRope();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        this.breakRope();
        return InteractionResult.CONSUME;
    }

    private void breakRope() {
        if (this.level().isClientSide) {
            return;
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        this.spawnAtLocation(new ItemStack(Items.LEAD));
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (from == null || to == null) {
            return this.getBoundingBox();
        }
        Vec3 a = this.getAnchorA();
        Vec3 b = this.getAnchorB();
        return new AABB(
                Math.min(a.x, b.x) - 1.0, Math.min(a.y, b.y) - 1.0, Math.min(a.z, b.z) - 1.0,
                Math.max(a.x, b.x) + 1.0, Math.max(a.y, b.y) + 1.0, Math.max(a.z, b.z) + 1.0
        );
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putLong("From", this.from.asLong());
        compound.putLong("To", this.to.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.from = BlockPos.of(compound.getLong("From"));
        this.to = BlockPos.of(compound.getLong("To"));
        this.reposition();
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(this.from.asLong());
        buffer.writeLong(this.to.asLong());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.from = BlockPos.of(buffer.readLong());
        this.to = BlockPos.of(buffer.readLong());
        this.reposition();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.LEAD);
    }
}
