package com.example.myfirstmod.debug;

import com.example.myfirstmod.BtsdhzOriginal;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * 渲染基准工具（开发用）：在一个**固定种子**的随机区域里撒几万个方块、把摄像机与时间固定住、
 * 量一段时间内的帧率，量完把方块清掉并保存世界，结果写进 {@code run/btsdhz-bench-result.txt}。
 *
 * <p>触发：在客户端运行目录放 {@code btsdhz-bench.txt}（key=value，每行一条）：
 * <pre>
 * label=before
 * count=40000      # 方块数
 * radius=48        # x/z 随机半径（±48 → 96×96）
 * height=24        # y 方向随机高度
 * seed=20261010    # 固定种子，两次跑必须一样
 * warmup=12        # 预热秒数（等区块网格重建）
 * measure=20       # 测量秒数
 * block=minecraft:glass_pane
 * </pre>
 *
 * <p>随机范围刻意压小（96×24×96 的盒子里 4 万个），保证相邻关系足够多、连接逻辑真的跑起来。
 * 文件不存在时这个工具完全不生效。跑之前记得关垂直同步、关"失焦暂停"（见 {@code run/options.txt}）。
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID, value = Dist.CLIENT)
public final class RenderBench {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Path TRIGGER = Paths.get("btsdhz-bench.txt");
    private static final Path RESULT = Paths.get("btsdhz-bench-result.txt");

    private static final Config CONFIG = readConfig();

    private static final List<Integer> FPS = new ArrayList<>();
    private static final List<Integer> REBUILD_FPS = new ArrayList<>();
    private static final Set<BlockState> STATES = new HashSet<>();

    /** 0 未开始 / 1 放块 / 2 预热 / 3 微基准 / 4 稳态帧率 / 5 重建掉帧 / 6 清理 / 7 完成。 */
    private static int phase;
    private static int index;
    private static int samples;
    private static int connected;
    private static int cleared;
    private static boolean serverBusy;
    private static long stamp;
    private static int dipSamples;
    private static boolean screenshotTaken;
    private static double quadNanosPerBlock;
    private static double quadNanosPerBlockCold;
    private static long quadBlocksSampled;
    private static long blackhole;

    private static List<BlockPos> positions = List.of();
    private static Block block = Blocks.GLASS_PANE;
    private static BlockState placementState = Blocks.GLASS_PANE.defaultBlockState();
    private static Camera camera;
    private static Boolean oldVsync;
    private static Integer oldFramerateLimit;
    private static Boolean oldPauseOnLostFocus;

    private RenderBench() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (CONFIG == null || phase >= 7) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        switch (phase) {
            case 0 -> prepare(minecraft, server);
            case 1 -> placeBatch(minecraft, server);
            case 2 -> settle(minecraft);
            case 3 -> microBench(minecraft, server);
            case 4 -> measure(minecraft, server);
            case 5 -> rebuildDip(minecraft, server);
            default -> clearBatch(minecraft, server);
        }
    }

    /** 区域、光照、摄像机、区块都先备好；玩家切成旁观者免得掉下去。 */
    private static void prepare(Minecraft minecraft, MinecraftServer server) {
        phase = 1;
        serverBusy = true;
        // 关垂直同步 / 放开帧率上限 / 关"失焦暂停"，否则后台跑的客户端会被帧率上限把差异盖掉
        oldVsync = minecraft.options.enableVsync().get();
        oldFramerateLimit = minecraft.options.framerateLimit().get();
        oldPauseOnLostFocus = minecraft.options.pauseOnLostFocus;
        minecraft.options.enableVsync().set(false);
        minecraft.options.framerateLimit().set(260);
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.save();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            level.setWeatherParameters(24000, 0, false, false);

            Random random = new Random(CONFIG.seed());
            List<BlockPos> spots = new ArrayList<>(CONFIG.count());
            for (int i = 0; i < CONFIG.count(); i++) {
                int x = random.nextInt(CONFIG.radius() * 2 + 1) - CONFIG.radius();
                int z = random.nextInt(CONFIG.radius() * 2 + 1) - CONFIG.radius();
                int y = random.nextInt(CONFIG.height());
                spots.add(new BlockPos(x, y, z));
            }
            int surface = level.getMinBuildHeight() + 16;
            for (int cx = -CONFIG.radius(); cx <= CONFIG.radius(); cx += 4) {
                for (int cz = -CONFIG.radius(); cz <= CONFIG.radius(); cz += 4) {
                    surface = Math.max(surface,
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz));
                }
            }
            int baseY = surface + 12;
            List<BlockPos> placed = new ArrayList<>(spots.size());
            for (BlockPos spot : spots) {
                BlockPos pos = new BlockPos(spot.getX(), baseY + spot.getY(), spot.getZ());
                placed.add(pos);
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);   // 先把地形生成出来
            }
            positions = placed;
            camera = new Camera(0.5, baseY + CONFIG.height() / 2.0 + 6.0, -CONFIG.radius() * 2.0 - 12.0);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, camera.x(), camera.y(), camera.z(), 0.0F, camera.pitch());
            }
            LOGGER.info("[原版精修/基准] 场景已备好：{} 个 {}，基准高度 y={}，摄像机 ({}, {}, {})",
                    positions.size(), BuiltInRegistries.BLOCK.getKey(block), baseY,
                    String.format(Locale.ROOT, "%.1f, %.1f, %.1f", camera.x(), camera.y(), camera.z()));
            serverBusy = false;
        });
    }

    private static void placeBatch(Minecraft minecraft, MinecraftServer server) {
        holdCamera(minecraft);
        if (serverBusy || positions.isEmpty()) {
            return;
        }
        if (index >= positions.size()) {
            phase = 2;
            stamp = 0L;
            return;
        }
        int from = index;
        int to = Math.min(positions.size(), from + CONFIG.batch());
        index = to;
        serverBusy = true;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int i = from; i < to; i++) {
                level.setBlock(positions.get(i), placementState, 3);
            }
            serverBusy = false;
        });
    }

    private static void settle(Minecraft minecraft) {
        holdCamera(minecraft);
        if (stamp == 0L) {
            stamp = System.nanoTime();
        }
        if (System.nanoTime() - stamp < CONFIG.warmup() * 1_000_000_000L) {
            return;
        }
        countStates();
        LOGGER.info("[原版精修/基准] 放置完成：{} 个方块里有 {} 个连了邻居、{} 种状态；"
                        + "预热 {} 秒后开始测量", positions.size(), connected, STATES.size(), CONFIG.warmup());
        phase = 3;
        samples = 0;
        stamp = System.nanoTime();
    }

    /**
     * 微基准：直接量"每个方块取一遍四边形"的耗时（原版 {@code tesselateBlock} 每次就是
     * 6 个方向 + 一次不带方向，共 7 次 {@code getQuads}）。这一段就是属性读取与模型拼装的真实代价，
     * 不受 GPU / 帧率上限干扰；两种实现跑同一批状态、同一台机器，可以直接比。
     */
    private static void microBench(Minecraft minecraft, MinecraftServer server) {
        holdCamera(minecraft);
        BlockRenderDispatcher renderer = minecraft.getBlockRenderer();
        List<BlockState> states = List.copyOf(STATES);
        if (states.isEmpty()) {
            phase = 4;
            samples = 0;
            stamp = System.nanoTime();
            return;
        }
        quadNanosPerBlockCold = runQuadPass(renderer, states, Math.max(1, CONFIG.reps() / 8));
        quadNanosPerBlock = runQuadPass(renderer, states, CONFIG.reps());
        quadBlocksSampled = (long) states.size() * CONFIG.reps();
        LOGGER.info("[原版精修/基准] getQuads 微基准：{} 种状态 × {} 轮，热身后每方块取一遍四边形 "
                        + "{} ns（冷 {} ns）", states.size(), CONFIG.reps(),
                String.format(Locale.ROOT, "%.1f", quadNanosPerBlock),
                String.format(Locale.ROOT, "%.1f", quadNanosPerBlockCold));
        phase = 4;
        samples = 0;
        stamp = System.nanoTime();
    }

    private static double runQuadPass(BlockRenderDispatcher renderer, List<BlockState> states, int reps) {
        RandomSource random = RandomSource.create(42L);
        long total = 0L;
        for (int rep = 0; rep < reps; rep++) {
            long start = System.nanoTime();
            for (BlockState state : states) {
                BakedModel model = renderer.getBlockModel(state);
                for (Direction direction : Direction.values()) {
                    blackhole += model.getQuads(state, direction, random).size();
                }
                blackhole += model.getQuads(state, null, random).size();
            }
            total += System.nanoTime() - start;
        }
        return reps == 0 ? 0.0 : (double) total / ((double) reps * states.size());
    }

    private static void measure(Minecraft minecraft, MinecraftServer server) {
        holdCamera(minecraft);
        if (!screenshotTaken && samples >= 1) {
            screenshotTaken = true;
            Screenshot.grab(minecraft.gameDirectory, "bench-" + CONFIG.label() + ".png",
                    minecraft.getMainRenderTarget(), component -> LOGGER.info("[原版精修/基准] 截图：{}", component.getString()));
        }
        if (System.nanoTime() - stamp < samples * 1_000_000_000L) {
            return;
        }
        FPS.add(minecraft.getFps());
        samples++;
        if (samples < CONFIG.measure()) {
            return;
        }
        phase = 5;
        dipSamples = 0;
        REBUILD_FPS.clear();
        stamp = System.nanoTime();
        minecraft.levelRenderer.allChanged();
        LOGGER.info("[原版精修/基准] 稳态测量结束，已强制整块重绘（观察重建掉帧）");
    }

    /** 强制所有区块网格重建后，再采几秒帧率，看重建把帧率压低多少。 */
    private static void rebuildDip(Minecraft minecraft, MinecraftServer server) {
        holdCamera(minecraft);
        if (System.nanoTime() - stamp < dipSamples * 1_000_000_000L) {
            return;
        }
        REBUILD_FPS.add(minecraft.getFps());
        dipSamples++;
        if (dipSamples < 5) {
            return;
        }
        writeResult();
        phase = 6;
        index = 0;
    }

    private static void clearBatch(Minecraft minecraft, MinecraftServer server) {
        holdCamera(minecraft);
        if (serverBusy) {
            return;
        }
        if (index >= positions.size()) {
            serverBusy = true;
            server.execute(() -> {
                server.saveEverything(true, true, false);
                appendResult("cleared=" + cleared);
                LOGGER.info("[原版精修/基准] 已清理 {} 个测试方块并保存世界", cleared);
            });
            restoreOptions(minecraft);
            phase = 7;
            return;
        }
        int from = index;
        int to = Math.min(positions.size(), from + CONFIG.batch() * 4);
        index = to;
        serverBusy = true;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int i = from; i < to; i++) {
                if (level.setBlock(positions.get(i), Blocks.AIR.defaultBlockState(), 3)) {
                    cleared++;
                }
            }
            serverBusy = false;
        });
    }

    private static void holdCamera(Minecraft minecraft) {
        if (camera == null || minecraft.player == null) {
            return;
        }
        minecraft.player.setPos(camera.x(), camera.y(), camera.z());
        minecraft.player.setDeltaMovement(0, 0, 0);
        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(camera.pitch());
    }

    /** 把跑基准前改掉的视频选项放回去。 */
    private static void restoreOptions(Minecraft minecraft) {
        if (oldVsync != null) {
            minecraft.options.enableVsync().set(oldVsync);
        }
        if (oldFramerateLimit != null) {
            minecraft.options.framerateLimit().set(oldFramerateLimit);
        }
        if (oldPauseOnLostFocus != null) {
            minecraft.options.pauseOnLostFocus = oldPauseOnLostFocus;
        }
        minecraft.options.save();
    }

    private static void countStates() {
        STATES.clear();
        connected = 0;
        for (BlockPos pos : positions) {
            BlockState state = Minecraft.getInstance().level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            STATES.add(state);
            if (state.hasProperty(CrossCollisionBlock.NORTH)
                    && (state.getValue(CrossCollisionBlock.NORTH) || state.getValue(CrossCollisionBlock.EAST)
                    || state.getValue(CrossCollisionBlock.SOUTH) || state.getValue(CrossCollisionBlock.WEST))) {
                connected++;
            }
        }
    }

    private static void writeResult() {
        double mean = FPS.isEmpty() ? 0.0 : FPS.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int min = FPS.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = FPS.stream().mapToInt(Integer::intValue).max().orElse(0);
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        String text = """
                label=%s
                block=%s
                count=%d
                radius=%d
                height=%d
                seed=%d
                warmup=%d
                measure=%d
                distinctStates=%d
                connectedPanels=%d
                quadBlocksSampled=%d
                quadNanosPerBlock=%.1f
                quadNanosPerBlockCold=%.1f
                fpsSamples=%s
                fpsMean=%.2f
                fpsMin=%d
                fpsMax=%d
                rebuildFpsSamples=%s
                usedHeapBytes=%d
                """.formatted(CONFIG.label(), BuiltInRegistries.BLOCK.getKey(block), positions.size(),
                CONFIG.radius(), CONFIG.height(), CONFIG.seed(), CONFIG.warmup(), CONFIG.measure(),
                STATES.size(), connected, quadBlocksSampled, quadNanosPerBlock, quadNanosPerBlockCold,
                FPS, mean, min, max, REBUILD_FPS, used);
        try {
            Files.writeString(RESULT, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[原版精修/基准] 结果文件写不出去", e);
        }
            LOGGER.info("[原版精修/基准] {}：{} 个方块（{} 个有连接、{} 种状态）帧率 平均 {} / 最低 {} / 最高 {}，"
                            + "重建后帧率 {}，getQuads {} ns/方块",
                CONFIG.label(), positions.size(), connected, STATES.size(),
                String.format(Locale.ROOT, "%.1f", mean), min, max, REBUILD_FPS,
                String.format(Locale.ROOT, "%.1f", quadNanosPerBlock));
    }

    private static void appendResult(String line) {
        try {
            Files.writeString(RESULT, Files.readString(RESULT, StandardCharsets.UTF_8) + line + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[原版精修/基准] 结果文件追加失败：{}", e.toString());
        }
    }

    @Nullable
    private static Config readConfig() {
        try {
            if (!Files.isRegularFile(TRIGGER)) {
                return null;
            }
            int count = 40000;
            int radius = 48;
            int height = 24;
            long seed = 20261010L;
            int warmup = 12;
            int measure = 20;
            int batch = 2000;
            int reps = 1500;
            String label = "bench";
            String blockId = "minecraft:glass_pane";
            for (String line : Files.readAllLines(TRIGGER, StandardCharsets.UTF_8)) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String key = parts[0].trim().toLowerCase(Locale.ROOT);
                String value = parts[1].trim();
                switch (key) {
                    case "count" -> count = Integer.parseInt(value);
                    case "radius" -> radius = Integer.parseInt(value);
                    case "height" -> height = Integer.parseInt(value);
                    case "seed" -> seed = Long.parseLong(value);
                    case "warmup" -> warmup = Integer.parseInt(value);
                    case "measure" -> measure = Integer.parseInt(value);
                    case "batch" -> batch = Integer.parseInt(value);
                    case "reps" -> reps = Integer.parseInt(value);
                    case "label" -> label = value;
                    case "block" -> blockId = value;
                    default -> {
                    }
                }
            }
            Block parsed = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
            if (parsed != null && parsed != Blocks.AIR) {
                block = parsed;
                placementState = parsed.defaultBlockState();
            }
            return new Config(label, count, radius, height, seed, warmup, measure, batch, reps);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[原版精修/基准] 读不了 btsdhz-bench.txt：{}", e.toString());
            return null;
        }
    }

    private record Config(String label, int count, int radius, int height, long seed,
                          int warmup, int measure, int batch, int reps) {
    }

    private record Camera(double x, double y, double z, float pitch) {
        Camera(double x, double y, double z) {
            this(x, y, z, 3.0F);
        }
    }
}
