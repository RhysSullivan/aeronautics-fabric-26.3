package dev.simulated_team.simulated.content.entities.diagram.screen.preview;

import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramFramebuffer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** PORT-26.3: a diagram preview to draw at x0..x1, y0..y1 (GUI pixels) with the GUI pose at submission. */
public record DiagramPreviewRenderState(DiagramFramebuffer fbo, Matrix3x2f pose, int x0, int y0, int x1, int y1,
                                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
    public DiagramPreviewRenderState(final DiagramFramebuffer fbo, final Matrix3x2f pose, final int x0, final int y0, final int x1, final int y1,
                                     final @Nullable ScreenRectangle scissorArea) {
        this(fbo, pose, x0, y0, x1, y1, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public float scale() {
        return 1.0F;
    }
}
