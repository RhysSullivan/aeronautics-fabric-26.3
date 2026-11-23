package dev.eriksonn.aeronautics.mixin.levitite;

import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Levitite fluid tracking and bridges eye submersion to vanilla air handling. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    // PORT-26.3: fluid heights and currents live in EntityFluidInteraction; vanilla's only carries currents for water
    // and lava, so levitite gets its own interaction, updated and applied where vanilla applies its currents.
    @org.spongepowered.asm.mixin.Unique
    private final net.minecraft.world.entity.EntityFluidInteraction aeronautics$levitite =
            new net.minecraft.world.entity.EntityFluidInteraction(java.util.Set.of(AeroTags.FluidTags.LEVITITE_BLEND));

    @Inject(method = "updateFluidInteraction", at = @At("TAIL"), cancellable = true)
    private void aeronautics$updateLevititeFluid(final CallbackInfoReturnable<Boolean> cir) {
        final Entity entity = (Entity) (Object) this;
        this.aeronautics$levitite.update(entity, !entity.isPushedByFluid());
        if (this.aeronautics$levitite.isInFluid(AeroTags.FluidTags.LEVITITE_BLEND)) {
            if (entity.isPushedByFluid()) {
                // NeoForge's Levitite fluid type uses vanilla's non-ultra-warm lava scale, upward.
                this.aeronautics$levitite.applyCurrentTo(AeroTags.FluidTags.LEVITITE_BLEND, entity, -0.0023333333333333335D);
            }
            cir.setReturnValue(true);
        }
    }

    /**
     * PORT-26.3: the eye-in-levitite state is the one {@link #aeronautics$updateLevititeFluid} stores on the entity, read
     * the same way vanilla reads its own eye-in-water state ({@code fluidInteraction.isEyeInFluid}). This query must never
     * read the level: 26.3 calls it from places that run off the server thread, for example a zombie's conversion tracker
     * when the zombie is saved into a chunk that is being generated on a worker thread. There {@code entity.level()} is
     * the real server level, and a level read asks the server thread for the chunk and waits for it while the server
     * thread waits for that generation: the server froze.
     */
    @Inject(method = "isEyeInFluid", at = @At("RETURN"), cancellable = true)
    private void aeronautics$eyeInLevitite(final TagKey<Fluid> fluidTag,
                                            final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && FluidTags.WATER.equals(fluidTag)
                && this.aeronautics$levitite.isEyeInFluid(AeroTags.FluidTags.LEVITITE_BLEND)) {
            // Vanilla uses this query both for drowning and for the HUD air meter.
            cir.setReturnValue(true);
        }
    }
}
