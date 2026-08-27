package com.example.myfirstmod.datagen;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import com.example.myfirstmod.util.StairConnection;
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

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, "btsdhz_original", existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 台阶（剔除手工建模的多纹理材质）
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof SlabBlock)
                .filter(slab -> !isHandAuthSlab(slab))
                .forEach(slab -> {
                    String name = BuiltInRegistries.BLOCK.getKey(slab).getPath();
                    generateSlabBlockStates(slab, name);
                });

        // 楼梯（剔除手工建模的多纹理材质）
        BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof StairBlock)
                .filter(stair -> !isHandAuthStair(stair))
                .forEach(stair -> {
                    String name = BuiltInRegistries.BLOCK.getKey(stair).getPath();
                    generateStairBlockStates(stair, name);
                });
    }

    private boolean isHandAuthSlab(Block slab) {
        return HAND_AUTH_SLABS.contains(BuiltInRegistries.BLOCK.getKey(slab).getPath());
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
