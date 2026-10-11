package com.example.myfirstmod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 隐形、静止的“座位”实体。玩家骑乘它即可显示原版坐姿动画(双腿弯曲),
 * 由于座位本身不会移动,玩家也不会被移动输入带走。
 */
public class SitEntity extends Entity {
    public SitEntity(EntityType<? extends SitEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    // 座位本身不参与任何碰撞、推动或交互。
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // 不可见。
    @Override
    public boolean shouldRender(double x, double y, double z) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(this.getX(), this.getY(), this.getZ(), this.getX(), this.getY(), this.getZ());
    }

    // 玩家坐在座位上时,底边贴合座位位置(略微抬高以免穿地)。
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    public void tick() {
        super.tick();
        // 无人骑乘时自动清理(玩家死亡、离开或退出时)。
        if (!this.level().isClientSide && this.getPassengers().isEmpty()) {
            this.discard();
        }
    }
}
