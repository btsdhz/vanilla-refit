package com.example.myfirstmod.datagen;

import com.example.myfirstmod.ModBlocks;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.PaneCornerSupport;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import com.example.myfirstmod.util.StairConnection;
import com.example.myfirstmod.util.StairShapeModels;
import java.util.List;
import java.util.Set;

public class ModBlockStateProvider extends BlockStateProvider {

    /**
     * 这些材质的台阶在 src/main/resources 下手工建模（多纹理/特殊纹理），
     * 不参与数据生成器的自动生成，避免生成错误的单纹理版本。
     *
     * 说明：
     *  - 平滑石：模组固定使用 smooth_stone 单一纹理，不走通用纹理映射；
     *  - 砂岩 / 红砂岩及它们的切制版：属于多纹理方块（顶/侧/底不同），
     *    通用生成的单纹理模型会把顶/底也渲染成侧面纹理，故改为手工建模，
     *    并提供按面分配的纹理（见 vertical_slab_multi_*.json 及对应 per-material 模型）。
     */
    private static final Set<String> HAND_AUTH_SLABS = Set.of(
            "smooth_stone_slab",
            "sandstone_slab",
            "cut_sandstone_slab",
            "red_sandstone_slab",
            "cut_red_sandstone_slab"
    );

    /**
     * 这些材质的竖楼梯在 src/main/resources 下手工建模（多纹理），
     * 不参与数据生成器的自动生成。
     *
     * 注意：原版不存在 cut_sandstone_stairs / cut_red_sandstone_stairs 方块，
     * 因此此处只列出真实存在的 sandstone_stairs 与 red_sandstone_stairs。
     * 切制砂岩/切制红砂岩只有台阶(slab)没有楼梯(stairs)。
     */
    private static final Set<String> HAND_AUTH_STAIRS = Set.of(
            "sandstone_stairs",
            "red_sandstone_stairs",
            "smooth_sandstone_stairs",
            "smooth_red_sandstone_stairs"
    );

    /**
     * 模板载体：只给这两个方块生成竖形态模型，运行时其它方块（原版和模组方块）都拿它们的
     * 烘焙模型当几何模板，再换成各自贴图（见 client/RetexturedTemplateModel）。
     *
     * <p>这么做之后：竖形态的几何/UV/剔除标记只有这一份（手工调好的），其它方块不用各自
     * 预生成模型——jar 里少了七百多个文件，新增方块也能自动获得竖形态。
     */
    private static final Set<String> TEMPLATE_CARRIERS = Set.of(
            "oak_slab",
            "oak_stairs"
    );

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, "btsdhz_original", existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 混合半砖：一格放两块不同材质半砖。模型是占位（客户端运行时合并两块半砖几何）
        generateMixedSlabBlockStates();

        // 玻璃板：原版 multipart 之外追加四个“角上水平面片”属性对应的部分
        generatePaneBlockStates();

        // 台阶：只生成模板载体（橡木）和本模组注册的方块；其余方块由运行时按模板换图
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof SlabBlock)
                .filter(this::isGeneratedModelSource)
                .forEach(slab -> {
                    String name = BuiltInRegistries.BLOCK.getKey(slab).getPath();
                    generateSlabBlockStates(slab, name);
                });

        // 楼梯：同上
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof StairBlock)
                .filter(this::isGeneratedModelSource)
                .forEach(stair -> {
                    String name = BuiltInRegistries.BLOCK.getKey(stair).getPath();
                    generateStairBlockStates(stair, name);
                });
    }

    /**
     * 哪些方块需要预生成 blockstate/模型：
     *
     * <ul>
     *   <li>模板载体（橡木台阶/楼梯）——运行时其它方块拿它们的模型当几何模板；</li>
     *   <li>本模组自己注册的方块（平滑石楼梯等）——运行时不接管它们，得有自己的模型。</li>
     * </ul>
     *
     * <p>其余原版方块与其它模组方块都走运行时路径（见 client/RetexturedTemplateModel），
     * 所以不再预生成；这样也顺便避免了"数据生成环境里加载了别的模组时，尝试为它们的方块
     * 生成模型而缺少贴图导致 runData 失败"的问题。
     */
    private boolean isGeneratedModelSource(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return namespace.equals("btsdhz_original")
                || TEMPLATE_CARRIERS.contains(BuiltInRegistries.BLOCK.getKey(block).getPath());
    }

    private void generateMixedSlabBlockStates() {
        ModelFile placeholder = models().getBuilder("merged_slab")
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/cube_all")))
                .texture("all", mcLoc("block/stone"));
        ModelFile itemModel = models().getBuilder("merged_slab_inventory")
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/cube_all")))
                .texture("all", mcLoc("block/stone"));

        var builder = getVariantBuilder(ModBlocks.MERGED_SLAB.get());
        // 遍历 3×3×3 属性组合，保证运行时每个 BlockState 都有模型可查。
        for (VerticalSlabMode mode : VerticalSlabMode.values()) {
            for (SlabType type : SlabType.values()) {
                for (FluidType fluid : FluidType.values()) {
                    builder.partialState()
                            .with(ModBlockStateProperties.MODE, mode)
                            .with(SlabBlock.TYPE, type)
                            .with(ModBlockStateProperties.FLUID_TYPE, fluid)
                            .addModels(new ConfiguredModel(placeholder));
                }
            }
        }

        // 物品模型：中键/其它途径拿到 merged_slab 时显示占位图标，避免紫黑块
        itemModels().getBuilder("merged_slab")
                .parent(new ModelFile.UncheckedModelFile(ResourceLocation.parse("btsdhz_original:block/merged_slab")));
    }

    private boolean isHandAuthSlab(Block slab) {
        return HAND_AUTH_SLABS.contains(BuiltInRegistries.BLOCK.getKey(slab).getPath());
    }

    /**
     * 玻璃板（原版命名空间的 *_pane）部件化的 blockstate 与模型。
     *
     * 一块玻璃板由 12 个部件拼成（12 个属性全为 false 时才是原版那根棍）：
     *  - 4 个水平面片（btsdhz_ne / se / nw / sw）：1/4 面大小、位于中间高度 8 像素处；
     *  - 8 个竖直面片：原版东西南北四个方向各分上下两半（下半用原版的
     *    north/east/south/west 属性，上半用 btsdhz_north_up / east_up / south_up / west_up），
     *    每半是 8 高 × 8 长 × 2 厚。
     * 每个部件的基准模型都是朝北（或东北角）的那一块，其余方向靠 y 轴旋转 90/180/270 摆放：
     * 上/下半模型按 北→东→南→西 旋转，角上面片按 东北→东南→西南→西北 旋转。
     *
     * 铁栏杆和玻璃板同属 IronBarsBlock、柱与横杆尺寸一致，所以同样参与这套部件与规则，
     * 只是默认那根“棍”用它自己的原版模型（iron_bars_post_ends + iron_bars_post）、
     * 大面与断面都用自己的 iron_bars 贴图；其它模组的玻璃板贴图命名不一定遵循原版规则，先不生成。
     *
     * 贴图沿用原版玻璃板的用法（见原版 glass_pane_post / glass_pane_side 模型）：
     * 厚度方向的那 2 像素薄面用 glass_pane_top（#edge，本身就是 2 像素宽的玻璃断面），
     * 大面用玻璃方块贴图（#pane）。
     *
     * 另外，12 个部件属性里只要有一个为 true，
     * 就不渲染默认的那根“棍”（中间立柱 glass_pane_post 与四个未连接方向的柱面 noside）：
     * 棍代表的是“什么都没连”的原版外观，一旦出现连接或角上面片就交给对应部分去画。
     */
    private void generatePaneBlockStates() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!PaneCornerSupport.isSupportedPaneBlock(block)) {
                continue;   // 玻璃板（*_pane）与原版栏杆类（*_bars：铁栏杆、铜栏杆系列）
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            String name = key.getPath();
            boolean bars = name.endsWith("_bars");
            ResourceLocation paneTexture;
            ResourceLocation edgeTexture;
            if (bars) {
                // 栏杆类：大面与断面都用方块自己那张贴图；涂蜡铜栏杆沿用未涂蜡氧化态那张
                // （原版 blockstate 也是这么指的，所以先去掉 waxed_ 前缀）
                ResourceLocation texture = mcLoc("block/" + name.replaceFirst("^waxed_", ""));
                paneTexture = texture;
                edgeTexture = texture;
            } else {
                // 原版命名规则：玻璃板贴图 = 去掉 _pane 后缀的玻璃方块贴图，断面 = 那张玻璃的 _pane_top
                String textureBase = name.substring(0, name.length() - "_pane".length());
                paneTexture = mcLoc("block/" + textureBase);
                edgeTexture = mcLoc("block/" + textureBase + "_pane_top");
            }
            // 每个部件各一份模型，只生成不引用：blockstate 里不再按属性条件挑模型，
            // 而是运行时按状态拼装（见 client/PaneBakedModel、client/PaneModelEvents）。
            // 东/西是北/南的模型绕 y 轴 90° 转出来的，和原版 glass_pane_side 的摆放方式一致。
            panePartModel("pane_corner", name, paneTexture, edgeTexture);
            panePartModel("pane_corner_se", name, paneTexture, edgeTexture);
            panePartModel("pane_corner_sw", name, paneTexture, edgeTexture);
            panePartModel("pane_corner_nw", name, paneTexture, edgeTexture);
            panePartModel("pane_side_lower", name, paneTexture, edgeTexture);
            panePartModel("pane_side_lower_alt", name, paneTexture, edgeTexture);
            panePartModel("pane_side_upper", name, paneTexture, edgeTexture);
            panePartModel("pane_side_upper_alt", name, paneTexture, edgeTexture);

            // blockstate 只留一个变体（不带任何属性条件）：所有状态共用这一个模型。
            // 每个状态的模型数据因此不再各展开一份——那部分（条件判断、状态到模型的映射）
            // 实测是内存的大头；具体画哪些部件交给运行时按状态里那 12 个属性决定。
            // 属性、连接规则、碰撞箱、状态数都不变，所以老存档与调试棒行为不受影响。
            simpleBlock(block, paneModel(bars
                    ? name.replaceFirst("^waxed_", "") + "_post"
                    : name + "_post"));
        }
    }

    private ModelFile paneModel(String path) {
        return new ModelFile.UncheckedModelFile(mcLoc("block/" + path));
    }

    /** 生成某个部件在某种材质下的子模型：父模型是模组里的手写模板，贴图换成该材质。 */
    private ModelFile panePartModel(String template, String material, ResourceLocation paneTexture,
                                    ResourceLocation edgeTexture) {
        return models().getBuilder(template + "_" + material)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/" + template)))
                .texture("pane", paneTexture)
                .texture("edge", edgeTexture);
    }

    private boolean isHandAuthStair(Block stair) {
        return HAND_AUTH_STAIRS.contains(BuiltInRegistries.BLOCK.getKey(stair).getPath());
    }

    // ===== 竖楼梯 + 平放楼梯 blockstate =====
    private void generateStairBlockStates(Block stair, String name) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(stair);
        String namespace = key.getNamespace();

        // 涂蜡铜：原版没有 waxed_* 模型，去掉前缀用未涂蜡模型（外观相同）
        String flatName = name.replaceFirst("^waxed_", "");

        // ---- 平放模型：按命名空间决定前缀 ----
        String modelBase = namespace + ":block/" + flatName;
        ModelFile flatStraight = new ModelFile.UncheckedModelFile(ResourceLocation.parse(modelBase));
        ModelFile flatInner = new ModelFile.UncheckedModelFile(ResourceLocation.parse(modelBase + "_inner"));
        ModelFile flatOuter = new ModelFile.UncheckedModelFile(ResourceLocation.parse(modelBase + "_outer"));

        // ---- 竖放模型：NONE 用 vertical_stair，CORNER 用原版 inner（补块在上半时 x:180） ----
        String vTexture = StairTextureHelper.getTexturePath(stair);
        ResourceLocation vTextureLoc = ResourceLocation.parse(vTexture);

        // 连接形态的模型路径（复用原版 inner；平滑石/涂蜡走 namespace/flatName）
        String innerModelPath = namespace + ":block/" + flatName + "_inner";
        ModelFile connBottom = new ModelFile.UncheckedModelFile(ResourceLocation.parse(innerModelPath));

        // NONE 用竖放模型
        ModelFile verticalModel = models().getBuilder("vertical_stair_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);

        // 连接形态子模型（带贴图，parent 到无贴图的连接模型）
        ModelFile connRight = models().getBuilder("vertical_stair_conn_right_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_right")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);
        ModelFile connLeft = models().getBuilder("vertical_stair_conn_left_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_left")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);
        ModelFile connDouble = models().getBuilder("vertical_stair_conn_double_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_double")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);
        // 上平楼梯专用连接子模型（几何只做上下镜像，不再用 blockstate 翻转，规避 x:180 前后镜像）
        ModelFile connRightTop = models().getBuilder("vertical_stair_conn_right_top_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_right_top")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);
        ModelFile connLeftTop = models().getBuilder("vertical_stair_conn_left_top_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_left_top")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);
        ModelFile connDoubleTop = models().getBuilder("vertical_stair_conn_double_top_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_stair_conn_double_top")))
                .texture("texture", vTextureLoc)
                .texture("particle", vTextureLoc);


        var builder = getVariantBuilder(stair);

        // 竖放：连接形态 × 朝向 × 上下，模型与角度全部由 StairShapeModels 给出（见该类注释）
        // 连接形态不占属性：它写进原版 shape 当载体（见 StairConnection），所以这里是 5 个载体值
        for (Half half : Half.values()) {
            for (int i = 0; i < StairShapeModels.HORIZONTAL_FACINGS.length; i++) {
                Direction d = StairShapeModels.HORIZONTAL_FACINGS[i];
                for (StairConnection connection : StairConnection.values()) {
                    StairShapeModels.Shape shape = StairShapeModels.vertical(connection, half, i);
                    ModelFile model = switch (shape.kind()) {
                        case VERTICAL -> verticalModel;
                        case VANILLA_INNER -> connBottom;
                        case CONN_RIGHT -> shape.topVariant() ? connRightTop : connRight;
                        case CONN_LEFT -> shape.topVariant() ? connLeftTop : connLeft;
                        case CONN_DOUBLE -> shape.topVariant() ? connDoubleTop : connDouble;
                        default -> throw new IllegalStateException("竖放状态不该用到 " + shape.kind());
                    };
                    builder.partialState()
                            .with(ModBlockStateProperties.VERTICAL, true)
                            .with(StairBlock.FACING, d)
                            .with(StairBlock.HALF, half)
                            .with(StairBlock.SHAPE, connection.toShape())
                            .addModels(new ConfiguredModel(model, shape.xRot(), shape.yRot(), shape.uvLock()));
                }
            }
        }

        // ===== 平放 40 变体（VERTICAL=false + facing × half × shape，角度表同样在 StairShapeModels） =====
        for (int s = 0; s < StairShapeModels.flatShapeCount(); s++) {
            for (int f = 0; f < StairShapeModels.HORIZONTAL_FACINGS.length; f++) {
                for (Half half : Half.values()) {
                    StairShapeModels.Shape shape = StairShapeModels.flat(s, half, f);
                    ModelFile m = switch (shape.kind()) {
                        case FLAT_STRAIGHT -> flatStraight;
                        case FLAT_INNER -> flatInner;
                        case FLAT_OUTER -> flatOuter;
                        default -> throw new IllegalStateException("平放状态不该用到 " + shape.kind());
                    };
                    builder.partialState()
                            .with(ModBlockStateProperties.VERTICAL, false)
                            .with(StairBlock.FACING, StairShapeModels.HORIZONTAL_FACINGS[f])
                            .with(StairBlock.HALF, half)
                            .with(StairBlock.SHAPE, StairShapeModels.flatShape(s))
                            .addModels(new ConfiguredModel(m, shape.xRot(), shape.yRot(), shape.uvLock()));
                }
            }
        }
    }

    // ===== 竖台阶 blockstate =====
    private void generateSlabBlockStates(Block slab, String name) {
        ResourceLocation textureLoc = ResourceLocation.parse(SlabTextureHelper.getTexturePath(slab));

        // 涂蜡铜：原版没有 waxed_* 模型，去掉前缀用未涂蜡模型（外观相同）
        String flatName = name.replaceFirst("^waxed_", "");

        ModelFile northModel = models().getBuilder("vertical_slab_north_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_slab_north")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);
        ModelFile southModel = models().getBuilder("vertical_slab_south_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_slab_south")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);
        ModelFile westModel = models().getBuilder("vertical_slab_west_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_slab_west")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);
        ModelFile eastModel = models().getBuilder("vertical_slab_east_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_slab_east")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);
        ModelFile doubleNsModel = models().getBuilder("vertical_double_ns_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_double_ns")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);
        ModelFile doubleEwModel = models().getBuilder("vertical_double_ew_" + name)
                .parent(new ModelFile.UncheckedModelFile(modLoc("block/vertical_double_ew")))
                .texture("texture", textureLoc)
                .texture("particle", textureLoc);

        // 平放模型：涂蜡用未涂蜡模型
        ModelFile slabBottom = new ModelFile.UncheckedModelFile(ResourceLocation.parse("minecraft:block/" + flatName));
        ModelFile slabTop = new ModelFile.UncheckedModelFile(ResourceLocation.parse("minecraft:block/" + flatName + "_top"));
        ModelFile slabDouble = models().getBuilder("slab_double_" + name)
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/cube_all")))
                .texture("all", textureLoc);

        var builder = getVariantBuilder(slab);

        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                .addModels(new ConfiguredModel(slabBottom));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.TOP)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                .addModels(new ConfiguredModel(slabTop));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.DOUBLE)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                .addModels(new ConfiguredModel(slabDouble));

        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .addModels(new ConfiguredModel(northModel));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.TOP)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .addModels(new ConfiguredModel(southModel));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.DOUBLE)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_NS)
                .addModels(new ConfiguredModel(doubleNsModel));

        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_EW)
                .addModels(new ConfiguredModel(westModel));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.TOP)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_EW)
                .addModels(new ConfiguredModel(eastModel));
        builder.partialState()
                .with(SlabBlock.TYPE, SlabType.DOUBLE)
                .with(ModBlockStateProperties.MODE, VerticalSlabMode.VERTICAL_EW)
                .addModels(new ConfiguredModel(doubleEwModel));
    }
}
