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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
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

    /** 中键拾取时用于判断“点中哪一半”的缓存玩家（客户端每 tick 更新；服务端为 null）。 */
    public static Player cachedPlayer;

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
        // 两块半砖的材质可能对应不同工具（木头用斧、石头用镐）。如果取两者的较慢值，
        // 由于不存在同时对两种材质都有效的工具，任何工具都提不了速，等于工具适配完全失效。
        // 这里改成按准星实际命中的那一半的材质来算，与“潜行只拆一半”的判定保持一致。
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MixedSlabBlockEntity mixed) {
            Block target = isHitFirstHalf(level, pos, state, player)
                    ? mixed.getFirstSlab()
                    : mixed.getSecondSlab();
            return target.defaultBlockState().getDestroyProgress(player, level, pos);
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

    /** 中键拾取：返回“准星命中的那半”的半砖物品，避免返回无名结构物品。 */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
            Block hit = mixed.getFirstSlab();
            if (level.isClientSide() && cachedPlayer != null) {
                boolean first = isHitFirstHalf(level, pos, state, cachedPlayer);
                hit = first ? mixed.getFirstSlab() : mixed.getSecondSlab();
            }
            return new ItemStack(hit);
        }
        return super.getCloneItemStack(level, pos, state);
    }

    private static boolean isHitFirstHalf(BlockGetter level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof Level l) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(6.0));
            BlockHitResult hit = l.clip(new ClipContext(
                    eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getBlockPos().equals(pos)) {
                Vec3 loc = hit.getLocation();
                double dx = loc.x - pos.getX();
                double dz = loc.z - pos.getZ();
                double dy = loc.y - pos.getY();
                VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
                return switch (mode) {
                    case SLAB -> dy < 0.5;
                    case VERTICAL_NS -> dz < 0.5;
                    case VERTICAL_EW -> dx < 0.5;
                };
            }
        }
        return true;
    }

    /** 拆除整块混合半砖时：释放两块半砖的掉落物（创造模式不掉落）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        // 释放两块半砖的物品
        if (blockEntity instanceof MixedSlabBlockEntity mixed) {
            if (!player.getAbilities().instabuild) {
                // 每一半按自己的材质判定工具适配（用错工具能挖掉但不掉落）
                Block first = mixed.getFirstSlab();
                Block second = mixed.getSecondSlab();
                if (canHarvest(player, first)) {
                    popResource(level, pos, new ItemStack(first));
                }
                if (canHarvest(player, second)) {
                    popResource(level, pos, new ItemStack(second));
                }
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    /** 该半砖材质在当前工具下是否会掉落（原版语义：需要正确工具才能收获）。 */
    private static boolean canHarvest(Player player, Block slabBlock) {
        BlockState state = slabBlock.defaultBlockState();
        return !state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state);
    }
}
