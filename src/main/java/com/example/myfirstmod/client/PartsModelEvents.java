package com.example.myfirstmod.client;

import com.example.myfirstmod.client.model.PartBakedModel;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;

/**
 * 把栅栏 / 墙的"每个状态一个拼接结果模型"换成"一个材质一个按状态挑片段的模型"。
 *
 * <p>原版这两种方块用的是 multipart 方块状态：栅栏 5 条规则（中间柱 + 四方向横杆），
 * 墙 9 条规则（中间柱 + 四方向矮段 + 四方向高段）。游戏为**每一个状态**烘焙一个拼接结果模型，
 * 实测本模组在这一项上多出一万六千多个对象；每次取四边形还要逐条判断规则、临时拼列表。
 * 这里换成 {@link PartBakedModel}：片段在烘焙期取一次，渲染时按状态里那几个属性挑。
 *
 * <p>片段的取法（不做任何旋转、不改几何，只做集合运算）：
 * <ul>
 *     <li>栅栏：中间柱 = "四边都不连"的模型（那条规则无条件命中）；某方向横杆 = "只有那个方向连着"的模型
 *         减去中间柱那一份。</li>
 *     <li>墙：中间柱 = "up=true 且四边都不连"减去"什么都不连"（后者是空模型）；
 *         某方向矮段/高段 = "只有那个方向是 low / tall"的模型减去"什么都不连"那一份。</li>
 * </ul>
 * 减的是**同一个对象**（两边都来自原版烘焙结果的同一个四边形），所以不会误伤。
 *
 * <p>安全网：替换之前，对这个方块的**每一个状态**，把本模组的模型返回的四边形和原版那个逐状态模型
 * 返回的四边形按"6 个方向 + 不剔除组"逐组比对（按对象身份比多重集合）。只要有一个状态对不上，
 * 就整个方块保持原样并在日志里报出来——其它模组那些结构不同的栅栏/墙会被这一关挡下来，
 * 不会出现缺面或错面。
 */
public final class PartsModelEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 只用来问模型要四边形；片段模型都是静态的（没有随机变体），固定一个种子即可。 */
    private static final RandomSource RANDOM = RandomSource.create(0L);

    /** 位顺序与 {@link #fenceKey} / {@link #wallKey} 一致：北、东、南、西。 */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private PartsModelEvents() {
    }

    /** 替换栅栏与墙的模型；必须在"下移/上移"那层包装之前调用，让包装套在共享模型外面。 */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        int blocks = 0;
        int states = 0;
        int skipped = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean fence = block instanceof FenceBlock;
            if (!fence && !(block instanceof WallBlock)) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            PartBakedModel shared = fence
                    ? buildFence(models, block, blockId)
                    : buildWall(models, block, blockId);
            if (shared == null) {
                skipped++;
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                models.put(BlockModelShaper.stateToModelLocation(blockId, state), shared);
                states++;
            }
            blocks++;
        }
        LOGGER.info("[原版精修/模型] 栅栏/墙模型已合并：{} 个方块 / {} 个状态共用 {} 个模型实例；"
                        + "替换前已逐状态与原版拼接结果比对（全部一致），结构不同跳过 {} 个方块",
                blocks, states, blocks, skipped);
    }

    /** 栅栏：中间柱（无条件）+ 四方向横杆。 */
    @Nullable
    private static PartBakedModel buildFence(Map<ModelResourceLocation, BakedModel> models, Block block,
                                             ResourceLocation blockId) {
        BlockState base = block.getStateDefinition().any();
        for (Direction direction : HORIZONTAL) {
            base = base.setValue(fenceProperty(direction), false);
        }
        BakedModel baseModel = models.get(BlockModelShaper.stateToModelLocation(blockId, base));
        if (baseModel == null) {
            return null;
        }
        List<List<BakedQuad>> post = bucketsOf(baseModel, base);

        List<List<BakedQuad>>[] sides = newSides();
        for (int i = 0; i < HORIZONTAL.length; i++) {
            BlockState sideState = base.setValue(fenceProperty(HORIZONTAL[i]), true);
            BakedModel sideModel = models.get(BlockModelShaper.stateToModelLocation(blockId, sideState));
            if (sideModel == null) {
                return null;
            }
            sides[i] = subtract(bucketsOf(sideModel, sideState), post);
        }

        PartBakedModel.Part[] parts = new PartBakedModel.Part[1 + HORIZONTAL.length];
        parts[0] = new PartBakedModel.Part(post, key -> true);
        for (int i = 0; i < HORIZONTAL.length; i++) {
            int bit = 1 << i;
            parts[1 + i] = new PartBakedModel.Part(sides[i], key -> (key & bit) != 0);
        }
        return verify(models, block, blockId,
                new PartBakedModel(baseModel, 16, PartsModelEvents::fenceKey, parts));
    }

    /** 墙：中间柱（up）+ 四方向矮段（low）+ 四方向高段（tall）。 */
    @Nullable
    private static PartBakedModel buildWall(Map<ModelResourceLocation, BakedModel> models, Block block,
                                            ResourceLocation blockId) {
        BlockState base = block.getStateDefinition().any().setValue(WallBlock.UP, false);
        for (Direction direction : HORIZONTAL) {
            base = base.setValue(wallProperty(direction), WallSide.NONE);
        }
        BakedModel baseModel = models.get(BlockModelShaper.stateToModelLocation(blockId, base));
        if (baseModel == null) {
            return null;
        }
        // "什么都不连"的墙是空模型，正好当作减法基准
        List<List<BakedQuad>> nothing = bucketsOf(baseModel, base);

        BlockState postState = base.setValue(WallBlock.UP, true);
        BakedModel postModel = models.get(BlockModelShaper.stateToModelLocation(blockId, postState));
        if (postModel == null) {
            return null;
        }
        List<List<BakedQuad>> post = subtract(bucketsOf(postModel, postState), nothing);

        List<List<BakedQuad>>[] low = newSides();
        List<List<BakedQuad>>[] tall = newSides();
        for (int i = 0; i < HORIZONTAL.length; i++) {
            EnumProperty<WallSide> property = wallProperty(HORIZONTAL[i]);
            BlockState lowState = base.setValue(property, WallSide.LOW);
            BakedModel lowModel = models.get(BlockModelShaper.stateToModelLocation(blockId, lowState));
            BlockState tallState = base.setValue(property, WallSide.TALL);
            BakedModel tallModel = models.get(BlockModelShaper.stateToModelLocation(blockId, tallState));
            if (lowModel == null || tallModel == null) {
                return null;
            }
            low[i] = subtract(bucketsOf(lowModel, lowState), nothing);
            tall[i] = subtract(bucketsOf(tallModel, tallState), nothing);
        }

        PartBakedModel.Part[] parts = new PartBakedModel.Part[1 + HORIZONTAL.length * 2];
        parts[0] = new PartBakedModel.Part(post, key -> (key & 1) != 0);
        for (int i = 0; i < HORIZONTAL.length; i++) {
            int shift = 1 + 2 * i;
            parts[1 + i] = new PartBakedModel.Part(low[i], key -> ((key >> shift) & 3) == 1);
            parts[1 + HORIZONTAL.length + i] =
                    new PartBakedModel.Part(tall[i], key -> ((key >> shift) & 3) == 2);
        }
        return verify(models, block, blockId,
                new PartBakedModel(baseModel, 512, PartsModelEvents::wallKey, parts));
    }

    /**
     * 替换前的安全网：对这个方块的每个状态，把共享模型与原版逐状态拼接结果按组比对。
     * 有任何一格对不上就返回 null（保持原版），并把这个方块记进日志。
     */
    @Nullable
    private static PartBakedModel verify(Map<ModelResourceLocation, BakedModel> models, Block block,
                                         ResourceLocation blockId, PartBakedModel shared) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BakedModel original = models.get(BlockModelShaper.stateToModelLocation(blockId, state));
            if (original == null) {
                return null;
            }
            if (!sameQuads(original, shared, state)) {
                LOGGER.warn("[原版精修/模型] {} 的合并模型与逐状态烘焙结果对不上（状态 {}），这个方块保持原版",
                        blockId, state);
                return null;
            }
        }
        return shared;
    }

    /** 栅栏的状态编号：4 个方向各 1 bit。 */
    private static int fenceKey(BlockState state) {
        int key = 0;
        for (int i = 0; i < HORIZONTAL.length; i++) {
            if (state.getValue(fenceProperty(HORIZONTAL[i]))) {
                key |= 1 << i;
            }
        }
        return key;
    }

    /** 墙的状态编号：bit0 = up，之后每个方向 2 bit（0=没有，1=矮段，2=高段，与 WallSide 的序号一致）。 */
    private static int wallKey(BlockState state) {
        int key = state.getValue(WallBlock.UP) ? 1 : 0;
        for (int i = 0; i < HORIZONTAL.length; i++) {
            key |= state.getValue(wallProperty(HORIZONTAL[i])).ordinal() << (1 + 2 * i);
        }
        return key;
    }

    /** 取一个模型的 7 组四边形：6 个方向 + 不剔除组。 */
    private static List<List<BakedQuad>> bucketsOf(BakedModel model, BlockState state) {
        List<List<BakedQuad>> buckets = new ArrayList<>(PartBakedModel.GROUPS);
        for (Direction direction : Direction.values()) {
            buckets.add(List.copyOf(model.getQuads(state, direction, RANDOM, ModelData.EMPTY, null)));
        }
        buckets.add(List.copyOf(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null)));
        return List.copyOf(buckets);
    }

    /** 按对象身份，从 include 的每一组里减掉 exclude 的那些四边形（只搬运分组，不改几何）。 */
    private static List<List<BakedQuad>> subtract(List<List<BakedQuad>> include, List<List<BakedQuad>> exclude) {
        List<List<BakedQuad>> out = new ArrayList<>(PartBakedModel.GROUPS);
        for (int group = 0; group < PartBakedModel.GROUPS; group++) {
            List<BakedQuad> from = include.get(group);
            List<BakedQuad> drop = exclude.get(group);
            if (from.isEmpty() || drop.isEmpty()) {
                out.add(from);
                continue;
            }
            Map<BakedQuad, Boolean> dropping = new IdentityHashMap<>();
            for (BakedQuad quad : drop) {
                dropping.put(quad, Boolean.TRUE);
            }
            List<BakedQuad> keep = new ArrayList<>(from.size());
            for (BakedQuad quad : from) {
                if (!dropping.containsKey(quad)) {
                    keep.add(quad);
                }
            }
            out.add(List.copyOf(keep));
        }
        return List.copyOf(out);
    }

    /** 本模组拼出来的四边形与原版逐状态烘焙的结果是否一致（按组、按对象身份比多重集合）。 */
    private static boolean sameQuads(BakedModel original, PartBakedModel shared, BlockState state) {
        for (int group = 0; group < PartBakedModel.GROUPS; group++) {
            Direction side = group == PartBakedModel.UNCLASSIFIED_GROUP ? null : Direction.values()[group];
            List<BakedQuad> expected = original.getQuads(state, side, RANDOM, ModelData.EMPTY, null);
            List<BakedQuad> actual = shared.getQuads(state, side, RANDOM, ModelData.EMPTY, null);
            if (!sameMultiset(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMultiset(List<BakedQuad> expected, List<BakedQuad> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        Map<BakedQuad, Integer> counts = new IdentityHashMap<>();
        for (BakedQuad quad : expected) {
            counts.merge(quad, 1, Integer::sum);
        }
        for (BakedQuad quad : actual) {
            Integer count = counts.get(quad);
            if (count == null || count == 0) {
                return false;
            }
            counts.put(quad, count - 1);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<List<BakedQuad>>[] newSides() {
        return new List[HORIZONTAL.length];
    }

    private static BooleanProperty fenceProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }

    private static EnumProperty<WallSide> wallProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> WallBlock.NORTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }
}
