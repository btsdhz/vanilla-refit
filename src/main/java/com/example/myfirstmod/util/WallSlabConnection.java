package com.example.myfirstmod.util;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * “墙 + 台阶”的跨格连接逻辑。
 *
 * <p>墙贴到台阶上时会整体位移半格（下台阶上的墙 btsdhz_on_slab=true，上台阶下的墙
 * btsdhz_under_top_slab=true）。此时它要连接的墙并不在同一层，而在“台阶那一格”的四周：
 * <ul>
 *     <li>位移墙自身：下移墙看脚下台阶格的四个水平邻居、上移墙看头顶台阶格的四个水平邻居
 *         是不是墙类方块，是则补上对应方向的墙臂；</li>
 *     <li>台阶四周的普通墙：看自己朝着台阶的那一面，台阶上/下方是不是对应的位移墙，是则补上墙臂。</li>
 * </ul>
 *
 * <p>这类连接是原版不会计算的斜向关系，原版的邻居更新也不会通知到，所以这里除了在墙自身
 * 状态计算时补判定，还需要在墙/台阶被放置、移除后主动刷新受影响的墙。
 *
 * <p>补判定前会先用原版规则重算四个水平方向，这样当台阶或墙被拆掉、连接不再成立时，
 * 之前补出来的墙臂也能被正确撤销。
 */
public final class WallSlabConnection {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** 防止 WallBlock.updateShape 与这里的重算互相递归。 */
    private static final ThreadLocal<Boolean> RECALCULATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 防止 Level.setBlock 触发的 onPlace/onRemove 再次进入批量刷新。 */
    private static final ThreadLocal<Boolean> REFRESHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WallSlabConnection() {
    }

    /**
     * 给墙补上跨格连接（带廉价过滤：与台阶无关的墙直接原样返回）。
     */
    public static BlockState withSlabConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        if (RECALCULATING.get() || !(state.getBlock() instanceof WallBlock)) {
            return state;
        }
        if (!isSlabRelated(state, level, pos)) {
            return state;
        }
        return recomputeConnections(state, level, pos);
    }

    /**
     * 墙或台阶发生变化后，刷新可能被波及的墙（含斜向关系，原版不会主动通知）。
     *
     * @param changedState 变化后的方块状态；移除场景传被移除前的旧状态
     */
    public static void refreshAround(Level level, BlockPos pos, BlockState changedState) {
        if (REFRESHING.get()) {
            return;
        }
        boolean changedWall = changedState.is(BlockTags.WALLS);
        boolean changedSlab = changedState.getBlock() instanceof SlabBlock;
        if (!changedWall && !changedSlab) {
            return;
        }
        REFRESHING.set(Boolean.TRUE);
        try {
            Set<BlockPos> candidates = new LinkedHashSet<>();
            // 变化点自身与紧邻的上下、水平方向都可能是下台阶，逐一收集它们牵涉的墙
            collectSlabWalls(level, pos, candidates);
            collectSlabWalls(level, pos.above(), candidates);
            collectSlabWalls(level, pos.below(), candidates);
            for (Direction dir : HORIZONTAL) {
                collectSlabWalls(level, pos.relative(dir), candidates);
            }
            if (changedSlab) {
                // 台阶刚被移除时 pos 处已经不是台阶，用旧状态再补一次它牵涉的墙
                candidates.add(pos.above());
                candidates.add(pos.below());
                for (Direction dir : HORIZONTAL) {
                    candidates.add(pos.relative(dir));
                }
            }
            for (BlockPos candidate : candidates) {
                refreshWall(level, candidate);
            }
        } finally {
            REFRESHING.set(Boolean.FALSE);
        }
    }

    /** 收集某个台阶牵涉的墙：下台阶→台阶上方的墙；上台阶→台阶下方的墙；两者都含台阶四邻的墙。 */
    private static void collectSlabWalls(Level level, BlockPos slabPos, Set<BlockPos> out) {
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

    /** 重算指定位置的墙，并写回世界（状态没变则不动）。 */
    private static void refreshWall(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WallBlock)) {
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

    /** 先用原版规则重算四个水平面（撤销失效连接），再补上下台阶带来的跨格连接。 */
    private static BlockState recomputeConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        RECALCULATING.set(Boolean.TRUE);
        try {
            BlockState result = state;
            for (Direction dir : HORIZONTAL) {
                BlockPos neighbourPos = pos.relative(dir);
                result = result.updateShape(dir, level.getBlockState(neighbourPos), level, pos, neighbourPos);
            }
            for (Direction dir : HORIZONTAL) {
                if (result.getValue(wallProperty(dir)) == WallSide.NONE
                        && connectsAcrossSlab(result, level, pos, dir)) {
                    result = result.setValue(wallProperty(dir), WallSide.LOW);
                    result = recomputePost(result, level, pos);
                }
            }
            if (SlabSupport.isUnderTopSlab(result)) {
                result = forceFullHeightUnderTopSlab(result);
            }
            return result;
        } finally {
            RECALCULATING.set(Boolean.FALSE);
        }
    }

    /**
     * 上台阶下的墙补成“完整高度”的墙（立柱 + 墙臂都是 16/16 高）。
     *
     * <p>原版把墙当成“上方有方块”的条件是：上方方块碰撞形状的 DOWN 面能盖住立柱测试框。
     * 完整方块的 DOWN 面就是它自身（0~16 全高），所以成立；而上半台阶的下表面只占半格，
     * {@code getFaceShape(DOWN)} 会被切成 1/16 厚的薄片，盖不住测试框，于是原版判定不成立：
     * 墙只有 14/16 高的墙臂、也没有立柱。我们虽然把墙整体上移半格让它贴上台阶底面，
     * 但墙自己的形状仍矮 2/16，看上去就是台阶下面一条缝。
     *
     * <p>所以这里对“上移墙”手动按“上方被完整盖住”处理：补上立柱（UP=true），
     * 并把已有墙臂提升为 TALL，位移半格后正好顶到上台阶底面。
     */
    private static BlockState forceFullHeightUnderTopSlab(BlockState state) {
        BlockState result = state.setValue(WallBlock.UP, true);
        for (Direction dir : HORIZONTAL) {
            EnumProperty<WallSide> property = wallProperty(dir);
            if (result.getValue(property) != WallSide.NONE) {
                result = result.setValue(property, WallSide.TALL);
            }
        }
        return result;
    }

    /** 侧面连接变化后，用原版逻辑重算立柱（UP）。 */
    private static BlockState recomputePost(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        return state.updateShape(Direction.UP, level.getBlockState(abovePos), level, pos, abovePos);
    }

    /** 本墙是否可能与跨格连接有关（自己已位移、或水平邻居里有台阶）。 */
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

    /** 判断 dir 方向是否需要“跨台阶”连接。 */
    private static boolean connectsAcrossSlab(BlockState self, LevelAccessor level, BlockPos pos, Direction dir) {
        // 下移墙：看脚下台阶格该方向的邻居
        if (SlabSupport.isOnSlab(self)) {
            BlockPos slabPos = pos.below();
            if (SlabSupport.isBottomSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.WALLS)) {
                return true;
            }
        }
        // 上移墙（贴在上台阶下方）：看头顶台阶格该方向的邻居
        if (SlabSupport.isUnderTopSlab(self)) {
            BlockPos slabPos = pos.above();
            if (SlabSupport.isTopSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.WALLS)) {
                return true;
            }
        }
        // 普通墙：看水平邻居是不是下台阶，且台阶上方是下移墙
        BlockPos neighbourPos = pos.relative(dir);
        if (SlabSupport.isBottomSlab(level, neighbourPos)) {
            BlockState above = level.getBlockState(neighbourPos.above());
            return above.is(BlockTags.WALLS) && SlabSupport.isOnSlab(above);
        }
        // 普通墙：看水平邻居是不是上台阶，且台阶下方是上移墙
        if (SlabSupport.isTopSlab(level, neighbourPos)) {
            BlockState below = level.getBlockState(neighbourPos.below());
            return below.is(BlockTags.WALLS) && SlabSupport.isUnderTopSlab(below);
        }
        return false;
    }

    private static EnumProperty<WallSide> wallProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> WallBlock.NORTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        };
    }
}
