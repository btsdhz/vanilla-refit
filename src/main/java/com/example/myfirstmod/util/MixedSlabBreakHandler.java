package com.example.myfirstmod.util;

import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * 整块拆除混合半砖：移除方块并释放两块半砖的掉落物（创造模式不掉落）。
     *
     * 混合半砖没有原版掉落表，原版破坏链靠 getDrops 只会返回空，
     * 因此在这里直接弹出两块半砖。返回 true 表示已接管，调用方应结束原逻辑。
     */
    public static boolean dropWholeMixed(Level level, BlockPos pos, BlockState state, Player player) {
        boolean isMixed = state.getBlock() instanceof MixedSlabBlock;
        if (!isMixed) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        Block a = Blocks.STONE_SLAB;
        Block b = Blocks.STONE_SLAB;
        if (be instanceof MixedSlabBlockEntity mixed) {
            a = mixed.getFirstSlab();
            b = mixed.getSecondSlab();
        }
        // 先读回材质再移除方块（方块实体随之被清理），保证掉落物正确
        level.removeBlock(pos, false);
        if (!player.getAbilities().instabuild) {
            // 非潜行的整块拆除：不做完整工具适配，但手上没拿工具时“需要工具”的半砖不给掉落
            if (canHarvestLoose(player, a)) {
                Block.popResource(level, pos, new ItemStack(a));
            }
            if (canHarvestLoose(player, b)) {
                Block.popResource(level, pos, new ItemStack(b));
            }
        }
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
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
        BlockState keepState = FluidHolder.with(block.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, keepTop ? SlabType.TOP : SlabType.BOTTOM), FluidType.NONE);
        return applyBreak(level, pos, keepState, block, player);
    }

    private static boolean applyBreak(Level level, BlockPos pos, BlockState keepState, Block hitBlock,
                                      Player player) {
        // 对称之杖：本格只拆一半，镜像那边也应该只少掉对应的那一半
        com.example.myfirstmod.event.CreateSymmetryCompat.finishHalfBreak(level, player, pos, keepState, hitBlock);
        // 一次性把整格替换为“保留的那一半”，方块实体会随之被移除，避免两次区块更新
        level.setBlock(pos, keepState, 3);
        // 播放被拆那块半砖的破坏音效（mixin 完全接管了原版流程，音效需手动补回）
        level.playSound(null, pos, hitBlock.defaultBlockState().getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        // 潜行“精准拆除”才做工具适配：用错工具照样能挖掉，但不产掉落物
        if (!player.getAbilities().instabuild && canHarvest(player, hitBlock)) {
            Block.popResource(level, pos, new ItemStack(hitBlock));
        }
        return true;
    }

    /**
     * 该半砖材质在当前工具下是否会掉落（原版语义：需要正确工具才能收获）。
     *
     * 例如石头半砖必须用镐，木半砖徒手也掉；用错工具时方块照样能挖掉，只是不产掉落物。
     * 只在潜行的“精准拆除”里生效，非潜行的整块拆除不做工具限制。
     */
    private static boolean canHarvest(Player player, Block slabBlock) {
        BlockState state = slabBlock.defaultBlockState();
        return !state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state);
    }

    /**
     * 整块拆除（非潜行）使用的宽松判定：不要求工具完全匹配，
     * 但手上必须拿着工具——徒手、或者只拿着泥土方块之类的非工具物品，
     * 挖“需要正确工具”的半砖（例如石头半砖）都不给掉落。
     *
     * 潜行的“精准拆除”用的是严格判定 {@link #canHarvest(Player, Block)}。
     */
    public static boolean canHarvestLoose(Player player, Block slabBlock) {
        return canHarvestLoose(player.getMainHandItem(), slabBlock);
    }

    /** 同上，但直接给工具物品；没有工具（null/空手）时“需要工具”的半砖不给掉落。 */
    public static boolean canHarvestLoose(ItemStack tool, Block slabBlock) {
        BlockState state = slabBlock.defaultBlockState();
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        // 只认带 TOOL 组件的物品（镐/斧/锹/锄/剑等，含模组工具），非工具物品不算
        return tool != null && !tool.isEmpty() && tool.has(DataComponents.TOOL);
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
            return FluidHolder.with(base.setValue(ModBlockStateProperties.MODE, mode)
                    .setValue(SlabBlock.TYPE, isFirstKept ? SlabType.BOTTOM : SlabType.TOP), fluid);
        }
        return base;
    }

}
