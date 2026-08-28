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
 * 统一的“半砖放置/填充”判定：只要点击面朝向某个半砖的空余半砖空间，就在该格填入半砖。
 *
 * 这套规则对所有方向、平放/竖放、同材质/不同材质一致：
 *  - 同材质 → 设成原版 DOUBLE（两半同材质的完整方块）；
 *  - 不同材质 → 设成一格混合半砖（两块不同材质各占一半）。
 *
 * “空余半砖空间”指半砖缺的那一半所在方向。触发点击分两种：
 *  - 直接点半砖本体朝向空余空间的那一面；
 *  - 点空余空间旁边某个方块、且该面朝向半砖空余空间的那一面。
 *
 * 朝向编码（与 MixedSlabBlock 渲染一致）：
 *  - 0 = 平放下半（MODE=SLAB, TYPE=BOTTOM）      上半=1
 *  - 2 = 竖直北半（MODE=VERTICAL_NS, TYPE=BOTTOM）南半=3
 *  - 4 = 竖直西半（MODE=VERTICAL_EW, TYPE=BOTTOM）东半=5
 *  A 永远取较小编号的一半，B 取较大编号的一半。
 */
public final class MixedSlabPlacement {

    private MixedSlabPlacement() {
    }

    /**
     * 统一入口：尝试把手持半砖 block 填进一个“朝向该半砖空余半砖空间”的格子。
     *
     * @return true 表示已处理（该格已变为 DOUBLE 或混合半砖），调用方应结束原逻辑。
     */
    public static boolean tryFill(Level level, BlockPos clickedPos, BlockState targetState, Block block,
                                  Direction clickedFace) {
        // 情形 A：点击半砖本体，点击面朝向它的空余半砖空间（点了“朝空余空间的那一面”）
        if (isDirectFillable(targetState, clickedFace)) {
            return fill(level, clickedPos, targetState, block);
        }
        // 情形 B：点击相邻方块、且该面朝向一个有空余半砖空间的半砖格
        BlockPos neighborPos = clickedPos.relative(clickedFace);
        if (!neighborPos.equals(clickedPos)) {
            BlockState sideState = level.getBlockState(neighborPos);
            if (isNeighborFillable(sideState)) {
                return fill(level, neighborPos, sideState, block);
            }
        }
        return false;
    }

    /** 点击半砖本体：该格是半砖、未填满，且点击面朝向空余半砖空间。 */
    private static boolean isDirectFillable(BlockState state, Direction clickedFace) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(ModBlockStateProperties.MODE)) {
            return false;
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) {
            return false;
        }
        int existing = orientationOf(state);
        return existing >= 0 && clickedFace == emptyFaceOf(existing);
    }

    /** 点击相邻方块：该面朝向一个“有空余半砖空间”的半砖格（不限定方向，覆盖对面与侧面）。 */
    private static boolean isNeighborFillable(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(ModBlockStateProperties.MODE)) {
            return false;
        }
        return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE && orientationOf(state) >= 0;
    }

    /** 在该格填入半砖：同材质→DOUBLE，不同材质→混合。 */
    private static boolean fill(Level level, BlockPos pos, BlockState targetState, Block block) {
        int existing = orientationOf(targetState);
        if (existing < 0) {
            return false;
        }
        boolean sameMaterial = targetState.getBlock() == block;
        if (sameMaterial) {
            // 同材质：原版 DOUBLE
            BlockState doubleState = targetState.setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
            if (level.setBlock(pos, doubleState, 3)) {
                level.playSound(null, pos, doubleState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
            return false;
        }

        // 不同材质：混合半砖
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
        // 合并成堆叠/混合半砖后统一清空含液状态（与原版堆叠半砖一致）
        FluidType fluid = FluidType.NONE;
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
