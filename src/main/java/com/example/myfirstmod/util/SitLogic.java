package com.example.myfirstmod.util;

import com.example.myfirstmod.ModEntities;
import com.example.myfirstmod.entity.SitEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 服务端坐/起逻辑:坐在一个静止的隐形座位上。 */
public final class SitLogic {
    private SitLogic() {
    }

    public static void toggleSit(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle instanceof SitEntity) {
            // 已在座位上:下来并移除座位。
            player.stopRiding();
            vehicle.discard();
            return;
        }

        // 已在其它载具(矿车、船等)上,不做处理。
        if (player.isPassenger()) {
            return;
        }

        if (player.isDeadOrDying() || player.isSpectator()) {
            return;
        }

        Level level = player.level();
        SitEntity seat = new SitEntity(ModEntities.SIT.get(), level);
        seat.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(seat);
        player.startRiding(seat, true);
    }
}
