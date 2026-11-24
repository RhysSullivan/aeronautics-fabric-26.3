package dev.ryanhcode.sable.mixin.rider_weight;

import dev.ryanhcode.sable.sublevel.RiderWeight;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * PORT-26.3 addition (rider weight): records the on-ground flag of each move packet the server applies, and forgets it
 * when the server moves the player itself.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerRiderMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * On the server thread (after {@code ensureRunningOnSameThread}, in the method Sable's own move-packet hook is in),
     * just before vanilla applies the move: packets vanilla drops (invalid values, client not loaded, a teleport not yet
     * confirmed) are not recorded.
     */
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handlePlayerPositionChange(DDDFFZZ)V"))
    private void sable$recordClientOnGround(final ServerboundMovePlayerPacket packet, final CallbackInfo ci) {
        ((RiderWeight.ClientGround) this.player).sable$setClientOnGround(packet.isOnGround());
    }

    /** Every server-side move of a player (commands, dimension change, respawn, a rejected move sent back) ends here. */
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void sable$forgetClientOnGround(final PositionMoveRotation destination, final Set<Relative> relatives, final CallbackInfo ci) {
        ((RiderWeight.ClientGround) this.player).sable$setClientOnGround(false);
    }
}
