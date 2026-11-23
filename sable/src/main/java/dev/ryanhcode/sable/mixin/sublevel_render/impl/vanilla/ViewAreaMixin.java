package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes view area calls redirect to data renderers
 */
// PORT-26.3: section dirtiness moved from ViewArea.setDirty to LevelExtractor.setSectionDirty (same arguments)
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
public class ViewAreaMixin {

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void sable$setDirty(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final SubLevelContainer plotContainer = ((SubLevelContainerHolder) Minecraft.getInstance().level).sable$getPlotContainer();
        final LevelPlot plot = plotContainer.getPlot(x, z);

        if (plot != null) {
            (((ClientSubLevel) plot.getSubLevel()).getRenderData()).setDirty(x, y, z, playerChanged);
            ci.cancel();
        }
    }
}
