package com.example.myfirstmod.client.model;

import com.example.myfirstmod.util.SlabSupport;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 栅栏“台阶侧”横杆修正模型。
 *
 * <p>背景：原版栅栏的两根横杆在 y=6~9 与 12~15（间距 6/16 格），而贴台阶的位移是 8/16 格，
 * 所以高低不同的两根栅栏，横杆必然错 2/16 格、怎么摆都对不齐。
 *
 * <p>修正规则（与像素示意图一致，只改“台阶侧”那半根横杆）：
 * <ul>
 *     <li>我比邻居高一档（邻居更低）：只保留下横杆，并整体下移 2/16 格（上横杆不画）；</li>
 *     <li>我比邻居低一档（邻居更高）：下横杆下移 2/16 格，上横杆保留。</li>
 * </ul>
 * 这样我下移后的下横杆与对方的（未位移的）上横杆正好落在同一世界高度，跨台阶处接成一根横杆；
 * 另一侧同理，于是整条“台阶式栅栏”的横杆高度是一致的。
 *
 * <p>“哪一侧是台阶侧、我是高的一侧还是低的一侧”要看邻居，属于按位置变化的信息；塞进方块状态会让
 * 状态量爆炸。这里借用 NeoForge 的 {@code BakedModel#getModelData(level, pos, state, data)}：
 * 区块构建时会带着世界和坐标调用它，我们在这里算好台阶侧，再在 {@code getQuads} 里改横杆。
 */
public class FenceStepModel extends BakedModelWrapper<BakedModel> {

    /** 每个水平方向占 2 bit：0=不是台阶侧，1=我更高（隐藏上横杆+下横杆下移），2=我更低（仅下横杆下移）。 */
    private static final ModelProperty<Integer> STEP = new ModelProperty<>();

    /** 与 {@link #computeStepPattern} 中的顺序一致：北、东、南、西。 */
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final float EPS = 1.0F / 32.0F;
    /** 横杆下移量：2/16 格。 */
    private static final float BAR_SHIFT = -2.0F / 16.0F;
    /**
     * 下横杆贴图改用上横杆那一段：原版两根横杆的贴图行不同——下横杆侧面用 uv 的 v=7~10、
     * 上横杆用 v=1~4，差 6/16 个贴图（不是 6/16 个图集）。上/下两个面两根横杆用的本来就是同一段 uv。
     */
    private static final float BAR_UV_ROWS = -6.0F / 16.0F;
    /** 下横杆 y 6~9、上横杆 y 12~15。 */
    private static final float LOWER_MIN = 6.0F / 16.0F - EPS;
    private static final float LOWER_MAX = 9.0F / 16.0F + EPS;
    private static final float UPPER_MIN = 12.0F / 16.0F - EPS;
    private static final float UPPER_MAX = 15.0F / 16.0F + EPS;
    /** 横杆厚度（另一个水平轴）7~9。 */
    private static final float BAR_THICK_MIN = 7.0F / 16.0F - EPS;
    private static final float BAR_THICK_MAX = 9.0F / 16.0F + EPS;
    /** 半根横杆的远端：北/西到 z/x=9/16，南/东从 7/16 起。 */
    private static final float HALF_END = 9.0F / 16.0F + EPS;
    private static final float HALF_START = 7.0F / 16.0F - EPS;

    public FenceStepModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        int pattern = computeStepPattern(level, pos, state);
        return pattern == 0 ? modelData : modelData.derive().with(STEP, pattern).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData modelData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, random, modelData, renderType);
        Integer pattern = modelData.get(STEP);
        if (pattern == null || pattern == 0 || quads.isEmpty()) {
            return quads;
        }
        return adjustBars(quads, pattern);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        // 没有世界上下文（物品栏、破坏覆盖层等）：保持原版外观
        return originalModel.getQuads(state, side, random);
    }

    /**
     * 算出这一格四个水平方向的台阶侧：0=不是，1=我更高（邻居更低），2=我更低（邻居更高）。
     * 比较用的是栅栏底面（横杆起点）的世界高度——跨台阶连接的两根栅栏并不在同一行。
     *
     * <p>先看同一行的那一侧；那里不是栅栏时再看斜上方/斜下方（跨台阶连接的两根栅栏就是这样斜着相邻的）。
     * 区块渲染区域只覆盖 3×3 区块，越界查询会抛异常，所以整体兜底：拿不到就当作没有台阶侧（保持原版外观）。
     */
    private static int computeStepPattern(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        try {
            double selfHeight = fenceBaseHeight(state, pos.getY());
            int pattern = 0;
            for (int i = 0; i < HORIZONTAL.length; i++) {
                Direction dir = HORIZONTAL[i];
                BlockPos sameRow = pos.relative(dir);
                BlockPos neighbourPos = findFence(level, sameRow, sameRow.above(), sameRow.below());
                if (neighbourPos == null) {
                    continue;
                }
                double neighbourHeight = fenceBaseHeight(level.getBlockState(neighbourPos), neighbourPos.getY());
                int kind = 0;
                if (neighbourHeight < selfHeight - 1.0E-4) {
                    kind = 1;
                } else if (neighbourHeight > selfHeight + 1.0E-4) {
                    kind = 2;
                }
                if (kind != 0) {
                    pattern |= kind << (i * 2);
                }
            }
            return pattern;
        } catch (RuntimeException e) {
            // 越界（区块渲染区域边缘）等情况：退回原版外观
            return 0;
        }
    }

    /** 在同一行、斜上方、斜下方三个位置里找第一个栅栏。 */
    @Nullable
    private static BlockPos findFence(BlockAndTintGetter level, BlockPos sameRow, BlockPos above, BlockPos below) {
        if (level.getBlockState(sameRow).is(BlockTags.FENCES)) {
            return sameRow;
        }
        if (level.getBlockState(above).is(BlockTags.FENCES)) {
            return above;
        }
        if (level.getBlockState(below).is(BlockTags.FENCES)) {
            return below;
        }
        return null;
    }

    /** 栅栏底面（横杆起点）的世界高度：上台阶下的栅栏高半格，下台阶上的栅栏低半格，其余按格子。 */
    private static double fenceBaseHeight(BlockState state, int y) {
        if (SlabSupport.isOnSlab(state)) {
            return y - 0.5;
        }
        return SlabSupport.isUnderTopSlab(state) ? y + 0.5 : y;
    }

    /** 按台阶侧规则改横杆：下横杆下移 2/16；我更高时上横杆不画。 */
    private static List<BakedQuad> adjustBars(List<BakedQuad> quads, int pattern) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            Bar bar = classifyBar(quad);
            if (bar == null) {
                out.add(quad);
                continue;
            }
            int kind = (pattern >> (bar.direction() * 2)) & 3;
            if (kind == 0) {
                out.add(quad);
            } else if (bar.upper()) {
                if (kind != 1) {
                    out.add(quad);
                }
            } else {
                out.add(shiftDown(quad));
            }
        }
        return out;
    }

    /** 判断这个 quad 属于哪一侧的横杆（上/下）。不属于任何横杆返回 null。 */
    @Nullable
    private static Bar classifyBar(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(vertices[i * stride]);
            float y = Float.intBitsToFloat(vertices[i * stride + 1]);
            float z = Float.intBitsToFloat(vertices[i * stride + 2]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        boolean lower = minY >= LOWER_MIN && maxY <= LOWER_MAX;
        boolean upper = minY >= UPPER_MIN && maxY <= UPPER_MAX;
        if (!lower && !upper) {
            return null;
        }
        boolean thickX = minX >= BAR_THICK_MIN && maxX <= BAR_THICK_MAX;
        boolean thickZ = minZ >= BAR_THICK_MIN && maxZ <= BAR_THICK_MAX;
        if (thickX) {
            if (maxZ <= HALF_END) {
                return new Bar(0, upper);           // 北
            }
            if (minZ >= HALF_START) {
                return new Bar(2, upper);           // 南
            }
        }
        if (thickZ) {
            if (maxX <= HALF_END) {
                return new Bar(3, upper);           // 西
            }
            if (minX >= HALF_START) {
                return new Bar(1, upper);           // 东
            }
        }
        return null;
    }

    /**
     * 顶点整体下移 2/16 格，并把这根下横杆的贴图换成上横杆那一段。
     *
     * <p>只改位置不动贴图的话，位移后的横杆贴图会和它对接的上横杆对不上（会看到一条贴图接缝）；
     * 侧面（法线水平的那四个面，含端头）把贴图行往上挪 6/16 正好落到上横杆那一段，上/下两个面
     * 在原版里两根横杆用的就是同一段 uv，所以不动。
     *
     * <p>注意顶点里存的 uv 是“图集坐标”——{@code FaceBakery} 写顶点时已经过了一遍
     * {@code sprite.getU/getV}，所以要按这张 sprite 在图集里的高度换算 6/16，不能直接减 6/16。
     */
    private static BakedQuad shiftDown(BakedQuad quad) {
        int[] src = quad.getVertices();
        int stride = src.length / 4;
        int[] copy = src.clone();
        boolean sideFace = quad.getDirection().getAxis().isHorizontal();
        TextureAtlasSprite sprite = quad.getSprite();
        float atlasVShift = sideFace
                ? (sprite.getV1() - sprite.getV0()) * BAR_UV_ROWS
                : 0.0F;
        for (int i = 0; i < 4; i++) {
            int positionY = i * stride + 1;
            copy[positionY] = Float.floatToRawIntBits(Float.intBitsToFloat(copy[positionY]) + BAR_SHIFT);
            if (sideFace) {
                int uvV = i * stride + 5;
                copy[uvV] = Float.floatToRawIntBits(Float.intBitsToFloat(copy[uvV]) + atlasVShift);
            }
        }
        return new BakedQuad(copy, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    /** 横杆所属方向（{@link #HORIZONTAL} 下标）与是不是上横杆。 */
    private record Bar(int direction, boolean upper) {
    }
}
