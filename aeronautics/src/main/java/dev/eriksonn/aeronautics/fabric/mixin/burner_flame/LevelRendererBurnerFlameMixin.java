package dev.eriksonn.aeronautics.fabric.mixin.burner_flame;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.IrisBurnerFlameRenderQueue;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT-26.3: the world frame for {@link IrisBurnerFlameRenderQueue}. Flames are collected while the level renders and
 * drawn at its return, after a shader pack's final pass (Iris finishes its frame before the level render returns).
 */
@Mixin(LevelRenderer.class)
public class LevelRendererBurnerFlameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void aeronautics$beginFlames(final GraphicsResourceAllocator resourceAllocator, final boolean renderOutline, final CameraRenderState cameraState,
                                         final GpuBufferSlice terrainFog, final Vector4f fogColor, final boolean shouldRenderSky,
                                         final boolean consistentDepthRequired, final CallbackInfo ci) {
        IrisBurnerFlameRenderQueue.beginWorldFrame(cameraState.viewRotationMatrix);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void aeronautics$drawFlames(final GraphicsResourceAllocator resourceAllocator, final boolean renderOutline, final CameraRenderState cameraState,
                                        final GpuBufferSlice terrainFog, final Vector4f fogColor, final boolean shouldRenderSky,
                                        final boolean consistentDepthRequired, final CallbackInfo ci) {
        IrisBurnerFlameRenderQueue.finishWorldFrameCollection();
        IrisBurnerFlameRenderQueue.drawAfterShaderComposite();
    }
}
