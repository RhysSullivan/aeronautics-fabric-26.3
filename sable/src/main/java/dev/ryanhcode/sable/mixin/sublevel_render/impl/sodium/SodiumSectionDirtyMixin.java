package dev.ryanhcode.sable.mixin.sublevel_render.impl.sodium;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT-26.3: with Sodium, block changes never reach {@code LevelExtractor.setSectionDirty} (Sable's vanilla hook,
 * {@code ViewAreaMixin}): Sodium overwrites {@code setBlockDirty}, {@code setBlocksDirty} and the section methods to go
 * straight to {@link SodiumWorldRenderer#scheduleRebuildForChunk}. A section inside the plot grid was then never marked
 * dirty, so a ship's sections kept their old meshes after a block on the ship changed: a block placed on an assembled
 * ship was not drawn, a broken one stayed drawn, until the ship's sections were rebuilt from scratch (chunk reload,
 * relog). Here the plot grid's sections go to their sub-level's render data, as the vanilla hook does.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumSectionDirtyMixin {
    @Shadow
    private @Nullable ClientLevel level;

    @Inject(method = "scheduleRebuildForChunk", at = @At("HEAD"), cancellable = true)
    private void sable$scheduleSubLevelRebuild(final int sectionX, final int sectionY, final int sectionZ, final boolean playerChanged,
                                               final CallbackInfo ci) {
        if (this.level == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) {
            return;
        }
        final LevelPlot plot = container.getPlot(sectionX, sectionZ);
        if (plot == null) {
            return;
        }
        ((ClientSubLevel) plot.getSubLevel()).getRenderData().setDirty(sectionX, sectionY, sectionZ, playerChanged);
        ci.cancel();
    }
}
