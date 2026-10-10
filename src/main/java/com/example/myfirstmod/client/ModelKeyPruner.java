package com.example.myfirstmod.client;

import com.example.myfirstmod.util.PaneCornerSupport;
import com.example.myfirstmod.mixin.ModelBakeryAccessor;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.slf4j.Logger;

/**
 * 把原版"每个方块状态各留一条顶层模型位置"的冗余键删掉，改成每块只留一条。
 *
 * <p>背景：原版 {@code BlockStateModelLoader} 会为**每一个状态**
 * 造一条 {@code ModelResourceLocation(方块id + 属性串)} 并注册进 {@code ModelBakery.topLevelModels}，
 * 烘焙时同一批键再建一张 {@code bakedTopLevelModels}，最后被 {@code ModelManager} 当作
 * {@code bakedRegistry} 长期持有。玻璃板一块 8192 个状态、18 块就是 146,880 条键
 * （MRL 24 B + 属性串 24 B + 串的 byte[] 约 226 B + 两张表各一个 HashMap$Node 32 B），
 * 这些状态本来就共用同一个模型实例——**存的全是同一句话的副本**。
 *
 * <p>为什么可以删：运行期渲染走的是另一样东西——{@code ModelManager.reload} 在烘焙末尾就把
 * "状态 → 模型"整个抄进了一张 {@code IdentityHashMap}（{@code ReloadState.modelCache}），
 * {@code BlockModelShaper.getBlockModel(state)} 查的是它。本类的钩子挂在
 * {@code ModelEvent.BakingCompleted}（在 {@code ModelManager.apply} 里、{@code bakedRegistry}
 * 赋值之后、状态缓存替换之前），此时那张按状态的表**已经建好了**，删掉的只是没人再查的键。
 *
 * <p>万一有第三方模组直接按"状态 MRL"来查（{@code modelManager.getModel(stateToModelLocation(状态))}），
 * {@code ModelManagerGetModelMixin} 会把这种查不到的情况按属性串解析回状态、走
 * {@code getBlockModel(state)} 兜底，所以对外表现不变（抽查自检见下表那条日志）。
 */
public final class ModelKeyPruner {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 每个方块抽几个状态去验证"删掉的键还能不能查回来"。 */
    private static final int SAMPLE_PER_BLOCK = 3;

    /** 安全阀：`-Dbtsdhz_original.pruneModelKeys=false` 可以整条关掉（与紧凑邻居表同一套做法）。 */
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("btsdhz_original.pruneModelKeys"));

    /** 待自检项：资源重载换好状态缓存之后再验（钩子触发时缓存还是旧的）。 */
    private static final List<Pending> PENDING = new ArrayList<>();

    private ModelKeyPruner() {
    }

    /** 资源重载完成、顶层模型表建好之后调用（{@code BtsdhzOriginal} 注册在模组事件总线上）。 */
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        if (!ENABLED) {
            return;
        }
        Map<ModelResourceLocation, BakedModel> baked = event.getModelBakery().getBakedTopLevelModels();
        Map<ModelResourceLocation, UnbakedModel> unbaked =
                ((ModelBakeryAccessor) event.getModelBakery()).btsdhz$topLevelModels();

        Map<ResourceLocation, String> targets = targets();
        Set<ResourceLocation> kept = new HashSet<>();
        List<ModelResourceLocation> redundant = new ArrayList<>();
        Map<String, long[]> perCategory = new LinkedHashMap<>();   // [条数, 估算字节]

        for (ModelResourceLocation location : baked.keySet()) {
            if (!isStateKey(location) || !targets.containsKey(location.id())) {
                continue;
            }
            if (kept.add(location.id())) {
                continue;   // 这一块的第一条留着当代表
            }
            redundant.add(location);
            long[] row = perCategory.computeIfAbsent(targets.get(location.id()), key -> new long[2]);
            row[0]++;
            row[1] += accountedBytes(location);
        }
        if (redundant.isEmpty()) {
            return;
        }

        // 两张表用的是同一批键对象，必须都删掉才能真正释放那些 MRL / 属性串
        for (ModelResourceLocation location : redundant) {
            baked.remove(location);
            unbaked.remove(location);
        }

        PENDING.add(new Pending(Map.copyOf(targets), redundant.size(), Map.copyOf(perCategory)));
        LOGGER.info("[原版精修/模型] 顶层模型表按方块去重：删掉 {} 条状态键、约 {}（每块只留 1 条）——"
                + "运行期渲染走的是按状态建好的缓存表，这一步只清掉没人再查的键，外观与碰撞箱不变",
                redundant.size(), describe(perCategory));
    }

    /**
     * 每个客户端 tick 检查一次（{@code ModClientEvents} 调用）：等状态缓存换好之后，
     * 验证改动过的方块"运行期查模型"和"按状态 MRL 查模型"两条路都还能拿到模型。
     */
    public static void tickChecks() {
        if (PENDING.isEmpty()) {
            return;
        }
        Pending pending = PENDING.remove(0);
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getModelManager();
        BakedModel missing = modelManager.getMissingModel();
        BlockModelShaper shaper = modelManager.getBlockModelShaper();

        int states = 0;
        int missingStates = 0;
        int sampled = 0;
        int sampledOk = 0;
        for (ResourceLocation id : pending.targets().keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null) {
                continue;
            }
            List<BlockState> all = block.getStateDefinition().getPossibleStates();
            for (BlockState state : all) {
                states++;
                if (shaper.getBlockModel(state) == missing) {
                    missingStates++;
                }
            }
            for (BlockState state : sample(all)) {
                sampled++;
                // 这条键已经被删了，能查回来只可能是 ModelManagerGetModelMixin 的兜底生效
                if (modelManager.getModel(BlockModelShaper.stateToModelLocation(state)) != missing) {
                    sampledOk++;
                }
            }
        }

        if (missingStates == 0 && sampled == sampledOk) {
            LOGGER.info("[原版精修/模型] 顶层模型表去重自检通过：删掉 {} 条状态键、约 {}；{} 个状态的运行期模型"
                            + "全部命中，抽查 {} 条已删的键经兜底解析也都查得回模型",
                    pending.removed(), describe(pending.perCategory()), states, sampled);
        } else {
            LOGGER.warn("[原版精修/模型] 顶层模型表去重自检没过：{} 个状态的运行期模型缺失，抽查 {} 条里有 {} 条"
                            + "按状态 MRL 查不回来（兜底解析失效？）", missingStates, sampled, sampled - sampledOk);
        }
    }

    /**
     * 把"状态 MRL"（{@code 方块id#属性名=值,...}）解析回方块状态；不是本模组动过的方块就返回 null。
     * 只在 {@code ModelManager.getModel} 查不到的时候当兜底用。
     */
    @Nullable
    public static BlockState stateFromVariant(ModelResourceLocation location) {
        if (!isStateKey(location)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(location.id());
        if (block == null || !isOurBlock(block)) {
            return null;
        }
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        BlockState state = block.defaultBlockState();
        for (String part : location.getVariant().split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                return null;
            }
            Property<?> property = definition.getProperty(part.substring(0, equals));
            if (property == null) {
                return null;
            }
            state = withValue(state, property, part.substring(equals + 1));
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    /** 是不是"按状态"的键（物品栏的 {@code inventory}、独立模型的 {@code standalone} 都不是）。 */
    private static boolean isStateKey(ModelResourceLocation location) {
        return location.getVariant().indexOf('=') >= 0;
    }

    /** 本模组改过模型/形状的五类方块（口径与 SlabbedModelEvents / PartsModelEvents / PaneModelEvents 一致）。 */
    private static boolean isOurBlock(Block block) {
        if (block instanceof SlabBlock || block instanceof StairBlock
                || block instanceof WallBlock || block instanceof FenceBlock) {
            return true;
        }
        return block instanceof IronBarsBlock && PaneCornerSupport.isSupportedPane(block.defaultBlockState());
    }

    private static Map<ResourceLocation, String> targets() {
        Map<ResourceLocation, String> out = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String category = null;
            if (block instanceof SlabBlock) {
                category = "竖半砖";
            } else if (block instanceof StairBlock) {
                category = "竖楼梯";
            } else if (block instanceof WallBlock) {
                category = "墙";
            } else if (block instanceof FenceBlock) {
                category = "栅栏";
            } else if (block instanceof IronBarsBlock
                    && PaneCornerSupport.isSupportedPane(block.defaultBlockState())) {
                category = "玻璃板/铁栏杆";
            }
            if (category != null) {
                out.put(BuiltInRegistries.BLOCK.getKey(block), category);
            }
        }
        return out;
    }

    /**
     * 删掉这条键真正释放掉的字节（与实测口径一致）：MRL 对象 24 B + 属性串 String 24 B +
     * 串内容的 byte[] (16 + 字符数) + 两张 HashMap 各一个条目 2×32 B。模型本身不在这笔账里
     * （删掉的是同一句话的副本，值仍然是同一个实例）。
     */
    private static long accountedBytes(ModelResourceLocation location) {
        return 24L + 24L + (16L + location.getVariant().length()) + 64L;
    }

    private static String describe(Map<String, long[]> perCategory) {
        StringBuilder text = new StringBuilder();
        perCategory.forEach((category, row) -> {
            if (!text.isEmpty()) {
                text.append('、');
            }
            text.append(category).append(' ').append(row[0]).append(" 条 ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f MiB", row[1] / 1048576.0));
        });
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<?> property, String value) {
        return ((Property<T>) property).getValue(value).map(v -> state.setValue((Property<T>) property, v)).orElse(null);
    }

    /** 首/中/尾各一个状态，够证明兜底解析在同一条链路上是通的。 */
    private static List<BlockState> sample(List<BlockState> all) {
        if (all.isEmpty()) {
            return List.of();
        }
        Set<BlockState> picked = new java.util.LinkedHashSet<>();
        picked.add(all.get(0));
        picked.add(all.get(all.size() / 2));
        picked.add(all.get(all.size() - 1));
        int step = Math.max(1, all.size() / SAMPLE_PER_BLOCK);
        for (int i = 0; i < all.size() && picked.size() < SAMPLE_PER_BLOCK; i += step) {
            picked.add(all.get(i));
        }
        return List.copyOf(picked);
    }

    private record Pending(Map<ResourceLocation, String> targets, int removed, Map<String, long[]> perCategory) {
    }
}
