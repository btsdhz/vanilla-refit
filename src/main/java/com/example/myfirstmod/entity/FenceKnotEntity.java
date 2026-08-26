package com.example.myfirstmod.entity;

import com.example.myfirstmod.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 位于一根栅栏上的绕绳结,与另一根栅栏上的伙伴绳结构成一条连接。
 *
 * <p>它的判定框和原版拴绳结一致(放在栅栏上),可被玩家右键点击来解除整条连接并掉落拴绳。
 * 仅当 {@link #isPrimary()} 为 true 时才负责绘制两点间的悬链线,避免两个绳结重复绘制。</p>
 */
public class FenceKnotEntity extends LeashFenceKnotEntity implements IEntityWithComplexSpawn {
    private BlockPos partner;
    private boolean primary;
    /** 创建时是否消耗了一根拴绳(生存为 true,创造为 false)。 */
    private boolean leadConsumed;

    public FenceKnotEntity(EntityType<? extends FenceKnotEntity> entityType, Level level) {
        super(entityType, level);
    }

    public FenceKnotEntity(Level level, BlockPos pos, BlockPos partner, boolean primary, boolean leadConsumed) {
        this(ModEntities.FENCE_KNOT.get(), level);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
        this.partner = partner;
        this.primary = primary;
        this.leadConsumed = leadConsumed;
    }

    public BlockPos getPartner() {
        return this.partner;
    }

    public boolean isPrimary() {
        return this.primary;
    }

    public boolean isLeadConsumed() {
        return this.leadConsumed;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (this.partner == null) {
            return super.getBoundingBoxForCulling();
        }
        Vec3 a = this.getRopeHoldPosition(0.0F);
        Vec3 b = new Vec3(this.partner.getX() + 0.5, this.partner.getY() + 0.575, this.partner.getZ() + 0.5);
        return new AABB(
                Math.min(a.x, b.x) - 0.5, Math.min(a.y, b.y) - 0.5, Math.min(a.z, b.z) - 0.5,
                Math.max(a.x, b.x) + 0.5, Math.max(a.y, b.y) + 0.5, Math.max(a.z, b.z) + 0.5
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !this.isRemoved()
                && this.partner != null
                && !this.level().getBlockState(this.partner).is(BlockTags.FENCES)) {
            this.cancelConnection();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        this.cancelConnection();
        return InteractionResult.CONSUME;
    }

    private void cancelConnection() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.partner != null) {
            AABB search = new AABB(this.partner).inflate(1.0);
            for (FenceKnotEntity other : this.level().getEntitiesOfClass(FenceKnotEntity.class, search)) {
                if (other != this && other.getPos().equals(this.partner)) {
                    other.discard();
                }
            }
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        // 只有生存模式创建(消耗过拴绳)才返还;创造模式不返还,与原版一致。
        if (this.leadConsumed) {
            // 掉在栅栏桩的“北侧”空气处(相对绳结中心向北 0.25 格),避免与栅栏重合被弹飞。
            BlockPos p = this.getPos();
            ItemEntity lead = new ItemEntity(this.level(), p.getX() + 0.5, p.getY() + 0.45, p.getZ() + 0.25, new ItemStack(Items.LEAD));
            lead.setDefaultPickUpDelay();
            this.level().addFreshEntity(lead);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        // LeashFenceKnotEntity 原版为空实现,须自行保存挂靠位置、伙伴与主/从标记。
        compound.putLong("AttachPos", this.getPos().asLong());
        compound.putLong("Partner", this.partner.asLong());
        compound.putBoolean("Primary", this.primary);
        compound.putBoolean("LeadConsumed", this.leadConsumed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        BlockPos p = BlockPos.of(compound.getLong("AttachPos"));
        this.setPos(p.getX(), p.getY(), p.getZ());
        this.partner = BlockPos.of(compound.getLong("Partner"));
        this.primary = compound.getBoolean("Primary");
        this.leadConsumed = compound.getBoolean("LeadConsumed");
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeLong(this.partner.asLong());
        buffer.writeBoolean(this.primary);
        buffer.writeBoolean(this.leadConsumed);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.partner = BlockPos.of(buffer.readLong());
        this.primary = buffer.readBoolean();
        this.leadConsumed = buffer.readBoolean();
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
