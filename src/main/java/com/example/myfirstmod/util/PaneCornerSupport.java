package com.example.myfirstmod.util;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * 玻璃板“部件化”形状的公共代码。
 *
 * 一块玻璃板由 12 个部件拼成（都靠方块状态控制，默认都不开，外观与原版一致）：
 *  - 4 个水平面片：方块四个角上 1/4 面大小、位于中间高度 8 像素处的水平玻璃，
 *    对应 btsdhz_ne / btsdhz_se / btsdhz_nw / btsdhz_sw；
 *  - 8 个竖直面片：原版的东西南北四个方向各自分成上下两半，每半是 8 高 × 8 长 × 2 厚，
 *    下半用原版属性 north / east / south / west（名字不变），
 *    上半用本模组的 btsdhz_north_up / east_up / south_up / west_up。
 * 所有部件的厚度都是 2 像素，与原版玻璃板自身板厚一致，模型与碰撞箱因此完全重合。
 *
 * 12 个属性全为 false 时保持原版形状与外观（中间那根立柱），所以老存档、没用调试棒改过的
 * 玻璃板不会发生任何变化。
 */
public final class PaneCornerSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 原版方块都在这个类的静态初始化里 new 出来，模组方块来自模组自己的注册类。
     * 用 {@code Blocks.class.getName()} 而不是手抄字面量，省得各版本抄错。
     */
    private static final String VANILLA_BLOCKS_CLASS = Blocks.class.getName();

    /** 水平面片的上下表面（7~9 像素，中心正好在第 8 像素）。 */
    public static final double CORNER_MIN_Y = 7.0;
    public static final double CORNER_MAX_Y = 9.0;

    /** 东北角水平面片（+X / -Z）。 */
    private static final VoxelShape NORTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 0.0, 16.0, CORNER_MAX_Y, 8.0);
    /** 东南角水平面片（+X / +Z）。 */
    private static final VoxelShape SOUTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 8.0, 16.0, CORNER_MAX_Y, 16.0);
    /** 西北角水平面片（-X / -Z）。 */
    private static final VoxelShape NORTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 0.0, 8.0, CORNER_MAX_Y, 8.0);
    /** 西南角水平面片（-X / +Z）。 */
    private static final VoxelShape SOUTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 8.0, 8.0, CORNER_MAX_Y, 16.0);

    // 竖直面片：每个方向的下半（Y 0~8）与上半（Y 8~16），厚度 2 像素、长度 8 像素（伸到方块中线）。
    private static final VoxelShape NORTH_LOWER = Block.box(7.0, 0.0, 0.0, 9.0, 8.0, 8.0);
    private static final VoxelShape NORTH_UPPER = Block.box(7.0, 8.0, 0.0, 9.0, 16.0, 8.0);
    private static final VoxelShape EAST_LOWER = Block.box(8.0, 0.0, 7.0, 16.0, 8.0, 9.0);
    private static final VoxelShape EAST_UPPER = Block.box(8.0, 8.0, 7.0, 16.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_LOWER = Block.box(7.0, 0.0, 8.0, 9.0, 8.0, 16.0);
    private static final VoxelShape SOUTH_UPPER = Block.box(7.0, 8.0, 8.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_LOWER = Block.box(0.0, 0.0, 7.0, 8.0, 8.0, 9.0);
    private static final VoxelShape WEST_UPPER = Block.box(0.0, 8.0, 7.0, 8.0, 16.0, 9.0);

    /** 12 个部件的属性，顺序与 {@link #PIECE_BY_INDEX} 一一对应（拼位掩码用）。 */
    @SuppressWarnings("unchecked")
    private static final Property<Boolean>[] PIECE_PROPERTIES = new Property[] {
            CrossCollisionBlock.NORTH, CrossCollisionBlock.EAST, CrossCollisionBlock.SOUTH, CrossCollisionBlock.WEST,
            ModBlockStateProperties.PANE_NORTH_UP, ModBlockStateProperties.PANE_EAST_UP,
            ModBlockStateProperties.PANE_SOUTH_UP, ModBlockStateProperties.PANE_WEST_UP,
            ModBlockStateProperties.PANE_NORTH_EAST, ModBlockStateProperties.PANE_SOUTH_EAST,
            ModBlockStateProperties.PANE_SOUTH_WEST, ModBlockStateProperties.PANE_NORTH_WEST};

    /** 12 个部件的形状，顺序同 {@link #PIECE_PROPERTIES}。 */
    private static final VoxelShape[] PIECE_BY_INDEX = {
            NORTH_LOWER, EAST_LOWER, SOUTH_LOWER, WEST_LOWER,
            NORTH_UPPER, EAST_UPPER, SOUTH_UPPER, WEST_UPPER,
            NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST};

    /** 四个角面片在 12 位掩码里的位置（下标 8~11）。 */
    private static final int CORNER_MASK = 0xF00;

    /**
     * “状态 → 12 位掩码”缓存：掩码要读 12 个属性才算得出来，而形状查询（碰撞、准星）与渲染
     * 都会对同一个状态反复要它——按状态记一次就够了，见 {@link StateIntCache}。
     */
    private static final StateIntCache PIECE_MASKS = new StateIntCache(PaneCornerSupport::computePieceMask, 1024);

    /**
     * 12 个部件合并出来的形状只跟“启用了哪些部件”有关（与材质、坐标都无关），
     * 所以按 12 位掩码缓存，最多 161 份；空形状表示“保持原版形状”。
     */
    private static final Map<Integer, VoxelShape> PIECE_SHAPES = new ConcurrentHashMap<>();

    /** 参与部件化的方块（原版命名空间的玻璃板 / 染色玻璃板 / 铁栏杆），首次使用时算一次。 */
    private static volatile Set<Block> supportedPaneBlocks;

    private PaneCornerSupport() {
    }

    /**
     * 该方块状态是不是“参与部件化”的方块：原版命名空间的玻璃板、染色玻璃板与铁栏杆。
     *
     * 铁栏杆和玻璃板同属 IronBarsBlock、柱与横杆的尺寸也完全一致，所以共用同一套 12 个部件、
     * 形状与连接规则；区别只在“什么都没连”时用铁栏杆自己的原版外观（post_ends + post）与贴图。
     * 其它模组的玻璃板贴图命名不一定遵循原版规则，完全不参与（见 {@link #shouldInjectPaneProperties()}）。
     */
    public static boolean isSupportedPane(BlockState state) {
        return state.getBlock() instanceof IronBarsBlock && supportedPaneBlocks().contains(state.getBlock())
                // 兜底：只有真的带部件属性才算“支持的玻璃板”。属性注入是类级 + 命名空间两道判定的，
                // 万一哪天两边口径对不上（或像调试用的关注入开关那样把属性拿掉），这里会退回原版形状，
                // 而不是在 pieceMask 里 getValue 一个不存在的属性直接崩在方块注册阶段。
                && state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST);
    }

    /**
     * 本模组的 8 个附加部件属性要不要注入到“正在被构造”的这块玻璃板 / 铁栏杆上。
     *
     * <p>属性只能在方块构造期（{@code createBlockStateDefinition}）注册，而那个时刻方块还没进注册表、
     * 拿不到命名空间，所以只能看“是谁在造它”：原版的玻璃板 / 铁栏杆全部在 {@link Blocks} 的静态初始化里
     * new 出来，第三方模组的方块则由模组自己的注册类构造。
     *
     * <p>为什么只给原版注入，有两条独立的理由：
     * <ol>
     *   <li>代价不划算：多这 8 个布尔属性，每个第三方玻璃板方块的状态数会从 32 变成 8192（×256）——
     *       整合包里几个玻璃模组就是几十到几百 MB，而本模组并不给它们做外观与碰撞（判定入口
     *       {@link #isSupportedPane} 只认原版命名空间），等于纯多占内存、没有任何效果。</li>
     *   <li>适配成本高：玻璃板要拼的是 12 个部件（8 个竖直上下半 + 4 个角面片）加那根“棍”，
     *       比台阶/楼梯的“一个模板换图”复杂得多，回归面也大。贴图本身并不需要靠名字猜——
     *       台阶段那套 {@code ModelSprites} 就是从方块自己的模型里读四边形、按几何朝向认图，
 *       玻璃板的大面与断面同样能从它自己的模型里读出来（原版玻璃板模型里两张都有）；
 *       所以这条路**技术上可行**，只是目前没有做。</li>
     * </ol>
     * 结论：第三方玻璃板 / 铁栏杆保持 100% 原版——不注入属性、不改外观、不改碰撞箱、也不参与连接规则。
     */
    public static boolean shouldInjectPaneProperties() {
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (VANILLA_BLOCKS_CLASS.equals(frame.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 方块注册完之后的自检（由 {@code FMLLoadCompleteEvent} 调用）：
     * 参与本模组部件化的玻璃板 / 铁栏杆必须带上那 8 个附加属性，其它玻璃板 / 铁栏杆必须一个都没带。
     * 任一边对不上都说明注入范围出错了（功能失效或多占内存），把方块名打进日志便于定位。
     */
    public static void logSummary() {
        int vanillaPanes = 0;
        int foreignPanes = 0;
        int foreignStates = 0;
        List<ResourceLocation> missing = new ArrayList<>();
        List<String> leaked = new ArrayList<>();
        Set<Block> supported = supportedPaneBlocks();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof IronBarsBlock)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            int states = block.getStateDefinition().getPossibleStates().size();
            boolean hasParts = block.defaultBlockState().hasProperty(ModBlockStateProperties.PANE_NORTH_EAST);
            if (supported.contains(block)) {
                vanillaPanes++;
                if (!hasParts) {
                    missing.add(id);
                }
            } else {
                foreignPanes++;
                foreignStates += states;
                if (hasParts) {
                    leaked.add(id + "=" + states + " 个状态");
                }
            }
        }
        if (missing.isEmpty() && leaked.isEmpty()) {
            LOGGER.info("[原版精修/状态] 玻璃板部件属性注入范围正确：原版命名空间 {} 个玻璃板/铁栏杆带 8 个附加属性，"
                            + "其它命名空间 {} 个玻璃板/铁栏杆保持原版状态数（合计 {} 个状态，不翻 256 倍）",
                    vanillaPanes, foreignPanes, foreignStates);
            return;
        }
        if (!missing.isEmpty()) {
            LOGGER.warn("[原版精修/状态] 这些玻璃板/铁栏杆本该有部件属性却没有，功能会失效：{}", missing);
        }
        if (!leaked.isEmpty()) {
            LOGGER.warn("[原版精修/状态] 这些第三方玻璃板/铁栏杆被注入了部件属性（状态数 ×256、且本模组不给它们做外观"
                    + "与碰撞）：{}", leaked);
        }
    }

    /**
     * 该方块算不算「本模组做部件化」的栏杆类方块。
     *
     * <p>判定是**按类 + 原版命名空间 + 名字能推出贴图规则**，不写死具体是哪几块：
     * <ul>
     *   <li>{@code *_pane}：玻璃板 / 染色玻璃板——大面用去掉 {@code _pane} 的那张玻璃贴图，
     *       断面用它的 {@code _pane_top}；</li>
     *   <li>{@code *_bars}：铁栏杆，以及 1.21.9 起新增的铜栏杆系列（未氧化 + 3 个氧化 + 4 个涂蜡，
     *       共 8 块）——它们与原版 iron_bars 同构，大面与断面都用方块自己那张贴图，涂蜡的那些
     *       沿用未涂蜡氧化态的贴图（原版 blockstate 就是这么指的，要先去掉 {@code waxed_} 前缀）。</li>
     * </ul>
     * 其它模组的方块一律不参与（贴图命名不一定遵循原版规则，见 {@link #shouldInjectPaneProperties()}）。
     */
    public static boolean isSupportedPaneBlock(Block block) {
        if (!(block instanceof IronBarsBlock)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (!id.getNamespace().equals("minecraft")) {
            return false;
        }
        String name = id.getPath();
        return name.endsWith("_pane") || name.endsWith("_bars");
    }

    /** 原版命名空间的玻璃板、染色玻璃板与栏杆类（铁栏杆 / 铜栏杆）；口径与数据生成、运行时模型一致。 */
    private static Set<Block> supportedPaneBlocks() {
        Set<Block> cached = supportedPaneBlocks;
        if (cached != null) {
            return cached;
        }
        Set<Block> built = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isSupportedPaneBlock(block)) {
                built.add(block);
            }
        }
        supportedPaneBlocks = Set.copyOf(built);
        return supportedPaneBlocks;
    }

    /** 该状态是否启用了任意一个角上的水平面片（栅栏式的上下面剔除规则要用）。 */
    public static boolean hasCorner(BlockState state) {
        return isSupportedPane(state) && (PIECE_MASKS.get(state) & CORNER_MASK) != 0;
    }

    /**
     * 玻璃板最终的拾取形状 / 碰撞形状（两者规则相同，只是传入的原版形状不同）。
     *
     * 12 个部件属性全为 false 时保持原版形状（中间那根立柱 + 已连接方向的横杆）；
     * 有一个为 true 时模型里就不再渲染立柱，形状改为由 12 个部件拼出来，和模型保持一致。
     *
     * @param currentShape 当前返回的形状（可能已被台阶舒适框叠加过与位置有关的形状）
     * @param vanillaShape 该状态在原版形状表里的形状，用来把叠加进来的那部分分离出来
     */
    public static VoxelShape adjustShape(VoxelShape currentShape, VoxelShape vanillaShape, BlockState state) {
        if (!isSupportedPane(state)) {
            // 栅栏、铁栏杆等没有这套属性，直接保持原样
            return currentShape;
        }
        int mask = pieceMask(state);
        if (mask == 0) {
            return currentShape;
        }
        VoxelShape pieces = PIECE_SHAPES.computeIfAbsent(mask, PaneCornerSupport::buildPieceShape);
        if (currentShape != vanillaShape) {
            // 邻格方块伸进本格的那部分（台阶舒适框）也要保留，否则准星会跳过它
            pieces = Shapes.or(pieces, Shapes.join(currentShape, vanillaShape, BooleanOp.ONLY_FIRST));
        }
        return pieces;
    }

    /** 该状态启用了哪些部件（12 位掩码）；一个都没启用时返回 0，表示保持原版形状。 */
    private static int pieceMask(BlockState state) {
        return PIECE_MASKS.get(state);
    }

    /** 真正读属性算掩码的那一遍（每个状态只会走到这里一次，除非缓存槽被顶掉）。 */
    private static int computePieceMask(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.PANE_NORTH_WEST)) {
            return 0;   // 第三方玻璃板：没有这套属性，按“保持原版形状”处理
        }
        int bits = 0;
        for (int i = 0; i < PIECE_PROPERTIES.length; i++) {
            if (state.getValue(PIECE_PROPERTIES[i])) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    /** 把掩码里启用的部件逐个并起来。 */
    private static VoxelShape buildPieceShape(int mask) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < PIECE_BY_INDEX.length; i++) {
            if ((mask & (1 << i)) != 0) {
                shape = Shapes.or(shape, PIECE_BY_INDEX[i]);
            }
        }
        return shape;
    }
}
