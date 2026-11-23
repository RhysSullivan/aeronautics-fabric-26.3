package dev.simulated_team.simulated.compat.minecraft;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * PORT-26.3: 26.3 removed {@code net.minecraft.client.renderer.MultiBufferSource}. The port's renderers still draw
 * through this shape; {@link dev.simulated_team.simulated.compat.create.RenderBridge} replays them into 26.3's
 * {@code SubmitNodeCollector}, one custom-geometry submission per render type they ask for.
 */
@FunctionalInterface
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType renderType);
}
