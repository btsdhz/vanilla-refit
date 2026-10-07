package com.example.myfirstmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.example.myfirstmod.config.Blocklist;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
import com.example.myfirstmod.util.PlacementMode;
import com.example.myfirstmod.util.PlacementModeState;
import com.example.myfirstmod.util.StairConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StairBlock.class)
public abstract class StairBlockMixin {

    // ===== 1. 注册属性 =====
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.VERTICAL);
        builder.add(ModBlockStateProperties.FLUID_TYPE);
    }

    // ===== 2. 放置逻辑 =====
    // 用 @ModifyReturnValue 而不是 RETURN + cancel：放置结果是"改"出来的，不是"取消"出来的，
    // 别的模组同时也改这个方法时两边能依次叠加上去，不会互相顶掉。
    @ModifyReturnValue(method = "getStateForPlacement", at = @At("RETURN"), remap = false)
    private BlockState btsdhz_original$getStateForPlacement(BlockState original,
                                                            @Local(argsOnly = true) BlockPlaceContext context) {
        if (original == null) {
            return null;
        }
        // 该玩家的楼梯放置逻辑切到了原版：不套用竖楼梯状态。
        if (PlacementModeState.stair(context.getPlayer()) == PlacementMode.VANILLA) {
            return original;
        }
        // 名单里被禁用的方块：完全按原版放置，连连接状态都不写（见 config/Blocklist）
        if (!Blocklist.allows((Block) (Object) this)) {
            return original;
        }
        // 其它模组的楼梯：没有对应的竖楼梯模型，保持原版行为（见 SlabSupport.isSupportedStair）
        if (!SlabSupport.isSupportedStair((Block) (Object) this)) {
            return original;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        FluidState fluidState = level.getFluidState(pos);
        FluidType fluidType = FluidType.NONE;
        if (fluidState.getType() == Fluids.WATER) {
            fluidType = FluidType.WATER;
        } else if (fluidState.getType() == Fluids.LAVA) {
            fluidType = ModTags.canHoldFluid(original, FluidType.LAVA) ? FluidType.LAVA : FluidType.NONE;
        }

        BlockState result;
        if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
            Direction facing = getFacingFromQuadrant(context);
            StairConnection conn = getConnection(level, pos, facing);
            result = original
                    .setValue(ModBlockStateProperties.VERTICAL, true)
                    .setValue(StairBlock.FACING, facing)
                    .setValue(StairBlock.HALF, getConnectionHalf(level, pos, facing, conn))
                    .setValue(BlockStateProperties.WATERLOGGED, false)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluidType)
                    // 连接形态借用原版 shape（5 个值当载体，见 StairConnection）
                    .setValue(StairBlock.SHAPE, conn.toShape());
        } else {
            result = original
                    .setValue(ModBlockStateProperties.VERTICAL, false)
                    .setValue(BlockStateProperties.WATERLOGGED, false)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluidType);
        }

        if (fluidType != FluidType.NONE && !level.isClientSide()) {
            net.minecraft.world.level.material.Fluid f = fluidType == FluidType.WATER ? Fluids.WATER : Fluids.LAVA;
            level.scheduleTick(pos, f, f.getTickDelay(level));
        }
        return result;
    }

    // ===== 3. 邻居变化时重算连接 =====
    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        if (state.hasProperty(ModBlockStateProperties.VERTICAL)
                && state.getValue(ModBlockStateProperties.VERTICAL)
                && level instanceof Level lvl) {
            Direction f = state.getValue(StairBlock.FACING);
            StairConnection conn = getConnection(lvl, currentPos, f);
            cir.setReturnValue(state
                    .setValue(StairBlock.SHAPE, conn.toShape())
                    .setValue(StairBlock.HALF, getConnectionHalf(lvl, currentPos, f, conn)));
            cir.cancel();
        }
    }

    // ===== 4. 碰撞箱（由 getLShape 推导，与渲染严格一致） =====
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.VERTICAL) && state.getValue(ModBlockStateProperties.VERTICAL)) {
            Direction facing = state.getValue(StairBlock.FACING);
            StairConnection conn = StairConnection.of(state);
            Half half = state.getValue(StairBlock.HALF);
            VoxelShape shape = switch (conn) {
                case CONN_RIGHT, CONN_LEFT, CONN_DOUBLE -> getConnShape(facing, conn, half);
                case CORNER -> getCornerShape(facing, half);
                default -> getLShape(facing);
            };
            cir.setReturnValue(shape);
            cir.cancel();
        }
    }

    // ===== 5. 流体状态 =====
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getFluidState(BlockState state, CallbackInfoReturnable<FluidState> cir) {
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            FluidType ft = state.getValue(ModBlockStateProperties.FLUID_TYPE);
            if (ft == FluidType.WATER) {
                cir.setReturnValue(Fluids.WATER.getSource(false));
                cir.cancel();
                return;
            } else if (ft == FluidType.LAVA) {
                cir.setReturnValue(Fluids.LAVA.getSource(false));
                cir.cancel();
                return;
            }
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            cir.setReturnValue(Fluids.WATER.getSource(false));
            cir.cancel();
        }
    }

    // ===== 6. 点击象限 → FACING（点击处 = L 的中心） =====
    @Unique
    private Direction getFacingFromQuadrant(BlockPlaceContext context) {
        double x = context.getClickLocation().x - context.getClickedPos().getX();
        double z = context.getClickLocation().z - context.getClickedPos().getZ();
        boolean east = x >= 0.5;
        boolean south = z >= 0.5;
        if (!east && !south) return Direction.NORTH; // 中心 NW
        if (east && !south)  return Direction.EAST;  // 中心 NE
        if (east && south)   return Direction.SOUTH; // 中心 SE
        return Direction.WEST;                       // 中心 SW
    }

    // ===== 7. 竖楼梯形状表（只由 FACING / 连接形态 / 上下半决定，全部预计算） =====
    // getShape 是碰撞与准星射线的高频路径，这里避免每次查询都重新 Shapes.or。
    @Unique
    private static final Direction[] HORIZONTAL_FACINGS =
            { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    @Unique
    private static final VoxelShape[] L_SHAPES = new VoxelShape[Direction.values().length];
    /** 拐角形状：L 形 + 空角补块，补块在上半还是下半由 half 决定。 */
    @Unique
    private static final VoxelShape[] CORNER_SHAPES =
            new VoxelShape[Direction.values().length * Half.values().length];
    @Unique
    private static final VoxelShape[] CONN_SHAPES = new VoxelShape[Direction.values().length
            * StairConnection.values().length * Half.values().length];

    static {
        for (Direction facing : HORIZONTAL_FACINGS) {
            VoxelShape lShape = buildLShape(facing);
            L_SHAPES[facing.ordinal()] = lShape;
            for (Half half : Half.values()) {
                CORNER_SHAPES[cornerIndex(facing, half)] = Shapes.or(lShape, buildCornerShape(facing, half));
            }
            for (StairConnection conn : StairConnection.values()) {
                for (Half half : Half.values()) {
                    CONN_SHAPES[connIndex(facing, conn, half)] = buildConnShape(facing, conn, half);
                }
            }
        }
    }

    @Unique
    private static int cornerIndex(Direction facing, Half half) {
        return facing.ordinal() * Half.values().length + half.ordinal();
    }

    @Unique
    private static int connIndex(Direction facing, StairConnection conn, Half half) {
        return (facing.ordinal() * StairConnection.values().length + conn.ordinal()) * Half.values().length
                + half.ordinal();
    }

    /** L 形（普通竖楼梯）形状。 */
    @Unique
    private static VoxelShape getLShape(Direction facing) {
        VoxelShape shape = L_SHAPES[facing.ordinal()];
        return shape != null ? shape : L_SHAPES[Direction.NORTH.ordinal()];
    }

    /** L 形 + 空角补块（拐角连接；补块在上半还是下半由 half 决定）。 */
    @Unique
    private static VoxelShape getCornerShape(Direction facing, Half half) {
        VoxelShape shape = CORNER_SHAPES[cornerIndex(facing, half)];
        return shape != null ? shape : CORNER_SHAPES[cornerIndex(Direction.NORTH, half)];
    }

    /** 单/双大面连接的形状。 */
    @Unique
    private static VoxelShape getConnShape(Direction facing, StairConnection conn, Half half) {
        VoxelShape shape = CONN_SHAPES[connIndex(facing, conn, half)];
        return shape != null ? shape : Shapes.empty();
    }

    // ===== 7b. L 形三块（按渲染模型：中心 + 两条臂，空角在对角） =====
    @Unique
    private static VoxelShape buildLShape(Direction facing) {
        switch (facing) {
            case EAST:  // 中心 NE，空 SW，臂 {W,S}
                return Shapes.or(
                        Block.box(0, 0, 0, 8, 16, 8),     // NW
                        Block.box(8, 0, 0, 16, 16, 8),    // NE 中心
                        Block.box(8, 0, 8, 16, 16, 16));  // SE
            case SOUTH: // 中心 SE，空 NW，臂 {N,W}
                return Shapes.or(
                        Block.box(8, 0, 0, 16, 16, 8),    // NE
                        Block.box(8, 0, 8, 16, 16, 16),   // SE 中心
                        Block.box(0, 0, 8, 8, 16, 16));   // SW
            case WEST:  // 中心 SW，空 NE，臂 {E,N}
                return Shapes.or(
                        Block.box(8, 0, 8, 16, 16, 16),   // SE
                        Block.box(0, 0, 8, 8, 16, 16),    // SW 中心
                        Block.box(0, 0, 0, 8, 16, 8));    // NW
            default:    // NORTH: 中心 NW，空 SE，臂 {E,S}
                return Shapes.or(
                        Block.box(0, 0, 8, 8, 16, 16),    // SW
                        Block.box(0, 0, 0, 8, 16, 8),     // NW 中心
                        Block.box(8, 0, 0, 16, 16, 8));   // NE
        }
    }

    // ===== 8. 空角补块（half=BOTTOM 补下半 y0~8，half=TOP 补上半 y8~16） =====
    @Unique
    private static VoxelShape buildCornerShape(Direction facing, Half half) {
        int y0 = (half == Half.BOTTOM) ? 0 : 8;
        int y1 = y0 + 8;
        switch (facing) {
            case EAST:  return Block.box(0, y0, 8, 8, y1, 16);   // 空 SW
            case SOUTH: return Block.box(0, y0, 0, 8, y1, 8);    // 空 NW
            case WEST:  return Block.box(8, y0, 0, 16, y1, 8);   // 空 NE
            default:    return Block.box(8, y0, 8, 16, y1, 16);  // 空 SE
        }
    }

    // ===== 8b. 单/双大面连接碰撞箱（与 vertical_stair_conn_* 模型元素一一对应） =====
    @Unique
    private static VoxelShape buildConnShape(Direction facing, StairConnection conn, Half half) {
        return switch (conn) {
            case CONN_RIGHT -> Shapes.or(
                    rotateBox(facing, half, 0, 0, 0, 16, 16, 8),   // 前段整体
                    rotateBox(facing, half, 8, 0, 8, 16, 8, 16));  // 右下下半
            case CONN_LEFT -> Shapes.or(
                    rotateBox(facing, half, 8, 0, 0, 16, 16, 16),  // 右半整体
                    rotateBox(facing, half, 0, 0, 0, 8, 8, 8));    // 左上下半
            case CONN_DOUBLE -> Shapes.or(
                    rotateBox(facing, half, 8, 0, 0, 16, 16, 8),   // 前右整体
                    rotateBox(facing, half, 0, 0, 0, 8, 8, 8),     // 左上下半
                    rotateBox(facing, half, 8, 0, 8, 16, 8, 16));  // 右下下半
            default -> Shapes.empty();
        };
    }

    // ===== 8c. 把模型默认朝向的包围盒旋转到指定 FACING 的世界朝向 =====
    @Unique
    private static VoxelShape rotateBox(Direction facing, Half half, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (half == Half.TOP) { // 上平楼梯：只做上下翻转（模型本身已是上下镜像后的几何）
            double oy1 = y1;
            y1 = 16 - y2;
            y2 = 16 - oy1;
        }
        double[] p1 = rotY(facing, x1, z1);
        double[] p2 = rotY(facing, x1, z2);
        double[] p3 = rotY(facing, x2, z1);
        double[] p4 = rotY(facing, x2, z2);
        double nx1 = Math.min(p1[0], Math.min(p2[0], Math.min(p3[0], p4[0])));
        double nx2 = Math.max(p1[0], Math.max(p2[0], Math.max(p3[0], p4[0])));
        double nz1 = Math.min(p1[1], Math.min(p2[1], Math.min(p3[1], p4[1])));
        double nz2 = Math.max(p1[1], Math.max(p2[1], Math.max(p3[1], p4[1])));
        return Block.box(nx1, y1, nz1, nx2, y2, nz2);
    }

    // ===== 8d. 绕竖直轴旋转（俯视、北朝上；与 blockstate 的 yaw 一致） =====
    @Unique
    private static double[] rotY(Direction facing, double x, double z) {
        switch (facing) {
            case EAST:  return new double[]{ x,     z     };  // yaw 0
            case SOUTH: return new double[]{ 16 - z, x     };  // yaw 90
            case WEST:  return new double[]{ 16 - x, 16 - z };  // yaw 180
            default:    return new double[]{ z,     16 - x };  // NORTH, yaw 270
        }
    }

    // ===== 9. 臂方向（中心 → 两条臂） =====
    @Unique
    private static Direction[] getArmDirections(Direction facing) {
        switch (facing) {
            case EAST:  return new Direction[]{ Direction.WEST, Direction.SOUTH };
            case SOUTH: return new Direction[]{ Direction.NORTH, Direction.WEST };
            case WEST:  return new Direction[]{ Direction.EAST, Direction.NORTH };
            default:    return new Direction[]{ Direction.EAST, Direction.SOUTH };
        }
    }

    // ===== 10. 连接检测（拐角逻辑优先，未触发才走新的大面连接逻辑） =====
    @Unique
    private StairConnection getConnection(Level level, BlockPos pos, Direction facing) {
        // 旧逻辑：臂方向有平放楼梯 → 拐角（优先，触发即跳过新逻辑）；补块在上半还是下半由 half 决定
        if (cornerSide(level, pos, facing) != null) return StairConnection.CORNER;
        // 新逻辑：两个完整面（facing 与 facing 逆时针90°）有平放楼梯 → conn_*
        Half left = findFlatHalf(level, pos, facing.getCounterClockWise());
        Half right = findFlatHalf(level, pos, facing);
        if (left != null && right != null) return StairConnection.CONN_DOUBLE;
        if (left != null) return StairConnection.CONN_LEFT;
        if (right != null) return StairConnection.CONN_RIGHT;
        return StairConnection.NONE;
    }

    // ===== 10a. 旧逻辑：臂方向平放楼梯给出拐角补块的位置（下半优先） =====
    @Unique
    private static Half cornerSide(Level level, BlockPos pos, Direction facing) {
        boolean hasBottom = false;
        boolean hasTop = false;
        for (Direction arm : getArmDirections(facing)) {
            Half half = findFlatHalf(level, pos, arm);
            if (half == Half.BOTTOM) hasBottom = true;
            else if (half == Half.TOP) hasTop = true;
        }
        if (hasBottom) return Half.BOTTOM;
        if (hasTop) return Half.TOP;
        return null;
    }

    // ===== 10b. 依据连接形态算出竖楼梯应使用的 HALF（下平=底部，上平=翻转） =====
    @Unique
    private static Half getConnectionHalf(Level level, BlockPos pos, Direction facing, StairConnection conn) {
        switch (conn) {
            case CONN_LEFT: {
                Half h = findFlatHalf(level, pos, facing.getCounterClockWise());
                return h != null ? h : Half.BOTTOM;
            }
            case CONN_RIGHT: {
                Half h = findFlatHalf(level, pos, facing);
                return h != null ? h : Half.BOTTOM;
            }
            case CONN_DOUBLE: {
                Half l = findFlatHalf(level, pos, facing.getCounterClockWise());
                Half r = findFlatHalf(level, pos, facing);
                if (l != null && r != null) return l != r ? Half.BOTTOM : l;
                return l != null ? l : (r != null ? r : Half.BOTTOM);
            }
            case CORNER: {
                Half h = cornerSide(level, pos, facing);
                return h != null ? h : Half.BOTTOM;
            }
            default: return Half.BOTTOM;
        }
    }

    // ===== 10c. 该方向邻居是否为平放楼梯，返回其 HALF；不是则返回 null =====
    @Unique
    private static Half findFlatHalf(Level level, BlockPos pos, Direction dir) {
        BlockState n = level.getBlockState(pos.relative(dir));
        if (n.getBlock() instanceof StairBlock
                && n.hasProperty(ModBlockStateProperties.VERTICAL)
                && !n.getValue(ModBlockStateProperties.VERTICAL)
                && n.hasProperty(StairBlock.HALF)) {
            return n.getValue(StairBlock.HALF);
        }
        return null;
    }
}
