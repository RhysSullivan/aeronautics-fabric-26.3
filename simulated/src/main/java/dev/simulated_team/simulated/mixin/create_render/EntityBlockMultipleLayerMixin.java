package dev.simulated_team.simulated.mixin.create_render;

import com.zurrtum.create.client.catnip.render.EntityBlockMultipleLayer;
import com.zurrtum.create.client.catnip.render.SuperByteBufferRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT-26.3: Create Fly's immediate {@code renderInto} on a multi-part buffer puts the parent on its recycle list and
 * each part on its own (parts recycle themselves when rendered). Recycling the parent then recycled the parts a
 * second time, so each part sat in the pool twice and was handed to two tasks: background {@code layer.future is
 * null} errors and corrupted vertex arrays (a crash). Simulated's renderers use that path. The parent no longer
 * recycles its parts; Create's own submit path never put parts there in the first place.
 */
@Mixin(EntityBlockMultipleLayer.class)
public class EntityBlockMultipleLayerMixin {
    @Redirect(method = "recycle", at = @At(value = "INVOKE",
            target = "Lcom/zurrtum/create/client/catnip/render/SuperByteBufferRenderState;recycle()V"))
    private void simulated$partsRecycleThemselves(final SuperByteBufferRenderState part) {
    }
}
