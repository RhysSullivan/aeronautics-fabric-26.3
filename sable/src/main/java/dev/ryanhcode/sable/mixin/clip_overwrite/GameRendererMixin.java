package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Makes sub-levels raycast against their render poses while picking.
 */
// PORT-26.3: picking moved from GameRenderer.pick(F)V to Minecraft.pick(F)V
@Mixin(Minecraft.class)
public class GameRendererMixin {

    @WrapMethod(method = "pick(F)V")
    private void sable$pickWithRenderPoses(final float partialTick, final Operation<Void> original) {
        final Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null) {
            original.call(partialTick);
            return;
        }
        final LevelPoseProviderExtension extension = (LevelPoseProviderExtension) minecraft.level;

        extension.sable$pushPoseSupplier(subLevel -> ((ClientSubLevel) subLevel).renderPose(partialTick));
        original.call(partialTick);
        extension.sable$popPoseSupplier();
    }
}
