package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
import com.example.myfirstmod.util.SlabOffset;
import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 统一修正“放在下台阶上的下移方块（火把/灵魂火把/红石火把/灯笼/栅栏/墙）”的碰撞判定。
 *
 * <p>背景：交互瞄准（OUTLINE）走的是 {@code getShape}，所以“舒适框”仍并入
 * {@code getShape} 才能点得到；但碰撞/遮挡/视觉形状默认继承 {@code getCollisionShape}。
 * 而下台阶本身是实心方块（会复用 {@code getShape}），于是舒适框也一并变成碰撞，
 * 导致火把/灯笼挂在下台阶上时上半格被一道看不见的“柱子”挡住。
 *
 * <p>这里在 {@code BlockBehaviour.getCollisionShape}（唯一的声明处，各子类都未覆写）
 * 统一守卫：
 * <ul>
 *   <li>普通水平下半台阶 + 上方下移方块：碰撞形状还原为不带舒适框的下半台阶，
 *       并把上方方块<b>自己的碰撞形状</b>换算到本格一起并入（下方注释里说明了原因：
 *       投掷物走射线判定，只查射线穿过的那一格，位移到本格的那截必须挂在本格上）；</li>
 *   <li>火把类（ON_SLAB_TORCH）下移/上移：碰撞形状置空（只保留 {@code getShape} 交互）；
 *       灯笼/栅栏/墙是需要阻挡的方块，仍保留物理碰撞。</li>
 * </ul>
 */
@Mixin(BlockBehaviour.class)
public abstract class OnSlabCollisionMixin {

    /** 普通水平下半台阶自身的碰撞形状（不含并入的舒适框）。 */
    private static final VoxelShape SHAPE_LOWER_HALF = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        // 普通水平下半台阶上方挂着下移方块（火把/灯笼/栅栏/墙）：
        // 去掉并入的“舒适框”（那是交互形状，火把之类不该挡住玩家），只保留下半台阶自身；
        // 再把上方方块自己的碰撞形状换算到本格并进来。
        //
        // 为什么必须并：玩家走动走的是按包围盒逐格取形状（BlockCollisions），实体包围盒本来就盖住
        // 上下两格，上方方块位移后的碰撞能查到；但箭矢之类的投掷物走的是射线判定
        // （ProjectileUtil 用 ClipContext.Block.COLLIDER 做 clip，也就是逐格 DDA），
        // 射线只检查它真正穿过的那一格，位移到下面半格的那截形状挂在上面那格上就查不到，
        // 箭矢会直接穿过"看起来有形体"的那半格。并进来之后，两者看到的是同一套箱子。
        if (state.getBlock() instanceof SlabBlock
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && SlabSupport.isLoweredOnSlabAbove(level, pos)) {
            BlockState above = level.getBlockState(pos.above());
            VoxelShape aboveCollision = SlabSupport.fromAboveShape(above,
                    above.getCollisionShape(level, pos.above(), context));
            cir.setReturnValue(SlabSupport.mergedWithNeighbour(SHAPE_LOWER_HALF, aboveCollision));
            return;
        }

        // 位移了半格的火把类（btsdhz_on_slab=true，或贴台阶枚举不是 none）：
        // 只保留交互判定（getShape），不参与碰撞。灯笼/栅栏/墙保留物理碰撞。
        if (state.is(ModTags.ON_SLAB_TORCH) && SlabSupport.offset(state) != SlabOffset.NONE) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
