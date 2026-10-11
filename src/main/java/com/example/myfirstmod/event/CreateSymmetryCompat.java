package com.example.myfirstmod.event;

import com.example.myfirstmod.ModBlocks;
import com.example.myfirstmod.block.entity.MixedSlabBlockEntity;
import com.example.myfirstmod.util.MixedSlabBreakHandler;
import com.example.myfirstmod.util.FluidHolder;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 机械动力「对称之杖」的兼容：让组合半砖被镜像过去时也带着两块材质。
 *
 * <p>组合半砖的两块材质存在方块实体里，而对称之杖走的是 {@code BlockState#mirror} +
 * {@code Level#setBlockAndUpdate}：方块状态（MODE/TYPE）能镜像过去，但新格子的方块实体是新建的
 * 默认值（石头 + 石头），所以镜像出来的那一块看起来就是石头材质的组合半砖。
 *
 * <p>做法：监听 {@link BlockEvent.EntityPlaceEvent} 算出该搬的两块材质，然后等这一 tick 的
 * {@link LevelTickEvent.Post} 再写进去。之所以不直接写：机械动力自己也用 {@code LOWEST} 优先级
 * 监听同一个事件，同优先级下两个监听器的先后由注册顺序决定，等 tick 结束再写就跟顺序无关了。
 *
 * <p>这里用反射调用机械动力的 {@code SymmetryWandItem#getMirror} 与
 * {@code SymmetryMirror#process(BlockPos, BlockState)}（后者就是它自己算镜像目标格用的公开方法），
 * 为的是不给本模组增加机械动力的编译依赖；机械动力不在、或哪天方法改名，这里会静默关闭，
 * 只影响这一条兼容，不影响其它功能。
 */
@EventBusSubscriber(modid = "btsdhz_original")
public final class CreateSymmetryCompat {

    private static final String CREATE_MOD_ID = "create";
    private static final String WAND_ITEM_ID = "create:wand_of_symmetry";
    private static final String WAND_CLASS =
            "com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem";
    private static final String MIRROR_CLASS =
            "com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror";

    /** 反射一次就缓存；失败（机械动力改接口）后不再重试。 */
    private static boolean reflectionFailed;
    private static Method getMirrorMethod;
    private static Method processMethod;

    /** 待写入的材质搬运（本 tick 结束、镜像目标格确实落位后再写）。 */
    private static final ArrayDeque<Copy> PENDING = new ArrayDeque<>();

    private record Copy(Level level, BlockPos pos, Block first, Block second) {
    }

    /** 待补的掉落：机械动力的镜像拆除是先 setBlock(AIR) 再问掉落，那会儿方块实体已经没了。 */
    private static final ArrayDeque<Drop> PENDING_DROPS = new ArrayDeque<>();

    private record Drop(Level level, BlockPos pos, List<ItemStack> items) {
    }

    /** 半拆后要在镜像格重建的半砖（等 tick 结束、确认那边确实被拆掉再放回去）。 */
    private static final ArrayDeque<Rebuild> PENDING_REBUILDS = new ArrayDeque<>();

    private record Rebuild(Level level, BlockPos pos, BlockState state, ItemStack item, Block hitBlock) {
    }

    // 本次破坏的镜像上下文（服务端单线程，一次破坏内有效）
    private static Level breakLevel;
    private static BlockPos breakSourcePos;
    private static Object breakMirror;
    private static boolean breakIsHalf;
    private static List<BlockPos> breakTargets;
    private static List<List<ItemStack>> breakTargetItems;

    private CreateSymmetryCompat() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (reflectionFailed || !ModList.get().isLoaded(CREATE_MOD_ID)) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        BlockPos sourcePos = event.getPos();
        BlockState sourceState = event.getPlacedBlock();
        if (sourceState.getBlock() != ModBlocks.MERGED_SLAB.get()) {
            return;   // 只有“刚合并出来的组合半砖”需要搬材质
        }
        if (!(level.getBlockEntity(sourcePos) instanceof MixedSlabBlockEntity source)
                || isUnset(source)) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> targets = invokeProcess(mirror, sourcePos, sourceState);
        if (targets == null) {
            return;
        }

        Direction sourceSide = firstHalfSide(sourceState);
        for (Map.Entry<BlockPos, BlockState> entry : targets.entrySet()) {
            BlockPos targetPos = entry.getKey();
            if (targetPos.equals(sourcePos)) {
                continue;   // 对称面正好穿过自己，机械动力也会跳过
            }
            // A 永远代表“下/北/西”那半块（见 MixedSlabModel）。镜像后这半块的几何位置可能被换到
            // 另一侧，那就得把两块材质对调，否则上下（南北/东西）会反。
            boolean swap = mirroredFirstHalfSide(mirror, sourcePos, targetPos, sourceSide)
                    != firstHalfSide(entry.getValue());
            PENDING.add(new Copy(level, targetPos.immutable(),
                    swap ? source.getSecondSlab() : source.getFirstSlab(),
                    swap ? source.getFirstSlab() : source.getSecondSlab()));
        }
    }

    /** 等这一 tick 结束时再写：这时机械动力的镜像方块无论先后都已经落位。 */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!PENDING.isEmpty()) {
            applyPending(event.getLevel());
        }
        if (!PENDING_DROPS.isEmpty()) {
            applyPendingDrops(event.getLevel());
        }
        if (!PENDING_REBUILDS.isEmpty()) {
            applyPendingRebuilds(event.getLevel());
        }
    }

    /** 半拆的镜像格：确认那边确实被拆掉了，就把“保留的那一半”放回去，并只掉落被拆的那半块。 */
    private static void applyPendingRebuilds(Level tickedLevel) {
        for (Iterator<Rebuild> it = PENDING_REBUILDS.iterator(); it.hasNext(); ) {
            Rebuild rebuild = it.next();
            if (rebuild.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!rebuild.level().getBlockState(rebuild.pos()).isAir()) {
                continue;   // 那边没被拆掉（没联动上），不能动它
            }
            rebuild.level().setBlock(rebuild.pos(), rebuild.state(), 3);
            rebuild.level().playSound(null, rebuild.pos(),
                    rebuild.hitBlock().defaultBlockState().getSoundType().getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!rebuild.item().isEmpty()) {
                Block.popResource(rebuild.level(), rebuild.pos(), rebuild.item());
            }
        }
    }

    private static void applyPending(Level tickedLevel) {
        for (Iterator<Copy> it = PENDING.iterator(); it.hasNext(); ) {
            Copy copy = it.next();
            if (copy.level() != tickedLevel) {
                continue;
            }
            it.remove();
            Level level = copy.level();
            BlockPos pos = copy.pos();
            if (level.getBlockState(pos).getBlock() != ModBlocks.MERGED_SLAB.get()) {
                continue;   // 目标格没被镜像成组合半砖（被挡住/不满足放置条件/已被拆掉）
            }
            if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity target) || !isUnset(target)) {
                continue;   // 已经有材质的不要覆盖
            }
            target.setBlocks(copy.first(), copy.second());
        }
    }

    // ===== 破坏侧的镜像处理 =====

    /**
     * 破坏前调用（此刻源格与镜像格的方块都还在）：记下这次破坏会波及的镜像格。
     *
     * <p>机械动力的镜像拆除是在 {@code BreakEvent} 里做的，而且它“先把镜像格设成空气、
     * 再调 {@code Block.dropResources} 问掉落”——问的时候方块实体已经被清掉，组合半砖在镜像
     * 那边会白掉两块材质；半拆（潜行只拆一半）时它还会把镜像那格整个拆掉。所以在方块还在的时候
     * 先把镜像格和它们的材质记下来，本 tick 结束再按需要补掉落 / 把“少掉的那一半”补回原样。
     */
    public static void beginBreak(Level level, Player player, BlockPos pos, BlockState state, boolean halfBreak) {
        clearBreakContext();
        if (reflectionFailed || !ModList.get().isLoaded(CREATE_MOD_ID) || level.isClientSide()) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> mirrored = invokeProcess(mirror, pos, state);
        if (mirrored == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        boolean creative = player.isCreative();
        List<BlockPos> targets = new ArrayList<>(mirrored.size());
        List<List<ItemStack>> items = new ArrayList<>(mirrored.size());
        for (BlockPos targetPos : mirrored.keySet()) {
            if (targetPos.equals(pos)) {
                continue;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (!isMirrorCounterpart(targetState, state)) {
                continue;   // 那边不是对应的堆叠半砖，交给机械动力按原样处理
            }
            targets.add(targetPos.immutable());
            items.add(creative ? List.of() : collectSlabItems(level, targetPos, tool));
        }
        if (targets.isEmpty()) {
            return;
        }
        breakLevel = level;
        breakSourcePos = pos.immutable();
        breakMirror = mirror;
        breakIsHalf = halfBreak;
        breakTargets = targets;
        breakTargetItems = items;
        if (!halfBreak) {
            // 整块拆除：镜像那边机械动力会把方块去掉但掉不出东西，这里替它补掉落
            for (int i = 0; i < targets.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(level, targets.get(i), items.get(i)));
                }
            }
        }
    }

    /**
     * 半拆完成后调用：让镜像格也只少掉对应的那一半（保留另一半）。
     *
     * <p>做法是等这一 tick 结束、确认镜像格确实被机械动力拆掉了，再把“保留的那一半”放回去，
     * 并只掉落被拆掉的那半块。
     */
    public static void finishHalfBreak(Level level, Player player, BlockPos pos,
                                       BlockState keepState, Block hitBlock) {
        if (breakTargets == null || breakLevel != level || !breakIsHalf
                || breakSourcePos == null || !breakSourcePos.equals(pos)) {
            clearBreakContext();
            return;
        }
        List<BlockPos> targets = breakTargets;
        Object mirror = breakMirror;
        clearBreakContext();
        if (mirror == null) {
            return;
        }
        // 用原版发射器的六向 facing 当探针，问机械动力“保留的这半块被镜像到了哪一侧”
        Direction keepSide = occupiedSide(keepState);
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, keepSide);
        Map<BlockPos, BlockState> probeMirror = invokeProcess(mirror, pos, probe);
        if (probeMirror == null) {
            return;
        }
        Block keepBlock = keepState.getBlock();
        ItemStack removed = player.isCreative() || !MixedSlabBreakHandler.canHarvestLoose(player, hitBlock)
                ? ItemStack.EMPTY
                : new ItemStack(hitBlock);
        for (BlockPos targetPos : targets) {
            BlockState mapped = probeMirror.get(targetPos);
            Direction side = mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                    ? mapped.getValue(BlockStateProperties.FACING)
                    : keepSide;
            PENDING_REBUILDS.add(new Rebuild(level, targetPos, halfStateOf(keepBlock, side), removed, hitBlock));
        }
    }

    /** 本次破坏结束（没接管成、事件被取消等）：清理上下文；半拆没走完就退回整块掉落，别把材料吞了。 */
    public static void endBreak() {
        if (breakTargets != null && breakIsHalf && breakTargetItems != null) {
            for (int i = 0; i < breakTargets.size(); i++) {
                if (!breakTargetItems.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(breakLevel, breakTargets.get(i), breakTargetItems.get(i)));
                }
            }
        }
        clearBreakContext();
    }

    private static void clearBreakContext() {
        breakLevel = null;
        breakSourcePos = null;
        breakMirror = null;
        breakIsHalf = false;
        breakTargets = null;
        breakTargetItems = null;
    }

    /** 镜像格是否就是“对应的堆叠半砖”：组合半砖对组合半砖，原版堆叠半砖对同方块的 DOUBLE。 */
    private static boolean isMirrorCounterpart(BlockState targetState, BlockState sourceState) {
        if (sourceState.getBlock() == ModBlocks.MERGED_SLAB.get()) {
            return targetState.getBlock() == ModBlocks.MERGED_SLAB.get();
        }
        return targetState.getBlock() == sourceState.getBlock()
                && targetState.hasProperty(SlabBlock.TYPE)
                && targetState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    /** 取某个组合半砖格两块材质的掉落物（按宽松工具规则）。 */
    private static List<ItemStack> collectSlabItems(Level level, BlockPos pos, ItemStack tool) {
        if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed)) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(2);
        for (Block slab : List.of(mixed.getFirstSlab(), mixed.getSecondSlab())) {
            if (MixedSlabBreakHandler.canHarvestLoose(tool, slab)) {
                items.add(new ItemStack(slab));
            }
        }
        return items;
    }

    private static void applyPendingDrops(Level tickedLevel) {
        for (Iterator<Drop> it = PENDING_DROPS.iterator(); it.hasNext(); ) {
            Drop drop = it.next();
            if (drop.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!drop.level().getBlockState(drop.pos()).isAir()) {
                continue;   // 镜像那边没被拆掉（没联动上），不能凭空给掉落
            }
            for (ItemStack item : drop.items()) {
                Block.popResource(drop.level(), drop.pos(), item);
            }
        }
    }

    // ===== 组合半砖的“第一块（A）”在哪一侧 =====

    /** A 所在的半边方向：平放=下半、竖放南北=北半、竖放东西=西半（与渲染模型一致）。 */
    private static Direction firstHalfSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        return switch (mode) {
            case VERTICAL_NS -> Direction.NORTH;
            case VERTICAL_EW -> Direction.WEST;
            default -> Direction.DOWN;
        };
    }

    /**
     * 用探针方块测出“源格的 A 侧”被这次镜像搬到了哪个方向。
     *
     * <p>机械动力的镜像作用在方块状态上，具体用哪种翻转（东西/南北/上下/对角）它自己知道，
     * 与其去猜，不如拿一个带六向 facing 的原版方块再问它一次：同一个镜面、同一个源格、
     * 目标格的 key 是一致的，把 facing 设成 A 侧方向，读回来的就是映射后的方向。
     * 探针取原版发射器（facing 六向，且原版实现了 rotate/mirror）。
     */
    private static Direction mirroredFirstHalfSide(Object mirror, BlockPos sourcePos,
                                                   BlockPos targetPos, Direction sourceSide) {
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, sourceSide);
        Map<BlockPos, BlockState> probeTargets = invokeProcess(mirror, sourcePos, probe);
        if (probeTargets == null) {
            return sourceSide;
        }
        BlockState mapped = probeTargets.get(targetPos);
        return mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                ? mapped.getValue(BlockStateProperties.FACING)
                : sourceSide;
    }

    private static boolean isUnset(MixedSlabBlockEntity entity) {
        return entity.getFirstSlab() == Blocks.STONE_SLAB && entity.getSecondSlab() == Blocks.STONE_SLAB;
    }

    // ===== 单半砖状态 ↔ “占住哪一侧” =====

    /** 单半砖（普通半砖状态）占住的那一侧：平放=下/上，竖放南北=北/南，竖放东西=西/东。 */
    private static Direction occupiedSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        boolean first = !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.TOP;
        return switch (mode) {
            case VERTICAL_NS -> first ? Direction.NORTH : Direction.SOUTH;
            case VERTICAL_EW -> first ? Direction.WEST : Direction.EAST;
            default -> first ? Direction.DOWN : Direction.UP;
        };
    }

    /** 反向：让某个半砖方块占住指定的一侧（含液体状态清空，与合并/半拆的规则一致）。 */
    private static BlockState halfStateOf(Block block, Direction side) {
        VerticalSlabMode mode = switch (side.getAxis()) {
            case Y -> VerticalSlabMode.SLAB;
            case Z -> VerticalSlabMode.VERTICAL_NS;
            default -> VerticalSlabMode.VERTICAL_EW;
        };
        boolean first = side == Direction.DOWN || side == Direction.NORTH || side == Direction.WEST;
        BlockState base = block.defaultBlockState();
        if (!base.hasProperty(ModBlockStateProperties.MODE) || !base.hasProperty(SlabBlock.TYPE)) {
            return base;   // 兜底：没有本模组半砖属性的方块就不硬套（正常不会走到）
        }
        BlockState state = base
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, first ? SlabType.BOTTOM : SlabType.TOP);
        state = FluidHolder.with(state, FluidType.NONE);
        return state;
    }

    // ===== 机械动力那边的取用（全程反射，机械动力不在时整条逻辑不生效） =====

    @Nullable
    private static ItemStack findWand(Player player) {
        int size = player.getInventory().getSelectionSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()
                    && WAND_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return stack;
            }
        }
        return null;
    }

    private static boolean prepareReflection() {
        if (getMirrorMethod != null && processMethod != null) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Class<?> wandClass = Class.forName(WAND_CLASS);
            Class<?> mirrorClass = Class.forName(MIRROR_CLASS);
            getMirrorMethod = wandClass.getMethod("getMirror", ItemStack.class);
            processMethod = mirrorClass.getMethod("process", BlockPos.class, BlockState.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return false;
        }
    }

    @Nullable
    private static Object invokeGetMirror(ItemStack wand) {
        try {
            return getMirrorMethod.invoke(null, wand);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, BlockState> invokeProcess(Object mirror, BlockPos pos, BlockState state) {
        try {
            return (Map<BlockPos, BlockState>) processMethod.invoke(mirror, pos, state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }
}
