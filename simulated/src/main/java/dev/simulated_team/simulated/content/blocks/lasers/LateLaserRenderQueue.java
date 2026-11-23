package dev.simulated_team.simulated.content.blocks.lasers;

import org.joml.Matrix4f;

/**
 * PORT-26.3 STUB: the deferred laser pass injected into the 1.21 frame graph (render-target overrides that 26.3
 * removed). Lasers are drawn inline with their block entity instead; this never collects.
 */
public final class LateLaserRenderQueue {
    private LateLaserRenderQueue() {
    }

    public static void beginWorldFrame() {
    }

    public static boolean isCollectingWorldFrame() {
        return false;
    }

    public static boolean enqueue(final Matrix4f matrix,
                                  final float beamEnd, final float offset, final float endU,
                                  final float red, final float green, final float blue,
                                  final float alpha, final float endAlpha) {
        return false;
    }

    public static void finishWorldFrameCollection() {
    }
}
