package dev.simulated_team.simulated.compat.minecraft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * PORT-26.3: {@code Font.drawInBatch} and {@code drawInBatch8xOutline} are gone; text is prepared and each glyph
 * draws itself into the buffer of its own render type. Same output, written against the compat buffer source.
 */
public final class FontBuffers {
    private FontBuffers() {
    }

    public static void drawInBatch(final Font font, final FormattedCharSequence text, final float x, final float y,
                                   final int color, final boolean shadow, final Matrix4f pose,
                                   final MultiBufferSource buffers, final Font.DisplayMode mode,
                                   final int backgroundColor, final int light) {
        visit(font.prepareText(text, x, y, color, shadow, false, backgroundColor), pose, buffers, mode, light);
    }

    public static void drawInBatch8xOutline(final Font font, final FormattedCharSequence text, final float x,
                                            final float y, final int color, final int outlineColor,
                                            final Matrix4f pose, final MultiBufferSource buffers, final int light) {
        visit(font.prepare8xTextOutline(text, x, y, outlineColor), pose, buffers, Font.DisplayMode.NORMAL, light);
        visit(font.prepareText(text, x, y, color, false, false, 0), pose, buffers, Font.DisplayMode.POLYGON_OFFSET, light);
    }

    private static void visit(final Font.PreparedText prepared, final Matrix4f pose, final MultiBufferSource buffers,
                              final Font.DisplayMode mode, final int light) {
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptRenderable(final TextRenderable renderable) {
                renderable.render(pose, buffers.getBuffer(renderable.renderType(mode)), light, false);
            }
        });
    }
}
