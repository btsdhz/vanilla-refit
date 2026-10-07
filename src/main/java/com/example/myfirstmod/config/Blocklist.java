package com.example.myfirstmod.config;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * 方块名单：决定「哪些方块允许本模组给它加竖形态」。
 *
 * <p>格式（配置项 blocklist，每行一条，从上往下匹配，<b>第一条命中的生效</b>；
 * 一条都没命中时默认<b>允许</b>）：
 *
 * <pre>
 *   +*                        全部允许（通常放第一条当兜底）
 *   -*                        全部禁止
 *   +minecraft:oak_slab       按方块 ID
 *   +somemod:*                按模组（命名空间）
 *   +#minecraft:wooden_slabs  按方块标签
 * </pre>
 *
 * <p>注意：属性是注册期注入的，名单没法把方块状态"变回去"，它只影响
 * <b>放置时会不会生成竖形态</b>（以及竖楼梯的连接）。所以它是个逃生口：
 * 某个方块模型不好看、或者和其它模组打架时，可以单独把它退回原版放置。
 */
public final class Blocklist {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 条目类型。 */
    public enum Kind {
        /** {@code +*} / {@code -*}：匹配任何方块。 */
        GLOBAL,
        /** {@code +minecraft:oak_slab}：按方块 ID。 */
        BLOCK_ID,
        /** {@code +somemod:*}：按模组（命名空间）。 */
        MOD_ID,
        /** {@code +#minecraft:wooden_slabs}：按方块标签。 */
        BLOCK_TAG
    }

    /**
     * 一条名单记录。
     *
     * @param allow 命中之后是允许还是禁止
     * @param kind  条目类型
     * @param value 去掉正负号和类型符号之后的值，自查命令直接显示它
     * @param tag   标签条目解析出的 TagKey，其它类型为 null
     */
    public record Entry(boolean allow, Kind kind, String value, TagKey<Block> tag) {

        public boolean matches(Block block, ResourceLocation id) {
            return switch (kind) {
                case GLOBAL -> true;
                case BLOCK_ID -> value.equals(id.toString());
                case MOD_ID -> value.equals(id.getNamespace());
                case BLOCK_TAG -> tag != null && block.defaultBlockState().is(tag);
            };
        }

        /** 自查命令里的显示形式。 */
        public String describe() {
            String sign = allow ? "+" : "-";
            return switch (kind) {
                case GLOBAL -> sign + "*  全部方块";
                case BLOCK_ID -> sign + value + "  方块";
                case MOD_ID -> sign + value + ":*  模组";
                case BLOCK_TAG -> sign + "#" + value + "  标签";
            };
        }
    }

    private static final List<Entry> DEFAULT_ENTRIES = List.of(new Entry(true, Kind.GLOBAL, "*", null));

    private Blocklist() {
    }

    /**
     * 解析后的名单。
     *
     * <p>每次调用都重新读配置，这样改完配置文件重载就立刻生效，不需要额外的失效逻辑；
     * 名单本身只有几行，这个开销可以忽略。
     */
    public static List<Entry> entries() {
        List<? extends String> raw;
        try {
            raw = BtsdhzConfig.BLOCKLIST.get();
        } catch (IllegalStateException notLoadedYet) {
            // 配置还没读进来（例如数据生成阶段）：按"全开"处理
            return DEFAULT_ENTRIES;
        }
        List<Entry> parsed = new ArrayList<>(raw.size());
        for (String line : raw) {
            Entry entry = parse(line);
            if (entry != null) {
                parsed.add(entry);
            }
        }
        return parsed.isEmpty() ? DEFAULT_ENTRIES : parsed;
    }

    /** 该方块是否允许本模组给它加竖形态。 */
    public static boolean allows(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        for (Entry entry : entries()) {
            if (entry.matches(block, id)) {
                return entry.allow();
            }
        }
        return true;
    }

    /** 解析一行；写错了就打印一条警告并忽略这一行（不让玩家因为一个笔误整个名单失效）。 */
    private static Entry parse(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.length() < 2) {
            LOGGER.warn("[原版精修] 方块名单条目 \"{}\" 太短，已忽略（应形如 +* / -minecraft:oak_slab / +mod:* / +#minecraft:wooden_slabs）", raw);
            return null;
        }
        boolean allow;
        if (line.charAt(0) == '+') {
            allow = true;
        } else if (line.charAt(0) == '-') {
            allow = false;
        } else {
            LOGGER.warn("[原版精修] 方块名单条目 \"{}\" 必须以 + 或 - 开头，已忽略", raw);
            return null;
        }

        String body = line.substring(1);
        if (body.equals("*")) {
            return new Entry(allow, Kind.GLOBAL, "*", null);
        }
        if (body.startsWith("#")) {
            String tagName = body.substring(1);
            try {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagName));
                return new Entry(allow, Kind.BLOCK_TAG, tagName, tag);
            } catch (RuntimeException invalid) {
                LOGGER.warn("[原版精修] 方块名单里的标签 \"{}\" 不合法，已忽略", tagName);
                return null;
            }
        }
        if (body.endsWith(":*")) {
            String mod = body.substring(0, body.length() - 2);
            if (mod.isEmpty() || mod.contains(":")) {
                LOGGER.warn("[原版精修] 方块名单里的模组 \"{}\" 不合法，已忽略", body);
                return null;
            }
            return new Entry(allow, Kind.MOD_ID, mod, null);
        }
        try {
            ResourceLocation.parse(body);
            return new Entry(allow, Kind.BLOCK_ID, body, null);
        } catch (RuntimeException invalid) {
            LOGGER.warn("[原版精修] 方块名单里的方块 ID \"{}\" 不合法，已忽略", body);
            return null;
        }
    }
}
