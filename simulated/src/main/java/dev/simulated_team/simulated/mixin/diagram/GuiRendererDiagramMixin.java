package dev.simulated_team.simulated.mixin.diagram;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.simulated_team.simulated.content.entities.diagram.screen.preview.DiagramPreviewRenderState;
import dev.simulated_team.simulated.content.entities.diagram.screen.preview.DiagramPreviewRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PORT-26.3: registers the diagram preview's picture-in-picture renderer, as Create Fly registers its own. */
@Mixin(GuiRenderer.class)
public class GuiRendererDiagramMixin {
    @WrapOperation(method = "<init>", require = 1, at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap;builder()Lcom/google/common/collect/ImmutableMap$Builder;", remap = false))
    private ImmutableMap.Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> simulated$addDiagramPreview(
            final Operation<ImmutableMap.Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>>> original) {
        final ImmutableMap.Builder<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> builder = original.call();
        builder.put(DiagramPreviewRenderState.class, new DiagramPreviewRenderer());
        return builder;
    }
}
