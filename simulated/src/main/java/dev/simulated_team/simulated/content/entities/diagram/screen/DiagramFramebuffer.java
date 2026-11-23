package dev.simulated_team.simulated.content.entities.diagram.screen;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.jetbrains.annotations.Nullable;

/**
 * PORT-26.3: what one diagram preview shows (the 1.21 preview drew into Veil framebuffers; Veil has no 26.x build).
 * {@code DiagramScreen.draw} records the view here at the preview's frame rate; {@code DiagramScreen.renderFBO} hands
 * it to {@link dev.simulated_team.simulated.content.entities.diagram.screen.preview.DiagramPreviewRenderer}, which
 * draws the ship into its own textures through 26.3's picture-in-picture GUI renderer and runs the outline pass.
 * The instance is the key for that renderer's textures, freed with {@link #free()}.
 */
public final class DiagramFramebuffer {
    public final int width;
    public final int height;
    public @Nullable ClientSubLevel subLevel;
    /** The preview camera in plot coordinates, and the rotation from plot axes to view axes (looking down -z, y up). */
    public final org.joml.Vector3d cameraLocal = new org.joml.Vector3d();
    public final org.joml.Quaternionf viewRotation = new org.joml.Quaternionf();
    public float radius;
    /** Distance from the camera to the ship's centre (the projections put their far plane at twice that). */
    public float cameraDistance;
    public float paletteOffset;
    public float fadeScale;
    public int lineColor;
    public int lineShadowColor;
    /** Set when the view changed (the screens redraw at their own frame rate); the renderer re-blits otherwise. */
    public boolean dirty;
    public boolean freed;

    private DiagramFramebuffer(final int width, final int height) {
        this.width = width;
        this.height = height;
    }

    public static DiagramFramebuffer create(final int width, final int height) {
        return new DiagramFramebuffer(width, height);
    }

    public void free() {
        this.freed = true;
        this.subLevel = null;
    }
}
