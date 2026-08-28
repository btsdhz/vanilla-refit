package com.example.myfirstmod.block.entity;

import com.example.myfirstmod.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 混合半砖方块实体：一个格子里放两块“不同材质”的半砖。
 *
 * slabA / slabB 各自是原版水平半砖方块(如 stone_slab / oak_slab)。它们的几何朝向由
 * 方块状态里的 MODE / TYPE 决定（水平上下堆叠 或 竖直南北/东西拼合），本实体只负责记住
 * 这两块半砖分别是什么材质。渲染时由客户端模型包装器读取并合并两个材质模型的几何。
 */
public class MixedSlabBlockEntity extends BlockEntity {

    /** 渲染时向模型传递：第一块半砖材质（Block）。 */
    public static final ModelProperty<Block> MERGED_SLAB_A = new ModelProperty<>();
    /** 渲染时向模型传递：第二块半砖材质（Block）。 */
    public static final ModelProperty<Block> MERGED_SLAB_B = new ModelProperty<>();

    private Block slabA = Blocks.STONE_SLAB;
    private Block slabB = Blocks.STONE_SLAB;

    public MixedSlabBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** BlockEntityType.Builder.of 需要的工厂方法。 */
    public static MixedSlabBlockEntity create(BlockPos pos, BlockState state) {
        return new MixedSlabBlockEntity(ModBlockEntities.MIXED_SLAB.get(), pos, state);
    }

    public Block getSlabA() {
        return slabA;
    }

    public Block getSlabB() {
        return slabB;
    }

    /** 设置两块半砖材质，并用“合并上半/北半”判断哪块是 A。 */
    public boolean setBlocks(Block slabA, Block slabB) {
        if (!isUsableSlab(slabA) || !isUsableSlab(slabB)) {
            return false;
        }
        this.slabA = slabA;
        this.slabB = slabB;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public Block getFirstSlab() {
        return slabA;
    }

    public Block getSecondSlab() {
        return slabB;
    }

    private static boolean isUsableSlab(Block block) {
        return block instanceof SlabBlock && block != Blocks.AIR;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.slabA = readSlab(tag.getString("slab_a"));
        this.slabB = readSlab(tag.getString("slab_b"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        tag.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
    }

    /**
     * 客户端从区块包/方块实体数据包里恢复实体时，使用的是 {@code getUpdateTag} 的网络数据
     * （而非磁盘保存的 {@code saveAdditional}）。若不覆写，客户端收到的就是空 tag，
     * 材质会退回默认石头。因此这里也写入两块材质。
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("slab_a", BuiltInRegistries.BLOCK.getKey(this.slabA).toString());
        tag.putString("slab_b", BuiltInRegistries.BLOCK.getKey(this.slabB).toString());
        return tag;
    }

    /** 放置/变更时向客户端同步实体数据（配合 {@code getUpdateTag}）。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static Block readSlab(String id) {
        if (id == null || id.isEmpty()) {
            return Blocks.STONE_SLAB;
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return block instanceof SlabBlock ? block : Blocks.STONE_SLAB;
    }
}
