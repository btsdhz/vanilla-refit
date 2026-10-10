package com.example.myfirstmod.client;

import com.example.myfirstmod.client.model.PaneBakedModel;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.PaneCornerSupport;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.slf4j.Logger;

/**
 * 玻璃板 / 铁栏杆的“运行时拼装”接线：blockstate 只留一个变体，渲染哪些部件由状态决定。
 *
 * <p>本模组的 12 个部件属性让每块玻璃板有 8,192 个状态；原版做法是给每个状态各烘焙一份模型，
 * 按状态展开的模型数据是内存大头。这里：
 * <ol>
 *   <li>数据生成时 blockstate 只写一个变体（见 {@code ModBlockStateProvider#generatePaneBlockStates}）；</li>
 *   <li>{@link #onRegisterAdditional} 把 8 个部件模型与那根“棍”用到的原版模型登记为要烘焙的模型；</li>
 *   <li>{@link #onModifyBakingResult} 把每个状态原来的模型换成同一个 {@link PaneBakedModel}，
 *       它按状态里那 12 个部件属性决定输出哪些四边形。</li>
 * </ol>
 *
 * <p>属性、连接规则、碰撞箱、状态数都不变，所以老存档、调试棒、和连接逻辑都不受影响。
 */
public final class PaneModelEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 只用来问模型要四边形，部件模型都是静态的（没有随机变体），固定一个种子即可。 */
    private static final RandomSource RANDOM = RandomSource.create(0L);

    /** 部件模型模板：数据生成会产出 {@code <模板>_<方块名>}，运行时按这个命名去找。 */
    private static final String[] PIECE_TEMPLATES = {
            "pane_side_lower", "pane_side_lower", "pane_side_lower_alt", "pane_side_lower_alt",
            "pane_side_upper", "pane_side_upper", "pane_side_upper_alt", "pane_side_upper_alt",
            "pane_corner", "pane_corner_se", "pane_corner_sw", "pane_corner_nw"};

    /**
     * 每个部件要不要绕 y 轴转 90°：东西两个方向的面片是拿北/南的基准模型转出来的
     * （和原版 {@code glass_pane_side} 用 {@code y: 90} 摆放东边横杆是同一套做法）。
     */
    private static final boolean[] PIECE_ROTATED = {
            false, true, false, true, false, true, false, true, false, false, false, false};

    /** 12 个部件属性，顺序与 {@link #PIECE_TEMPLATES} 一一对应。 */
    private static final List<Property<Boolean>> PIECE_PROPERTIES = List.of(
            CrossCollisionBlock.NORTH, CrossCollisionBlock.EAST, CrossCollisionBlock.SOUTH, CrossCollisionBlock.WEST,
            ModBlockStateProperties.PANE_NORTH_UP, ModBlockStateProperties.PANE_EAST_UP,
            ModBlockStateProperties.PANE_SOUTH_UP, ModBlockStateProperties.PANE_WEST_UP,
            ModBlockStateProperties.PANE_NORTH_EAST, ModBlockStateProperties.PANE_SOUTH_EAST,
            ModBlockStateProperties.PANE_SOUTH_WEST, ModBlockStateProperties.PANE_NORTH_WEST);

    private static final int ROT_NONE = 0;
    private static final int ROT_CW90 = 1;
    private static final int ROT_CCW90 = 2;

    private PaneModelEvents() {
    }

    /** 登记所有需要烘焙的部件模型与“棍”模型（blockstate 已经不再引用它们）。 */
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation id : supportedPaneIds()) {
            String name = id.getPath();
            for (String template : PIECE_TEMPLATES) {
                event.register(ModelResourceLocation.standalone(modLoc("block/" + template + "_" + name)));
            }
            for (StickPart part : stickParts(name)) {
                event.register(ModelResourceLocation.standalone(part.model()));
            }
        }
    }

    /**
     * 把每个玻璃板/铁栏杆状态的模型换成按状态拼装的 {@link PaneBakedModel}。
     *
     * <p>结束后做一次自检并把结论写进日志（INFO）：支持的方块必须**全部**被接管，
     * 少一个就说明部件模型没烘焙出来（例如资源包改了模型命名），那种情况会降级成
     * 按状态生成的模型并把缺的方块以 WARN 列出来。看日志就能确认玻璃板与铁栏杆都在走新逻辑。
     */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Set<ResourceLocation> supported = supportedPaneIds();
        Map<ResourceLocation, PaneBakedModel> built = new HashMap<>();
        Set<ResourceLocation> replaced = new HashSet<>();
        int replacedStates = 0;
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : models.entrySet()) {
            ResourceLocation id = entry.getKey().id();
            if (!supported.contains(id)) {
                continue;
            }
            // 方块 id 与物品 id 同名，模型表里还有一条 variant = "inventory" 的条目（物品栏/手持的 2D 图标）。
            // 那条必须留着原版模型，否则玻璃板与铁栏杆在物品栏里会变成 3D 立柱。
            if (ModelResourceLocation.INVENTORY_VARIANT.equals(entry.getKey().getVariant())) {
                continue;
            }
            PaneBakedModel model = built.get(id);
            if (model == null) {
                model = build(entry.getValue(), models, id);
                if (model == null) {
                    // 部件模型没烘焙出来（例如资源包改了模型命名）：保持原版外观，不要换上半成品
                    LOGGER.warn("[原版精修/模型] 玻璃板 {} 的部件模型不完整，这次不接管它的模型", id);
                    continue;
                }
                built.put(id, model);
            }
            entry.setValue(model);
            replaced.add(id);
            replacedStates++;
        }
        if (replaced.size() == supported.size()) {
            LOGGER.info("[原版精修/模型] 玻璃板/铁栏杆已全部改用运行时拼装：{} 个方块（含 iron_bars）、{} 个状态共用 {} 个模型实例",
                    replaced.size(), replacedStates, built.size());
        } else {
            Set<ResourceLocation> missing = new HashSet<>(supported);
            missing.removeAll(replaced);
            LOGGER.warn("[原版精修/模型] 这些玻璃板/铁栏杆没能改用运行时拼装，仍按状态生成模型：{}", missing);
        }
    }

    /** 拼一个材质的模型：11 个子模型（8 个部件 + 3 个棍部件）各取一次四边形，按部件存好。 */
    @Nullable
    private static PaneBakedModel build(BakedModel body, Map<ModelResourceLocation, BakedModel> models,
                                        ResourceLocation blockId) {
        String name = blockId.getPath();
        List<List<BakedQuad>> pieces = new ArrayList<>(PIECE_TEMPLATES.length * PaneBakedModel.GROUPS);
        for (int i = 0; i < PIECE_TEMPLATES.length; i++) {
            List<List<BakedQuad>> buckets = bucketsOf(models,
                    modLoc("block/" + PIECE_TEMPLATES[i] + "_" + name), PIECE_ROTATED[i] ? ROT_CW90 : ROT_NONE);
            if (buckets == null) {
                return null;
            }
            pieces.addAll(buckets);
        }
        List<List<BakedQuad>> stick = new ArrayList<>(PaneBakedModel.GROUPS);
        for (int group = 0; group < PaneBakedModel.GROUPS; group++) {
            List<BakedQuad> merged = new ArrayList<>();
            for (StickPart part : stickParts(name)) {
                List<List<BakedQuad>> buckets = bucketsOf(models, part.model(), part.rotation());
                if (buckets == null) {
                    return null;
                }
                merged.addAll(buckets.get(group));
            }
            stick.add(List.copyOf(merged));
        }
        return new PaneBakedModel(body, PIECE_PROPERTIES, List.copyOf(pieces), List.copyOf(stick));
    }

    /**
     * 取一个子模型的 7 组四边形（6 个方向 + 不参与剔除那组），按需整体绕方块中心转 90°。
     *
     * <p>转的时候只动位置和法线，UV 跟着顶点一起走——这正是原版变体 {@code y: 90}（uvlock 为 false）
     * 的行为，所以外观和原先按状态生成的模型完全一致。
     */
    @Nullable
    private static List<List<BakedQuad>> bucketsOf(Map<ModelResourceLocation, BakedModel> models,
                                                   ResourceLocation modelId, int rotation) {
        BakedModel model = models.get(ModelResourceLocation.standalone(modelId));
        if (model == null) {
            return null;
        }
        List<List<BakedQuad>> buckets = new ArrayList<>(PaneBakedModel.GROUPS);
        for (Direction direction : Direction.values()) {
            buckets.add(List.copyOf(model.getQuads(null, direction, RANDOM)));
        }
        buckets.add(List.copyOf(model.getQuads(null, null, RANDOM)));
        return rotation == ROT_NONE ? buckets : rotate(buckets, rotation == ROT_CW90);
    }

    /** 把 7 组四边形整体绕方块中心转 90°，并在方向分组之间搬运。 */
    private static List<List<BakedQuad>> rotate(List<List<BakedQuad>> buckets, boolean clockwise) {
        List<List<BakedQuad>> out = new ArrayList<>(PaneBakedModel.GROUPS);
        for (int i = 0; i < PaneBakedModel.GROUPS; i++) {
            out.add(List.of());
        }
        for (Direction direction : Direction.values()) {
            out.set(rotate(direction, clockwise).ordinal(),
                    rotateQuads(buckets.get(direction.ordinal()), clockwise));
        }
        out.set(PaneBakedModel.UNCLASSIFIED_GROUP, rotateQuads(buckets.get(PaneBakedModel.UNCLASSIFIED_GROUP), clockwise));
        return out;
    }

    /** 顺时针（北→东）或逆时针（北→西）转 90°，上下不变。 */
    private static Direction rotate(Direction direction, boolean clockwise) {
        return switch (direction) {
            case NORTH -> clockwise ? Direction.EAST : Direction.WEST;
            case EAST -> clockwise ? Direction.SOUTH : Direction.NORTH;
            case SOUTH -> clockwise ? Direction.WEST : Direction.EAST;
            case WEST -> clockwise ? Direction.NORTH : Direction.SOUTH;
            default -> direction;
        };
    }

    private static List<BakedQuad> rotateQuads(List<BakedQuad> quads, boolean clockwise) {
        if (quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            out.add(rotateQuad(quad, clockwise));
        }
        return out;
    }

    /**
     * 复制一份四边形并整体绕方块中心转 90°。
     *
     * <p>位置 (x, y, z) 按顺时针（北→东）映射成 (1 - z, y, x)，逆时针映射成 (z, y, 1 - x)；
     * 法线跟着转；UV 不动（贴图跟着顶点走，和原版 uvlock=false 的旋转一致）。
     */
    private static BakedQuad rotateQuad(BakedQuad quad, boolean clockwise) {
        int[] vertices = quad.getVertices().clone();
        for (int i = 0; i < 4; i++) {
            int base = i * IQuadTransformer.STRIDE;
            int position = base + IQuadTransformer.POSITION;
            float x = Float.intBitsToFloat(vertices[position]);
            float z = Float.intBitsToFloat(vertices[position + 2]);
            vertices[position] = Float.floatToRawIntBits(clockwise ? 1.0F - z : z);
            vertices[position + 2] = Float.floatToRawIntBits(clockwise ? x : 1.0F - x);

            int normalIndex = base + IQuadTransformer.NORMAL;
            int packed = vertices[normalIndex];
            float nx = (byte) (packed & 0xFF) / 127.0F;
            float ny = (byte) ((packed >> 8) & 0xFF) / 127.0F;
            float nz = (byte) ((packed >> 16) & 0xFF) / 127.0F;
            float rx = clockwise ? -nz : nz;
            float rz = clockwise ? nx : -nx;
            vertices[normalIndex] = (((byte) (rx * 127.0F)) & 0xFF)
                    | ((((byte) (ny * 127.0F)) & 0xFF) << 8)
                    | ((((byte) (rz * 127.0F)) & 0xFF) << 16)
                    | (packed & 0xFF000000);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), rotate(quad.getDirection(), clockwise), quad.getSprite(),
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    /** 参与这套部件化的方块：原版命名空间的玻璃板、染色玻璃板与铁栏杆（与数据生成的口径一致）。 */
    private static Set<ResourceLocation> supportedPaneIds() {
        Set<ResourceLocation> ids = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (PaneCornerSupport.isSupportedPaneBlock(block)) {
                ids.add(BuiltInRegistries.BLOCK.getKey(block));
            }
        }
        return ids;
    }

    /** 12 个部件全为假时那根“棍”由哪些原版模型拼出来（与原版 glass_pane / iron_bars / copper_bars 的用法一致）。 */
    private static List<StickPart> stickParts(String name) {
        if (name.endsWith("_bars")) {
            // 栏杆类（铁栏杆、铜栏杆系列）：原版模型是 <材质>_post_ends + <材质>_post；
            // 涂蜡铜栏杆沿用未涂蜡氧化态的模型，所以先去掉 waxed_ 前缀
            String base = name.replaceFirst("^waxed_", "");
            return List.of(
                    new StickPart(mcLoc("block/" + base + "_post_ends"), ROT_NONE),
                    new StickPart(mcLoc("block/" + base + "_post"), ROT_NONE));
        }
        return List.of(
                new StickPart(mcLoc("block/" + name + "_post"), ROT_NONE),
                new StickPart(mcLoc("block/" + name + "_noside"), ROT_NONE),
                new StickPart(mcLoc("block/" + name + "_noside_alt"), ROT_NONE),
                new StickPart(mcLoc("block/" + name + "_noside_alt"), ROT_CW90),
                new StickPart(mcLoc("block/" + name + "_noside"), ROT_CCW90));
    }

    private static ResourceLocation mcLoc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath("btsdhz_original", path);
    }

    /** “棍”的一个组成部分：模型 + 摆放时的旋转。 */
    private record StickPart(ResourceLocation model, int rotation) {
    }
}
