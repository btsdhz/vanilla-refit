package com.example.myfirstmod.datagen;

import com.example.myfirstmod.ModBlocks;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import com.example.myfirstmod.util.StairConnection;
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
            "red_sandstone_stairs"
    );

    /**
     * 玻璃板的八个方向属性：原版的东西南北 + 本模组的四个角。
     * 任意一个为 true 时就不渲染默认那根“棍”（立柱与柱面）。
     */
    private static final List<Property<Boolean>> PANE_DIRECTIONS = List.of(
            CrossCollisionBlock.NORTH, CrossCollisionBlock.EAST, CrossCollisionBlock.SOUTH, CrossCollisionBlock.WEST,
            ModBlockStateProperties.PANE_NORTH_EAST, ModBlockStateProperties.PANE_SOUTH_EAST,
            ModBlockStateProperties.PANE_NORTH_WEST, ModBlockStateProperties.PANE_SOUTH_WEST);

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, "btsdhz_original", existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 混合半砖：一格放两块不同材质半砖。模型是占位（客户端运行时合并两块半砖几何）
        generateMixedSlabBlockStates();

        // 玻璃板：原版 multipart 之外追加四个“角上水平面片”属性对应的部分
        generatePaneBlockStates();

        // 台阶（剔除手工建模的多纹理材质）
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof SlabBlock)
                .filter(this::isSupportedNamespace)
                .filter(slab -> !isHandAuthSlab(slab))
                .forEach(slab -> {
                    String name = BuiltInRegistries.BLOCK.getKey(slab).getPath();
                    generateSlabBlockStates(slab, name);
                });

        // 楼梯（剔除手工建模的多纹理材质）
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof StairBlock)
                .filter(this::isSupportedNamespace)
                .filter(stair -> !isHandAuthStair(stair))
                .forEach(stair -> {
                    String name = BuiltInRegistries.BLOCK.getKey(stair).getPath();
                    generateStairBlockStates(stair, name);
                });
    }

    /**
     * 只为本模组与原版命名空间的方块生成 blockstate/模型。
     * 否则当数据生成环境里加载了其它模组时，会尝试为它们的台阶/楼梯生成，
     * 而这些方块并不存在于本模组的数据上下文里（缺少贴图/模型），导致 runData 失败。
     */
    private boolean isSupportedNamespace(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return namespace.equals("minecraft") || namespace.equals("btsdhz_original");
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
     * 玻璃板（原版命名空间的 *_pane）四角水平面片的 blockstate 与模型。
     *
     * 原版玻璃板的 blockstate 是 multipart（立柱 + 四个方向的 side/noside），
     * 这里先按原版规则原样重建这些部分，保证外观不变，再追加四个方向属性
     * btsdhz_ne / btsdhz_se / btsdhz_nw / btsdhz_sw 对应的水平面片：
     * 四个方向的面片是同一块 1/4 面的几何，用 y 轴旋转分别摆到东南/西南/西北三个角
     * （基准模型是东北角，y=90→东南，y=180→西南，y=270→西北）。
     *
     * 铁栏杆虽然也是 IronBarsBlock，但原版结构不同且没有 *_pane_top 之类的贴图，不参与本功能；
     * 其它模组的玻璃板贴图命名不一定遵循原版规则，也先不生成（判定见 util/PaneCornerSupport）。
     *
     * 贴图沿用原版玻璃板的用法（见原版 glass_pane_post / glass_pane_side 模型）：
     * 厚度方向的那 2 像素薄面用 glass_pane_top（#edge，本身就是 2 像素宽的玻璃断面），
     * 大面用玻璃方块贴图（#pane）。面片的大面是上/下面，四个侧边是厚度方向的薄面。
     *
     * 另外，八个方向属性（原版东西南北 + 本模组四个角）里只要有一个为 true，
     * 就不渲染默认的那根“棍”（中间立柱 glass_pane_post 与四个未连接方向的柱面 noside）：
     * 棍代表的是“什么都没连”的原版外观，一旦出现连接或角上面片就交给对应部分去画。
     */
    private void generatePaneBlockStates() {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof IronBarsBlock) || block == Blocks.IRON_BARS) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            String name = key.getPath();
            if (!key.getNamespace().equals("minecraft") || !name.endsWith("_pane")) {
                continue;
            }
            // 原版命名规则：玻璃板贴图 = 去掉 _pane 后缀的玻璃方块贴图
            String textureBase = name.substring(0, name.length() - "_pane".length());
            ModelFile corner = models().getBuilder("pane_corner_" + name)
                    .parent(new ModelFile.UncheckedModelFile(modLoc("block/pane_corner")))
                    .texture("pane", mcLoc("block/" + textureBase))
                    .texture("edge", mcLoc("block/" + textureBase + "_pane_top"));

            ModelFile post = paneModel(name + "_post");
            ModelFile side = paneModel(name + "_side");
            ModelFile sideAlt = paneModel(name + "_side_alt");
            ModelFile noSide = paneModel(name + "_noside");
            ModelFile noSideAlt = paneModel(name + "_noside_alt");

            MultiPartBlockStateBuilder builder = getMultipartBuilder(block);
            // ===== 原版部分 =====
            // 默认的“棍”：中间立柱 + 四个未连接方向的柱面，只在八个方向属性全为 false 时渲染
            noStick(builder.part().modelFile(post).addModel());
            noStick(builder.part().modelFile(noSide).addModel());
            noStick(builder.part().modelFile(noSideAlt).addModel());
            noStick(builder.part().modelFile(noSideAlt).rotationY(90).addModel());
            noStick(builder.part().modelFile(noSide).rotationY(270).addModel());
            // 有连接的方向：各自的横杆
            builder.part().modelFile(side).addModel().condition(CrossCollisionBlock.NORTH, true);
            builder.part().modelFile(side).rotationY(90).addModel().condition(CrossCollisionBlock.EAST, true);
            builder.part().modelFile(sideAlt).addModel().condition(CrossCollisionBlock.SOUTH, true);
            builder.part().modelFile(sideAlt).rotationY(90).addModel().condition(CrossCollisionBlock.WEST, true);
            // ===== 本模组新增：四个角的水平面片 =====
            builder.part().modelFile(corner).addModel()
                    .condition(ModBlockStateProperties.PANE_NORTH_EAST, true);
            builder.part().modelFile(corner).rotationY(90).addModel()
                    .condition(ModBlockStateProperties.PANE_SOUTH_EAST, true);
            builder.part().modelFile(corner).rotationY(180).addModel()
                    .condition(ModBlockStateProperties.PANE_SOUTH_WEST, true);
            builder.part().modelFile(corner).rotationY(270).addModel()
                    .condition(ModBlockStateProperties.PANE_NORTH_WEST, true);
        }
    }

    private ModelFile paneModel(String path) {
        return new ModelFile.UncheckedModelFile(mcLoc("block/" + path));
    }

    /** 默认那根“棍”的判定：八个方向属性必须全为 false。 */
    private static void noStick(MultiPartBlockStateBuilder.PartBuilder part) {
        for (Property<Boolean> property : PANE_DIRECTIONS) {
            part.condition(property, false);
        }
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

        // ---- 竖放模型：NONE 用 vertical_stair，BOTTOM/TOP 用原版 inner(+TOP 用 x:180) ----
        String vTexture = StairTextureHelper.getTexturePath(stair);
        ResourceLocation vTextureLoc = ResourceLocation.parse(vTexture);

        // 连接形态的模型路径（复用原版 inner；平滑石/涂蜡走 namespace/flatName）
        String innerModelPath = namespace + ":block/" + flatName + "_inner";
        ModelFile connBottom = new ModelFile.UncheckedModelFile(ResourceLocation.parse(innerModelPath));

        // NONE 用我们的竖模型
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


        Direction[] vDirs = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
        int[] vY = { 270, 0, 90, 180 };  // 沿用已调好的方向

        var builder = getVariantBuilder(stair);   // ← 补这一行

        for (Half half : Half.values()) {
            for (int i = 0; i < 4; i++) {
                Direction d = vDirs[i];
                int y = vY[i];
                // NONE（L 形整体满高，上下翻转几何一致，均用 x=0）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.NONE)
                        .addModels(new ConfiguredModel(verticalModel, 0, y, true));
                // BOTTOM（原版 inner）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.BOTTOM)
                        .addModels(new ConfiguredModel(connBottom, 0, (y + 270) % 360, true));
                // TOP（原版 inner + x:180）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.TOP)
                        .addModels(new ConfiguredModel(connBottom, 180, y % 360, true));
                // CONN_RIGHT（单大面连接右）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.CONN_RIGHT)
                        .addModels(new ConfiguredModel(half == Half.TOP ? connRightTop : connRight, 0, y, true));
                // CONN_LEFT（单大面连接左）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.CONN_LEFT)
                        .addModels(new ConfiguredModel(half == Half.TOP ? connLeftTop : connLeft, 0, y, true));
                // CONN_DOUBLE（双大面连接）
                builder.partialState()
                        .with(ModBlockStateProperties.VERTICAL, true)
                        .with(StairBlock.FACING, d)
                        .with(StairBlock.HALF, half)
                        .with(ModBlockStateProperties.STAIR_CONNECTION, StairConnection.CONN_DOUBLE)
                        .addModels(new ConfiguredModel(half == Half.TOP ? connDoubleTop : connDouble, 0, y, true));
            }
        }

        // ===== 平放 40 变体（VERTICAL=false + facing × half × shape，硬编码旋转） =====
        int[][] Y_BOTTOM = {
                { 270,   0,  90, 180 },  // straight
                { 180, 270,   0,  90 },  // inner_left
                { 270,   0,  90, 180 },  // inner_right
                { 180, 270,   0,  90 },  // outer_left
                { 270,   0,  90, 180 }   // outer_right
        };
        int[][] Y_TOP = {
                { 270,   0,  90, 180 },  // straight
                { 270,   0,  90, 180 },  // inner_left
                {   0,  90, 180, 270 },  // inner_right
                { 270,   0,  90, 180 },  // outer_left
                {   0,  90, 180, 270 }   // outer_right
        };
        boolean[][] UV_BOTTOM = {
                { true, false,  true,  true },  // straight (east 无)
                { true,  true, false,  true },  // inner_left (south 无)
                { true, false,  true,  true },  // inner_right (east 无)
                { true,  true, false,  true },  // outer_left (south 无)
                { true, false,  true,  true }   // outer_right (east 无)
        };

        StairsShape[] shapes = {
                StairsShape.STRAIGHT, StairsShape.INNER_LEFT, StairsShape.INNER_RIGHT,
                StairsShape.OUTER_LEFT, StairsShape.OUTER_RIGHT
        };
        for (int s = 0; s < 5; s++) {
            ModelFile m = switch (s) {
                case 0 -> flatStraight;
                case 1, 2 -> flatInner;
                default -> flatOuter;
            };
            for (int f = 0; f < 4; f++) {
                for (Half half : Half.values()) {
                    int x = (half == Half.TOP) ? 180 : 0;
                    int y = (half == Half.TOP) ? Y_TOP[s][f] : Y_BOTTOM[s][f];
                    boolean uv = (half == Half.TOP) || UV_BOTTOM[s][f];
                    builder.partialState()
                            .with(ModBlockStateProperties.VERTICAL, false)
                            .with(StairBlock.FACING, vDirs[f])
                            .with(StairBlock.HALF, half)
                            .with(StairBlock.SHAPE, shapes[s])
                            .addModels(new ConfiguredModel(m, x, y, uv));
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
