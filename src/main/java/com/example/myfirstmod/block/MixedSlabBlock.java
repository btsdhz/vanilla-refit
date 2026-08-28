package com.example.myfirstmod.block;

import com.example.myfirstmod.ModBlockEntities;
import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 混合半砖：一格子里放两块“不同材质”的半砖。
 *
 * 复用现有属性体系：
 *  - MODE = SLAB            → 水平上下堆叠（下半 brA / 上半 brB）
 *  - MODE = VERTICAL_NS     → 竖直南北拼合
 *  - MODE = VERTICAL_EW     → 竖直东西拼合
 *  - TYPE 在原版半砖里表示 BOTTOM/TOP/DOUBLE；在混合半砖里用于记录“朝向”：
 *      MODE=SLAB 时 BOTTOM=下半、TOP=上半；MODE=VERTICAL_NS 时 BOTTOM=北半、TOP=南半；
 *      MODE=VERTICAL_EW 时 BOTTOM=西半、TOP=东半。
 *
 * 方块实体存 slabA/slabB 两块半砖的材质。
 */
public class MixedSlabBlock extends Block implements EntityBlock {
    public static final MapCodec<MixedSlabBlock> CODEC = simpleCodec(MixedSlabBlock::new);

    public MixedSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModBlockStateProperties.MODE);
        builder.add(net.minecraft.world.level.block.SlabBlock.TYPE);
        builder.add(ModBlockStateProperties.FLUID_TYPE);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // 取两块半砖中较慢的挖掘进度，避免破坏速度失真
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MixedSlabBlockEntity mixed) {
            float a = mixed.getFirstSlab().defaultBlockState().getDestroyProgress(player, level, pos);
            float b = mixed.getSecondSlab().defaultBlockState().getDestroyProgress(player, level, pos);
            return Math.min(a, b);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 两块半砖合成一整格：碰撞箱是完整方块
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MixedSlabBlockEntity(ModBlockEntities.MIXED_SLAB.get(), pos, state);
    }

    /** 拆除整块混合半砖时：释放两块半砖的掉落物（创造模式不掉落）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        // 释放两块半砖的物品
        if (blockEntity instanceof MixedSlabBlockEntity mixed) {
            if (!player.getAbilities().instabuild) {
                popResource(level, pos, new ItemStack(mixed.getFirstSlab()));
                popResource(level, pos, new ItemStack(mixed.getSecondSlab()));
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
