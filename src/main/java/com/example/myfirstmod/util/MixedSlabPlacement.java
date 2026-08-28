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
     * 尝试在 clickedPos 处把 targetState 与新块 block 合并成混合半砖。
     *
     * @return 是否成功放到混合半砖；失败时不要做任何改动。
     */
    public static boolean tryMerge(Level level, BlockPos pos, BlockState targetState, Block block,
                                   Direction clickedFace, Block entityBlock) {
        if (!(targetState.getBlock() instanceof SlabBlock)) {
            return false;
        }
        if (targetState.getBlock() == block) {
            return false;   // 同材质走原版 DOUBLE 合并，不进混合半砖
        }
        if (!targetState.hasProperty(ModBlockStateProperties.MODE)) {
            return false;
        }

        // 现有半砖是哪一半
        int existing = orientationOf(targetState);
        if (existing < 0) {
            return false;
        }

        // 新半砖要放到对向那半边
        int newOrientation = oppositeOf(existing, clickedFace);
        if (newOrientation < 0) {
            return false;
        }

        ParticleOrientation o1 = orientation(existing);
        ParticleOrientation o2 = orientation(newOrientation);

        Block a;       // 较小编号的一半
        Block b;       // 较大编号的一半
        if (existing < newOrientation) {
            a = targetState.getBlock();
            b = block;
        } else {
            a = block;
            b = targetState.getBlock();
        }

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

    /** 依据目标半砖现有朝向 + 被点击的面，推断新半砖应放的朝向编号。 */
    private static int oppositeOf(int existing, Direction clickedFace) {
        if (existing == 0 || existing == 1) {
            // 水平：现有 BOTTOM 时点 UP 放 TOP(1)；现有 TOP 时点 DOWN 放 BOTTOM(0)
            return existing == 0 ? 1 : 0;
        }
        if (existing == 2 || existing == 3) {
            // 竖南北：北(2)↔南(3)，需点击其薄面
            return existing == 2 ? 3 : 2;
        }
        if (existing == 4 || existing == 5) {
            return existing == 4 ? 5 : 4;
        }
        return -1;
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
