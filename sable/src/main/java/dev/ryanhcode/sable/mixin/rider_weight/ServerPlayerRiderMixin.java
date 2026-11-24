package dev.ryanhcode.sable.mixin.rider_weight;

import dev.ryanhcode.sable.sublevel.RiderWeight;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * PORT-26.3 addition (rider weight): the on-ground flag the player's client last sent, for {@link RiderWeight} only.
 * A new player object (login, respawn) starts with false.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRiderMixin implements RiderWeight.ClientGround {

    @Unique
    private boolean sable$clientOnGround;

    @Override
    public boolean sable$clientOnGround() {
        return this.sable$clientOnGround;
    }

    @Override
    public void sable$setClientOnGround(final boolean onGround) {
        this.sable$clientOnGround = onGround;
    }
}
