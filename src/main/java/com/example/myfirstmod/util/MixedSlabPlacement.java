package com.example.myfirstmod.util;

import com.example.myfirstmod.ModBlocks;
import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 把“往已有半砖的另一半放不同材质”的情形，转成一格混合半砖（MixedSlabBlock）。
 *
 * 朝向编码（与 MixedSlabBlock 渲染一致）：
 *  - 0 = 水平下半（MODE=SLAB, TYPE=BOTTOM）      上半=1
 *  - 2 = 竖直北半（MODE=VERTICAL_NS, TYPE=BOTTOM）南半=3
 *  - 4 = 竖直西半（MODE=VERTICAL_EW, TYPE=BOTTOM）东半=5
 *  A 永远取较小编号的一半，B 取较大编号的一半。
 */
public final class MixedSlabPlacement {

    private MixedSlabPlacement() {
    }

    /**
     * 点击“半砖自身朝向空余半砖空间的那一面”时，把它与新块 block 合并成混合半砖。
     *
     * 空余空间方向 emptyFace 由目标半砖朝向决定。allowedOpposite=true 时也允许点击
     * 空余空间的反向面（可由调用方区分“点半砖本体”与“点邻居方块的面”两种走到这里的路径）。
     *
     * @return 是否成功合并。
     */
    public static boolean tryMerge(Level level, BlockPos pos, BlockState targetState, Block block,
                                   Direction clickedFace, boolean allowedOpposite) {
        if (!isMergeableHalf(targetState, block)) {
            return false;
        }
        Direction emptyFace = emptyFaceOf(orientationOf(targetState));
        boolean faceOk = clickedFace == emptyFace
                || (allowedOpposite && clickedFace == emptyFace.getOpposite());
        if (!faceOk) {
            return false;
        }
        return merge(level, pos, targetState, block);
    }

    /**
     * 从“空余半砖空间”那边的相邻方块发起合并：点击 emptyPos（=slabPos.offset(emptyFace)）
     * 处方块朝向本格空余空间的那一面时，把本格半砖合并为混合半砖。
     */
    public static boolean tryMergeFromNeighbor(Level level, BlockPos slabPos, BlockState targetState,
                                               BlockPos emptyPos, Block block, Direction clickedFace) {
        if (!isMergeableHalf(targetState, block)) {
            return false;
        }
        Direction emptyFace = emptyFaceOf(orientationOf(targetState));
        // 本格空余空间在 emptyFace 那侧，从 emptyPos(=slabPos.offset(emptyFace)) 点击
        // 朝向本格的面，即 clickedFace == emptyFace.getOpposite()。
        return clickedFace == emptyFace.getOpposite()
                && merge(level, slabPos, targetState, block);
    }

    private static boolean isMergeableHalf(BlockState state, Block block) {
        if (!(state.getBlock() instanceof SlabBlock)) {
            return false;
        }
        if (state.getBlock() == block) {
            return false;   // 同材质走原版 DOUBLE 合并，不进混合半砖
        }
        return state.hasProperty(ModBlockStateProperties.MODE);
    }

    private static boolean merge(Level level, BlockPos pos, BlockState targetState, Block block) {
        int existing = orientationOf(targetState);
        if (existing < 0) {
            return false;
        }
        int newOrientation = oppositeOf(existing);
        Block a;       // 较小编号的一半
        Block b;       // 较大编号的一半
        if (existing < newOrientation) {
            a = targetState.getBlock();
            b = block;
        } else {
            a = block;
            b = targetState.getBlock();
        }

        ParticleOrientation o1 = orientation(existing);
        BlockState mergedState = buildMergedState(o1.mode(), o1.type(), a, b, targetState, level, pos);
        if (level.setBlock(pos, mergedState, 3)) {
            if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
                mixed.setBlocks(a, b);
            }
            level.playSound(null, pos, mergedState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    /** 半砖“缺的那一半”朝向哪一面（即空余半砖空间所在方向）。 */
    private static Direction emptyFaceOf(int code) {
        return switch (code) {
            case 0 -> Direction.UP;     // 平放下半：空余在上
            case 1 -> Direction.DOWN;   // 平放上半：空余在下
            case 2 -> Direction.SOUTH;  // 竖南北、北半：空余在南
            case 3 -> Direction.NORTH;  // 竖南北、南半：空余在北
            case 4 -> Direction.EAST;   // 竖东西、西半：空余在东
            default -> Direction.WEST;  // 竖东西、东半：空余在西
        };
    }

    private static int oppositeOf(int code) {
        return switch (code) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            default -> 4;
        };
    }

    private static BlockState buildMergedState(VerticalSlabMode mode, SlabType type, Block a, Block b,
                                               BlockState target, Level level, BlockPos pos) {
        FluidType fluid = target.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                ? target.getValue(ModBlockStateProperties.FLUID_TYPE)
                : FluidType.NONE;
        return ModBlocks.MERGED_SLAB.get().defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, type)
                .setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
    }

    private static ParticleOrientation orientation(int code) {
        return switch (code) {
            case 0 -> new ParticleOrientation(VerticalSlabMode.SLAB, SlabType.BOTTOM);
            case 1 -> new ParticleOrientation(VerticalSlabMode.SLAB, SlabType.TOP);
            case 2 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM);
            case 3 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
            case 4 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM);
            default -> new ParticleOrientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
        };
    }

    /** 把一个半砖 BlockState 映射成朝向编号，无法识别返回 -1。 */
    private static int orientationOf(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.MODE)) {
            return -1;
        }
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        SlabType type = state.hasProperty(SlabBlock.TYPE) ? state.getValue(SlabBlock.TYPE) : SlabType.BOTTOM;
        return switch (mode) {
            case SLAB -> type == SlabType.BOTTOM ? 0 : 1;
            case VERTICAL_NS -> type == SlabType.BOTTOM ? 2 : 3;
            case VERTICAL_EW -> type == SlabType.BOTTOM ? 4 : 5;
        };
    }

    private record ParticleOrientation(VerticalSlabMode mode, SlabType type) {
    }
}
