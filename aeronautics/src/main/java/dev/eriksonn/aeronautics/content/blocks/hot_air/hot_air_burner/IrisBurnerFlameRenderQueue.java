package dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import dev.simulated_team.simulated.content.physics_staff.OptionalShaderMods;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws burner flames after a shader pack has composited the frame.
 * <p>
 * The flame is a procedural shader that a pack knows nothing of: drawn into the pack's world targets, it is lit, fogged
 * or blended as the pack sees fit, or skipped. With a pack on, the flames of the world frame are queued instead, each
 * with the camera's model view baked into its pose, and drawn at the end of the level render (after Iris's final pass)
 * with their own shader, depth-tested against the world.
 * <p>
 * PORT-26.3: the 1.21 version drew the queue from {@code GameRenderer.renderLevel}; on 26.3 the begin and draw points
 * are the head and return of {@code LevelRenderer.render} ({@code fabric/mixin/burner_flame/LevelRendererBurnerFlameMixin}).
 * Flames submitted outside a world frame (Ponder, GUIs) still take the fire-sprite fallback.
 */
public final class IrisBurnerFlameRenderQueue {
    private static final List<QueuedFlame> QUEUED_FLAMES = new ArrayList<>();

    private static boolean collectingWorldFrame;
    private static @Nullable GpuBufferSlice worldProjection;
    private static @Nullable ProjectionType worldProjectionType;
    private static @Nullable GpuBufferSlice worldFog;
    private static final Matrix4f viewRotation = new Matrix4f();
    private static final net.minecraft.client.renderer.SubmitNodeStorage storage = new net.minecraft.client.renderer.SubmitNodeStorage();
    private static final net.minecraft.client.renderer.RenderBuffers buffers = new net.minecraft.client.renderer.RenderBuffers(1);
    private static net.minecraft.client.renderer.feature.@Nullable FeatureRenderDispatcher dispatcher;

    private IrisBurnerFlameRenderQueue() {
    }

    /** Starts a world frame; {@code cameraViewRotation} is the camera's rotation that the level render applies to every pose. */
    public static void beginWorldFrame(final org.joml.Matrix4fc cameraViewRotation) {
        QUEUED_FLAMES.clear();
        viewRotation.set(cameraViewRotation);
        collectingWorldFrame = OptionalShaderMods.isShaderPackActive() && !Boolean.getBoolean("aeronautics.debug.noDeferredFlame");
        worldProjection = null;
        worldProjectionType = null;
        worldFog = null;
    }

    public static boolean isCollectingWorldFrame() {
        return collectingWorldFrame;
    }

    public static boolean enqueue(final Matrix4f matrix, final float animationPhase, final float intensity, final float palette) {
        if (!collectingWorldFrame) {
            return false;
        }
        if (worldProjection == null) {
            worldProjection = RenderSystem.getProjectionMatrixBuffer();
            worldProjectionType = RenderSystem.getProjectionType();
            worldFog = RenderSystem.getShaderFog();
        }
        // block-entity poses are camera-relative without the camera's rotation, which the level render applies globally
        QUEUED_FLAMES.add(new QueuedFlame(new Matrix4f(viewRotation).mul(matrix), animationPhase, intensity, palette));
        return true;
    }

    public static void finishWorldFrameCollection() {
        collectingWorldFrame = false;
    }

    /** Draws the queued flames onto the main target (call after the level render, outside any render pass). */
    public static void drawAfterShaderComposite() {
        if (QUEUED_FLAMES.isEmpty()) {
            return;
        }
        final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        final GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        final ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        final GpuBufferSlice previousFog = RenderSystem.getShaderFog();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.identity();
            if (worldProjection != null && worldProjectionType != null) {
                RenderSystem.setProjectionMatrix(worldProjection, worldProjectionType);
            }
            if (worldFog != null) {
                RenderSystem.setShaderFog(worldFog);
            }
            // 26.3 draws through submissions: submit the flames as custom geometry (positions already in view space)
            // and render them in one pass onto the main target, depth-tested against what is there after the pack
            final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (dispatcher == null) {
                dispatcher = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(buffers, minecraft.getModelManager(),
                        minecraft.getAtlasManager(), minecraft.font, minecraft.gameRenderer.gameRenderState());
            }
            final List<QueuedFlame> flames = new ArrayList<>(QUEUED_FLAMES);
            storage.submitCustomGeometry(new com.mojang.blaze3d.vertex.PoseStack(), AeroRenderTypes.burnerFlame(), (pose, consumer) -> {
                for (final QueuedFlame flame : flames) {
                    HotAirBurnerRenderer.renderFlame(flame.matrix, consumer, flame.animationPhase, flame.intensity, flame.palette);
                }
            });
            final var target = minecraft.gameRenderer.mainRenderTarget();
            try (net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
                 com.mojang.renderpearl.api.commands.RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                         () -> "Aeronautics burner flames after the shader pack", target.getColorTextureView(), java.util.Optional.empty(),
                         target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
            buffers.endFrame();
        } finally {
            QUEUED_FLAMES.clear();
            modelViewStack.popMatrix();
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
            if (previousFog != null) {
                RenderSystem.setShaderFog(previousFog);
            }
        }
    }

    private record QueuedFlame(Matrix4f matrix, float animationPhase, float intensity, float palette) {
    }
}
