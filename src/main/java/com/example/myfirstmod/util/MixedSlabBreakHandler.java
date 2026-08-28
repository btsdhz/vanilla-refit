package com.example.myfirstmod.util;

import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 混合半砖的“只拆一半”逻辑。
 *
 * 玩家破坏混合半砖时，根据准星命中在哪半边，拆掉那半，把另一半写回原版半砖状态，
 * 并只掉落被拆的那一块半砖物品。命中判定用从眼睛沿视线做射线检测。
 */
public final class MixedSlabBreakHandler {

    private MixedSlabBreakHandler() {
    }

    /**
     * 尝试从“堆叠半砖”里拆掉被命中的那一半。
     *
     * 覆盖两类：
     *  - 混合半砖（不同材质，MixedSlabBlock）：一半留原样、一半写回原版半砖；
     *  - 原版 DOUBLE（同材质，SlabBlock TYPE=DOUBLE）：拆掉一半，另一半写成该材质的单半砖。
     *
     * @return true 表示已完整处理（拆一半，保留另一半），调用方应返回 true 并结束原逻辑。
     */
    public static boolean tryBreakHalf(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getBlock() instanceof MixedSlabBlock) {
            return breakMixedHalf(level, pos, state, player);
        }
        // 原版 DOUBLE（同材质堆叠半砖）
        if (state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return breakDoubleHalf(level, pos, state, player);
        }
        return false;
    }

    private static boolean breakMixedHalf(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedSlabBlockEntity mixed)) {
            return false;
        }

        boolean hitFirst = isHitFirstHalf(level, pos, state, player);
        Block hitBlock = hitFirst ? mixed.getFirstSlab() : mixed.getSecondSlab();
        Block keepBlock = hitFirst ? mixed.getSecondSlab() : mixed.getFirstSlab();

        BlockState keepState = buildKeptState(keepBlock, state, hitFirst);
        return applyBreak(level, pos, keepState, hitBlock, player);
    }

    private static boolean breakDoubleHalf(Level level, BlockPos pos, BlockState state, Player player) {
        boolean hitFirst = isHitFirstHalf(level, pos, state, player);
        // 原版 DOUBLE 的材质就是一个方块；保留另一半为该材质的单半砖
        Block block = state.getBlock();
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        boolean keepTop = hitFirst;   // 拆了“第一半(下/北/西)”，剩下“第二半(上/南/东)”
        BlockState keepState = block.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, keepTop ? SlabType.TOP : SlabType.BOTTOM)
                .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE);
        return applyBreak(level, pos, keepState, block, player);
    }

    private static boolean applyBreak(Level level, BlockPos pos, BlockState keepState, Block hitBlock,
                                      Player player) {
        // 一次性把整格替换为“保留的那一半”，方块实体会随之被移除，避免两次区块更新
        level.setBlock(pos, keepState, 3);
        // 播放被拆那块半砖的破坏音效（mixin 完全接管了原版流程，音效需手动补回）
        level.playSound(null, pos, hitBlock.defaultBlockState().getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        // 只掉落被拆的那一块
        if (!player.getAbilities().instabuild) {
            Block.popResource(level, pos, new ItemStack(hitBlock));
        }
        return true;
    }

    /** 根据玩家视线命中位置判断是“第一半（0/北/西 半）”还是“第二半”。 */
    private static boolean isHitFirstHalf(Level level, BlockPos pos, BlockState state, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(6.0));
        net.minecraft.world.phys.BlockHitResult hit =
                level.clip(new ClipContext(
                        eye, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, player));

        if (hit.getBlockPos().equals(pos)) {
            Vec3 loc = hit.getLocation();
            double dx = loc.x - pos.getX();
            double dz = loc.z - pos.getZ();
            double dy = loc.y - pos.getY();
            VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
            return switch (mode) {
                // 水平：上半=第二半，下半=第一半
                case SLAB -> dy < 0.5;
                case VERTICAL_NS -> dz < 0.5;   // 北半=第一半
                case VERTICAL_EW -> dx < 0.5;   // 西半=第一半
            };
        }
        // 兜底：按玩家相对方块中心的方位判断
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 rel = eye.subtract(center);
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        return switch (mode) {
            case SLAB -> rel.y < 0;
            case VERTICAL_NS -> rel.z < 0;
            case VERTICAL_EW -> rel.x < 0;
        };
    }

    private static BlockState buildKeptState(Block keepBlock, BlockState mixedState, boolean hitFirst) {
        VerticalSlabMode mode = mixedState.getValue(ModBlockStateProperties.MODE);
        FluidType fluid = mixedState.getValue(ModBlockStateProperties.FLUID_TYPE);
        // 第一个半被拆掉 => 剩下的是第二个半；第二个半被拆掉 => 剩下第一个半
        boolean isFirstKept = !hitFirst;

        BlockState base = keepBlock.defaultBlockState();
        if (mode == VerticalSlabMode.SLAB) {
            SlabType type = isFirstKept ? SlabType.BOTTOM : SlabType.TOP;
            return base.hasProperty(SlabBlock.TYPE) ? base.setValue(SlabBlock.TYPE, type) : base;
        }
        // 竖直：写回本模组竖半砖（复用 MODE 属性）
        if (base.hasProperty(ModBlockStateProperties.MODE)) {
            return base.setValue(ModBlockStateProperties.MODE, mode)
                    .setValue(SlabBlock.TYPE, isFirstKept ? SlabType.BOTTOM : SlabType.TOP)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
        }
        return base;
    }

}
