package dev.simulated_team.simulated.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.simulated_team.simulated.compat.minecraft.MultiBufferSource;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Runs a buffer-style renderer inside 26.3's submit model.
 * <p>
 * PORT-26.3: the renderer runs exactly once per submission, as it did in 1.21. Its vertices are recorded per render
 * type (already transformed by the pose it was given), and each render type is submitted as custom geometry that
 * replays the recording. An earlier version ran the renderer once to discover render types and again per type;
 * Create Fly's cached buffers recycle their vertex arrays after a render, so the second run read recycled state and
 * crashed (docking connector). Running once also halves the renderer's cost.
 */
public final class RenderBridge {
    private RenderBridge() {
    }

    /** Kept for renderers written against the two-pass bridge; the single-pass bridge never discovers. */
    public static boolean isDiscoveringLayers() {
        return false;
    }

    /** Kept for renderers written against the two-pass bridge; the single-pass bridge never replays a renderer. */
    public static boolean isReplayingLayer() {
        return false;
    }

    public static boolean isReplayingLayer(final RenderType layer) {
        return false;
    }

    public static void submit(final PoseStack poseStack, final SubmitNodeCollector queue, final BiConsumer<PoseStack, MultiBufferSource> renderer) {
        final Map<RenderType, Recording> recordings = new LinkedHashMap<>();
        final PoseStack legacyPose = new PoseStack();
        // Keep the submitted normal matrix as well as the position matrix: rebuilding it from the position matrix
        // folds Ponder's large uniform GUI scale into the normals and makes legacy geometry render too dark.
        legacyPose.last().set(poseStack.last());
        renderer.accept(legacyPose, layer -> recordings.computeIfAbsent(layer, l -> new Recording()));

        for (final Map.Entry<RenderType, Recording> entry : recordings.entrySet()) {
            final Recording recording = entry.getValue();
            if (recording.isEmpty()) {
                continue;
            }
            // The vertices are already in the submitted pose's space, so the replay ignores the pose it is handed.
            queue.submitCustomGeometry(poseStack, entry.getKey(), (pose, consumer) -> recording.replay(consumer));
        }
    }

    /** Records vertices with the attributes the renderer set on each, to replay into a real consumer later. */
    private static final class Recording implements VertexConsumer {
        private static final int COLOR = 1, UV = 2, UV1 = 4, UV2 = 8, NORMAL = 16, LINE_WIDTH = 32, UV3 = 64;

        private final FloatArrayList floats = new FloatArrayList(); // x y z u v nx ny nz lineWidth u3 v3
        private final IntArrayList ints = new IntArrayList();       // mask color uv1 uv2
        private int vertex = -1;

        boolean isEmpty() {
            return this.vertex < 0;
        }

        private int f(final int slot) {
            return this.vertex * 11 + slot;
        }

        private int i(final int slot) {
            return this.vertex * 4 + slot;
        }

        private void flag(final int bit) {
            this.ints.set(i(0), this.ints.getInt(i(0)) | bit);
        }

        @Override
        public VertexConsumer addVertex(final float x, final float y, final float z) {
            this.vertex++;
            this.floats.add(x);
            this.floats.add(y);
            this.floats.add(z);
            for (int k = 3; k < 11; k++) {
                this.floats.add(0f);
            }
            this.ints.add(0);
            this.ints.add(0);
            this.ints.add(0);
            this.ints.add(0);
            return this;
        }

        @Override
        public VertexConsumer setColor(final int red, final int green, final int blue, final int alpha) {
            return this.setColor((alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255);
        }

        @Override
        public VertexConsumer setColor(final int color) {
            if (this.vertex < 0) return this;
            this.ints.set(i(1), color);
            flag(COLOR);
            return this;
        }

        @Override
        public VertexConsumer setUv(final float u, final float v) {
            if (this.vertex < 0) return this;
            this.floats.set(f(3), u);
            this.floats.set(f(4), v);
            flag(UV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(final int u, final int v) {
            if (this.vertex < 0) return this;
            this.ints.set(i(2), (v & 0xFFFF) << 16 | u & 0xFFFF);
            flag(UV1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(final int u, final int v) {
            if (this.vertex < 0) return this;
            this.ints.set(i(3), (v & 0xFFFF) << 16 | u & 0xFFFF);
            flag(UV2);
            return this;
        }

        @Override
        public VertexConsumer setUv3(final float u, final float v) {
            if (this.vertex < 0) return this;
            this.floats.set(f(9), u);
            this.floats.set(f(10), v);
            flag(UV3);
            return this;
        }

        @Override
        public VertexConsumer setNormal(final float x, final float y, final float z) {
            if (this.vertex < 0) return this;
            this.floats.set(f(5), x);
            this.floats.set(f(6), y);
            this.floats.set(f(7), z);
            flag(NORMAL);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(final float lineWidth) {
            if (this.vertex < 0) return this;
            this.floats.set(f(8), lineWidth);
            flag(LINE_WIDTH);
            return this;
        }

        void replay(final VertexConsumer out) {
            for (int v = 0; v <= this.vertex; v++) {
                final int fb = v * 11, ib = v * 4, mask = this.ints.getInt(ib);
                out.addVertex(this.floats.getFloat(fb), this.floats.getFloat(fb + 1), this.floats.getFloat(fb + 2));
                if ((mask & COLOR) != 0) out.setColor(this.ints.getInt(ib + 1));
                if ((mask & UV) != 0) out.setUv(this.floats.getFloat(fb + 3), this.floats.getFloat(fb + 4));
                if ((mask & UV1) != 0) {
                    final int p = this.ints.getInt(ib + 2);
                    out.setUv1(p & 0xFFFF, p >>> 16);
                }
                if ((mask & UV2) != 0) {
                    final int p = this.ints.getInt(ib + 3);
                    out.setUv2(p & 0xFFFF, p >>> 16);
                }
                if ((mask & UV3) != 0) out.setUv3(this.floats.getFloat(fb + 9), this.floats.getFloat(fb + 10));
                if ((mask & NORMAL) != 0) out.setNormal(this.floats.getFloat(fb + 5), this.floats.getFloat(fb + 6), this.floats.getFloat(fb + 7));
                if ((mask & LINE_WIDTH) != 0) out.setLineWidth(this.floats.getFloat(fb + 8));
            }
        }
    }
}
