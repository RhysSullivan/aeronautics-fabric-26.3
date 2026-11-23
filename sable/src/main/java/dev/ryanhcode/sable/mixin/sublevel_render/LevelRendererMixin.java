package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 500)
public class LevelRendererMixin {

    // PORT-26.3: LevelRenderer.allChanged became invalidateCompiledGeometry(level, ...), and the renderer no longer holds the level
    // PORT-26.3: at HEAD with a lower priority than Sodium's mixin, because Sodium cancels this method at its head; at
    // TAIL the hook never ran with Sodium and sub-levels kept their old meshes after F3+T. Marking sections dirty first
    // is safe: they compile later in the frame, against the models the reload has already swapped in. The TAIL hook is
    // kept as well, so without Sodium sections are also marked after vanilla's own reset, exactly as before.
    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"))
    private void sable$allChangedHead(final ClientLevel level, final net.minecraft.client.Options options, final net.minecraft.client.Camera camera,
                                      final net.minecraft.client.color.block.BlockColors blockColors, final CallbackInfo ci) {
        this.sable$allChanged(level, options, camera, blockColors, ci);
    }

    @Inject(method = "invalidateCompiledGeometry", at = @At("TAIL"))
    private void sable$allChanged(final ClientLevel level, final net.minecraft.client.Options options, final net.minecraft.client.Camera camera,
                                  final net.minecraft.client.color.block.BlockColors blockColors, final CallbackInfo ci) {
        if (level == null) {
            return;
        }

        dev.ryanhcode.sable.Sable.LOGGER.info("Rebuilding sub-level sections after invalidateCompiledGeometry");
        SubLevelRenderDispatcher.get().rebuild(((ClientSubLevelContainer) ((SubLevelContainerHolder) level).sable$getPlotContainer()).getAllSubLevels());
    }

    // PORT-NOTE(mc26.1): the single-block batch pass (renderAfterSections) was
    // removed with the ShaderInstance pipeline — see VanillaSubLevelRenderDispatcher.
}
