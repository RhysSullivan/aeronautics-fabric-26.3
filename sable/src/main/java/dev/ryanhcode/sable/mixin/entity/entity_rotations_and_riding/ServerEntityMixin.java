package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {

    @Shadow private List<Entity> lastPassengers;

    @Shadow @Final private Entity entity;

    // PORT-26.3: the passenger packet is now sent with sendToTrackingPlayers (first call in sendChanges)
    @Inject(method = "sendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity$Synchronizer;sendToTrackingPlayers(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0))
    private void sable$beforeSendChanges(final CallbackInfo ci) {
        final List<Entity> passengers = this.entity.getPassengers();

        if (Sable.HELPER.getContaining(this.entity) != null) {
            this.lastPassengers = passengers;
        }
    }
}
