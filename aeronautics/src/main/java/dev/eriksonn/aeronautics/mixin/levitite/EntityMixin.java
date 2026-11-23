package dev.eriksonn.aeronautics.mixin.levitite;

import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
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

    @Inject(method = "isEyeInFluid", at = @At("RETURN"), cancellable = true)
    private void aeronautics$eyeInLevitite(final TagKey<Fluid> fluidTag,
                                            final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !FluidTags.WATER.equals(fluidTag)) {
            return;
        }

        final Entity entity = (Entity) (Object) this;
        final double eyeY = entity.getEyeY();
        final BlockPos eyePos = BlockPos.containing(entity.getX(), eyeY, entity.getZ());
        final FluidState fluidState = entity.level().getFluidState(eyePos);
        if (fluidState.is(AeroTags.FluidTags.LEVITITE_BLEND)
                && eyeY < eyePos.getY() + fluidState.getHeight(entity.level(), eyePos)) {
            // Vanilla uses this query both for drowning and for the HUD air meter.
            cir.setReturnValue(true);
        }
    }
}
