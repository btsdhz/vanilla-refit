package com.example.myfirstmod.client.model;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/**
 * 从烘焙 quad 的顶点算出它"几何上朝向哪个面"。
 *
 * <p>为什么不能直接用 {@code BakedQuad.getDirection()}：原版烘焙是按 <b>cullface</b> 分桶的，
 * 没写 cullface 的面（例如 {@code twilightforest:aurora_slab} 的 up 面）会被塞进"无剔除"那一桶，
 * 于是 {@code model.getQuads(state, Direction.UP, random)} 会是空的。要判断"这个面到底是哪一面"，
 * 只能自己看顶点。
 */
public final class QuadFacing {

    private QuadFacing() {
    }

    /** @return 该 quad 几何上朝向的面；顶点退化时返回 null。 */
    @Nullable
    public static Direction of(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = IQuadTransformer.STRIDE;
        int base = IQuadTransformer.POSITION;

        float x0 = Float.intBitsToFloat(vertices[base]);
        float y0 = Float.intBitsToFloat(vertices[base + 1]);
        float z0 = Float.intBitsToFloat(vertices[base + 2]);
        float x1 = Float.intBitsToFloat(vertices[base + stride]);
        float y1 = Float.intBitsToFloat(vertices[base + stride + 1]);
        float z1 = Float.intBitsToFloat(vertices[base + stride + 2]);
        float x2 = Float.intBitsToFloat(vertices[base + 2 * stride]);
        float y2 = Float.intBitsToFloat(vertices[base + 2 * stride + 1]);
        float z2 = Float.intBitsToFloat(vertices[base + 2 * stride + 2]);

        float ux = x1 - x0;
        float uy = y1 - y0;
        float uz = z1 - z0;
        float vx = x2 - x0;
        float vy = y2 - y0;
        float vz = z2 - z0;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;

        float ax = Math.abs(nx);
        float ay = Math.abs(ny);
        float az = Math.abs(nz);
        if (ax < 1.0E-6F && ay < 1.0E-6F && az < 1.0E-6F) {
            return null;
        }
        if (ax >= ay && ax >= az) {
            return nx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= ax && ay >= az) {
            return ny > 0 ? Direction.UP : Direction.DOWN;
        }
        return nz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
