package dev.ryanhcode.sable.mixin.fluids_on_sub_levels;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes fluids refuse to flow off the edge of sub-levels
 */
@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {


    /**
     * PORT-26.3: fluids also never leave the plot's bounds, downwards included. On 26.3 a fluid falls through
     * {@code canMaybePassThrough} (sideways spreading still goes through {@code canPassThrough}, checked below), so water
     * that reached a cell above open air fell out of the bottom of the ship and down the whole plot. Each block of that
     * falling column expanded the plot's bounds: the ship's world bounds reached far below it, its sky-light sample (taken at
     * the bounds' centre) was underground, and the whole ship, hull and block entities, was drawn without sky light.
     */
    @Inject(at = @At("HEAD"), method = "canMaybePassThrough", cancellable = true)
    private void sable$stayInPlot(final BlockGetter getter, final BlockPos fromPos, final BlockState fromState, final Direction direction,
                                  final BlockPos toPos, final BlockState toState, final FluidState toFluidState, final CallbackInfoReturnable<Boolean> cir) {
        if (!(getter instanceof final Level level)) {
            return;
        }
        final SubLevel subLevel = Sable.HELPER.getContaining(level, Vec3.atCenterOf(fromPos));
        if (subLevel != null && !subLevel.getPlot().getBoundingBox().contains(toPos.getX(), toPos.getY(), toPos.getZ())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(at = @At("HEAD"), method = "canPassThrough", cancellable = true)
    private void sable$canPassThrough(final BlockGetter pLevel, final Fluid pFluid, final BlockPos pFromPos, final BlockState pFromBlockState, final Direction pDirection, final BlockPos pToPos, final BlockState pToBlockState, final FluidState pToFluidState, final CallbackInfoReturnable<Boolean> cir) {
        if (!(pLevel instanceof final Level level)) {
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, Vec3.atCenterOf(pToPos));

        if (subLevel != null) {
            BlockPos mut = pToPos;
            boolean ableToFlow = false;

            while (subLevel.getPlot().getBoundingBox().contains(mut.getX(), mut.getY(), mut.getZ())) {
                mut = mut.below();

                if (mut.getY() < 0 || !pLevel.getBlockState(mut).isAir()) {
                    ableToFlow = true;
                    break;
                }
            }

            if (!ableToFlow) {
                cir.setReturnValue(false);
            }
        }
    }

}
