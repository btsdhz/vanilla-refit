package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.ModTags;
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
 * 统一修正“放在下台阶上的下移光源（火把/灵魂火把/红石火把/灯笼）”的碰撞判定。
 *
 * <p>背景：交互瞄准（OUTLINE）走的是 {@code getShape}，所以“舒适框”仍并入
 * {@code getShape} 才能点得到；但碰撞/遮挡/视觉形状默认继承 {@code getCollisionShape}。
 * 而下台阶本身是实心方块（会复用 {@code getShape}），于是舒适框也一并变成碰撞，
 * 导致火把/灯笼挂在下台阶上时上半格被一道看不见的“柱子”挡住。
 *
 * <p>这里在 {@code BlockBehaviour.getCollisionShape}（唯一的声明处，各子类都未覆写）
 * 统一守卫：
 * <ul>
 *   <li>普通水平下半台阶 + 上方下移光源：碰撞形状还原为不带舒适框的下半台阶；</li>
 *   <li>任何 ON_SLAB=true 的下移光源：碰撞形状置空（只保留 {@code getShape} 交互）。</li>
 * </ul>
 */
@Mixin(BlockBehaviour.class)
public abstract class OnSlabCollisionMixin {

    /** 普通水平下半台阶自身的碰撞形状（不含并入的舒适框）。 */
    private static final VoxelShape SHAPE_LOWER_HALF = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        // 普通水平下半台阶上方挂着下移火把/灯笼：去掉舒适框，只保留下半台阶的碰撞。
        if (state.getBlock() instanceof SlabBlock
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && SlabSupport.isLoweredTorchOrLanternAbove(level, pos)) {
            cir.setReturnValue(SHAPE_LOWER_HALF);
            return;
        }

        // 下移（ON_SLAB=true）或上移（UNDER_TOP_SLAB=true）的火把/灯笼：
        // 只保留交互判定（getShape），不参与碰撞。
        // 栅栏/墙虽然也用 ON_SLAB 下移，但它们是需要阻挡的屏障，仍保留物理碰撞。
        boolean isSlabLight = state.is(ModTags.ON_SLAB_TORCH) || state.is(ModTags.ON_SLAB_LANTERN);
        boolean loweredOrRaised = (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB))
                || (state.hasProperty(ModBlockStateProperties.UNDER_TOP_SLAB)
                && state.getValue(ModBlockStateProperties.UNDER_TOP_SLAB));
        if (isSlabLight && loweredOrRaised) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
