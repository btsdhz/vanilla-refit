package com.example.myfirstmod.util;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * “栅栏 + 台阶”的跨格连接逻辑，与墙那一侧的 {@link WallSlabConnection} 一一对应。
 *
 * <p>栅栏贴到台阶上时会整体位移半格（下台阶上的栅栏 btsdhz_on_slab=true，
 * 上台阶下的栅栏 btsdhz_under_top_slab=true）。此时它要连接的栅栏并不在同一层，
 * 而在“台阶那一格”的四周：
 * <ul>
 *     <li>位移栅栏自身：下移栅栏看脚下台阶格的四个水平邻居、上移栅栏看头顶台阶格的四个水平邻居
 *         是不是栅栏，是则补上对应方向的连接；</li>
 *     <li>台阶四周的普通栅栏：看自己朝着台阶的那一面，台阶上/下方是不是对应的位移栅栏，是则补上连接。</li>
 * </ul>
 *
 * <p>与墙不同的是：栅栏的连接状态只有连/不连两种（{@code CrossCollisionBlock} 的四个布尔属性），
 * 没有墙那种 TALL/LOW 墙臂与立柱开关，所以这里只补连接位，不处理任何高度或立柱。
 */
public final class FenceSlabConnection {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** 防止 FenceBlock.updateShape 与这里的重算互相递归。 */
    private static final ThreadLocal<Boolean> RECALCULATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 防止 Level.setBlock 触发的 onPlace/onRemove 再次进入批量刷新。 */
    private static final ThreadLocal<Boolean> REFRESHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FenceSlabConnection() {
    }

    /**
     * 给栅栏补上跨格连接（带廉价过滤：与台阶无关的栅栏直接原样返回）。
     */
    public static BlockState withSlabConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        if (RECALCULATING.get() || !(state.getBlock() instanceof FenceBlock)) {
            return state;
        }
        if (!isSlabRelated(state, level, pos)) {
            return state;
        }
        return recomputeConnections(state, level, pos);
    }

    /**
     * 栅栏或台阶发生变化后，刷新可能被波及的栅栏（含斜向关系，原版不会主动通知）。
     *
     * @param changedState 变化后的方块状态；移除场景传被移除前的旧状态
     */
    public static void refreshAround(Level level, BlockPos pos, BlockState changedState) {
        if (REFRESHING.get()) {
            return;
        }
        boolean changedFence = changedState.getBlock() instanceof FenceBlock;
        boolean changedSlab = changedState.getBlock() instanceof SlabBlock;
        if (!changedFence && !changedSlab) {
            return;
        }
        REFRESHING.set(Boolean.TRUE);
        try {
            Set<BlockPos> candidates = new LinkedHashSet<>();
            // 变化点自身与紧邻的上下、水平方向都可能是下台阶，逐一收集它们牵涉的栅栏
            collectSlabFences(level, pos, candidates);
            collectSlabFences(level, pos.above(), candidates);
            collectSlabFences(level, pos.below(), candidates);
            for (Direction dir : HORIZONTAL) {
                collectSlabFences(level, pos.relative(dir), candidates);
            }
            if (changedSlab) {
                // 台阶刚被移除时 pos 处已经不是台阶，用旧状态再补一次它牵涉的栅栏
                candidates.add(pos.above());
                candidates.add(pos.below());
                for (Direction dir : HORIZONTAL) {
                    candidates.add(pos.relative(dir));
                }
            }
            for (BlockPos candidate : candidates) {
                refreshFence(level, candidate);
            }
        } finally {
            REFRESHING.set(Boolean.FALSE);
        }
    }

    /** 收集某个台阶牵涉的栅栏：下台阶→台阶上方的栅栏；上台阶→台阶下方的栅栏；两者都含台阶四邻的栅栏。 */
    private static void collectSlabFences(Level level, BlockPos slabPos, Set<BlockPos> out) {
        if (SlabSupport.isBottomSlab(level, slabPos)) {
            out.add(slabPos.above());
        } else if (SlabSupport.isTopSlab(level, slabPos)) {
            out.add(slabPos.below());
        } else {
            return;
        }
        for (Direction dir : HORIZONTAL) {
            out.add(slabPos.relative(dir));
        }
    }

    /** 重算指定位置的栅栏，并写回世界（状态没变则不动）。 */
    private static void refreshFence(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceBlock)) {
            return;
        }
        BlockState updated = state;
        if (updated.hasProperty(ModBlockStateProperties.ON_SLAB)
                && updated.hasProperty(ModBlockStateProperties.UNDER_TOP_SLAB)) {
            boolean onSlab = SlabSupport.isBottomSlab(level, pos.below());
            boolean underTopSlab = !onSlab && SlabSupport.isTopSlab(level, pos.above());
            updated = updated
                    .setValue(ModBlockStateProperties.ON_SLAB, onSlab)
                    .setValue(ModBlockStateProperties.UNDER_TOP_SLAB, underTopSlab);
        }
        updated = recomputeConnections(updated, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    /** 先用原版规则重算四个水平面（撤销失效连接），再补上台阶带来的跨格连接。 */
    private static BlockState recomputeConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        RECALCULATING.set(Boolean.TRUE);
        try {
            BlockState result = state;
            for (Direction dir : HORIZONTAL) {
                BlockPos neighbourPos = pos.relative(dir);
                result = result.updateShape(dir, level.getBlockState(neighbourPos), level, pos, neighbourPos);
            }
            for (Direction dir : HORIZONTAL) {
                if (!result.getValue(fenceProperty(dir)) && connectsAcrossSlab(result, level, pos, dir)) {
                    result = result.setValue(fenceProperty(dir), true);
                }
            }
            return result;
        } finally {
            RECALCULATING.set(Boolean.FALSE);
        }
    }

    /** 本栅栏是否可能与跨格连接有关（自己已位移、或水平邻居里有台阶）。 */
    private static boolean isSlabRelated(BlockState state, LevelAccessor level, BlockPos pos) {
        if (SlabSupport.isOnSlab(state) || SlabSupport.isUnderTopSlab(state)) {
            return true;
        }
        for (Direction dir : HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            if (SlabSupport.isBottomSlab(level, neighbourPos) || SlabSupport.isTopSlab(level, neighbourPos)) {
                return true;
            }
        }
        return false;
    }

    /** 判断 dir 方向是否需要“跨台阶”连接（只连栅栏，与墙那边只连墙一致）。 */
    private static boolean connectsAcrossSlab(BlockState self, LevelAccessor level, BlockPos pos, Direction dir) {
        // 下移栅栏：看脚下台阶格该方向的邻居
        if (SlabSupport.isOnSlab(self)) {
            BlockPos slabPos = pos.below();
            if (SlabSupport.isBottomSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.FENCES)) {
                return true;
            }
        }
        // 上移栅栏（贴在上台阶下方）：看头顶台阶格该方向的邻居
        if (SlabSupport.isUnderTopSlab(self)) {
            BlockPos slabPos = pos.above();
            if (SlabSupport.isTopSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.FENCES)) {
                return true;
            }
        }
        // 普通栅栏：看水平邻居是不是下台阶，且台阶上方是下移栅栏
        BlockPos neighbourPos = pos.relative(dir);
        if (SlabSupport.isBottomSlab(level, neighbourPos)) {
            BlockState above = level.getBlockState(neighbourPos.above());
            return above.is(BlockTags.FENCES) && SlabSupport.isOnSlab(above);
        }
        // 普通栅栏：看水平邻居是不是上台阶，且台阶下方是上移栅栏
        if (SlabSupport.isTopSlab(level, neighbourPos)) {
            BlockState below = level.getBlockState(neighbourPos.below());
            return below.is(BlockTags.FENCES) && SlabSupport.isUnderTopSlab(below);
        }
        return false;
    }

    private static BooleanProperty fenceProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        };
    }
}
