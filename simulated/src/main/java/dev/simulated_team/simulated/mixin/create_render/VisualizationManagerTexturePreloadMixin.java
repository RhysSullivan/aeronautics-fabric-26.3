package dev.simulated_team.simulated.mixin.create_render;

import com.zurrtum.create.client.flywheel.api.backend.RenderContext;
import com.zurrtum.create.client.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.simulated_team.simulated.mixin_interface.create_render.FlywheelTextureQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PORT-26.3: loads the textures {@link FlywheelTexturePreloadMixin} queued, at the start of a level render (no pass open). */
@Mixin(value = VisualizationManagerImpl.class, remap = false)
public class VisualizationManagerTexturePreloadMixin {
    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void simulated$preloadQueuedTextures(final RenderContext context, final CallbackInfo ci) {
        Identifier id;
        while ((id = FlywheelTextureQueue.QUEUE.poll()) != null) {
            Minecraft.getInstance().getTextureManager().getTexture(id);
        }
    }
}
