package com.example.myfirstmod.event;

import com.example.myfirstmod.util.FluidType;
import com.example.myfirstmod.util.ModBlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

@EventBusSubscriber(modid = "btsdhz_original")
public class FluidSpreadEvents {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        LevelAccessor level = event.getLevel();
        if (level.isClientSide()) return;

        Set<Direction> sides = event.getNotifiedSides();
        if (sides == null || sides.isEmpty()) return;

        for (Direction dir : sides) {
            BlockPos neighborPos = event.getPos().relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);

            // 含流体方块：台阶，或楼梯（平放/竖放都有 FLUID_TYPE）
            boolean isFluidHolder;
            if (neighbor.getBlock() instanceof SlabBlock) {
                isFluidHolder = true;
            } else if (neighbor.getBlock() instanceof StairBlock) {
                isFluidHolder = neighbor.hasProperty(ModBlockStateProperties.FLUID_TYPE);
            } else {
                isFluidHolder = false;
            }
            if (!isFluidHolder) continue;
            if (!neighbor.hasProperty(ModBlockStateProperties.FLUID_TYPE)) continue;

            FluidType ft = neighbor.getValue(ModBlockStateProperties.FLUID_TYPE);
            if (ft != FluidType.WATER && ft != FluidType.LAVA) continue;

            Fluid fluid = ft == FluidType.WATER ? Fluids.WATER : Fluids.LAVA;
            level.scheduleTick(neighborPos, fluid, fluid.getTickDelay(level));
        }
    }
}