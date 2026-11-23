package dev.simulated_team.simulated.mixin.create_render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.client.catnip.render.EntityBlockLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PORT-26.3: Create Fly 26.3-6.0.9-2's {@code EntityBlockLayer.renderInto(Pose pose, ...)} multiplies the caller's pose
 * by its own parameter (which shadows the layer's {@code pose} field) instead of by the layer's transform, so buffers
 * drawn through {@code SuperByteBuffer.renderInto} land at twice their camera-relative offset, off screen.
 * {@code EntityBlockLightLayer} and the submit paths use the field. Simulated's legacy renderers draw through
 * {@code renderInto} when Flywheel is off (no backend, or Iris with a shader pack), so the steering wheel, the throttle
 * lever handle and the other legacy parts were invisible then, in the world and on sub-levels.
 */
@Mixin(value = EntityBlockLayer.class, remap = false)
public class EntityBlockLayerMixin {
    @Shadow
    @Final
    private PoseStack.Pose pose;

    @ModifyArg(method = "renderInto", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/zurrtum/create/client/catnip/render/SuperByteBuffer;mul(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"), index = 1)
    private PoseStack.Pose simulated$layerTransform(final PoseStack.Pose callerPose) {
        return this.pose;
    }
}
