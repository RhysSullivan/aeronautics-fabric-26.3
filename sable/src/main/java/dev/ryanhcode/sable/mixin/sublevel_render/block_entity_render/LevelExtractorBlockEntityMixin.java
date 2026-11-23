package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinterface.sublevel_render.SubLevelBlockEntityBatches;
import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT-26.3: extracts sub-level block entities (replaces the 1.21.x block_entity_render.LevelRendererMixin).
 * Wraps the call in {@code extract} rather than injecting into {@code extractVisibleBlockEntities}, because Sodium
 * cancels that method at its head and extracts block entities its own way.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorBlockEntityMixin {
    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @WrapOperation(method = "extract", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractVisibleBlockEntities(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
    private void sable$extractSubLevelBlockEntities(final LevelExtractor instance, final Camera camera, final float partialTick,
                                                    final LevelRenderState levelRenderState, final Operation<Void> original) {
        original.call(instance, camera, partialTick, levelRenderState);
        SubLevelBlockEntityRendering.extract(this.level, this.levelRenderer.blockEntityRenderDispatcher(), camera.position(), camera.getCullFrustum(), partialTick,
                levelRenderState.blockEntityRenderStates, ((SubLevelBlockEntityBatches) levelRenderState).sable$subLevelBlockEntities());
    }

    @WrapOperation(method = "extract", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractBlockDestroyAnimation(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
    private void sable$extractSubLevelBreaking(final LevelExtractor instance, final Camera camera, final LevelRenderState levelRenderState,
                                               final Operation<Void> original) {
        original.call(instance, camera, levelRenderState);
        SubLevelBlockEntityRendering.extractBreaking(this.level, camera.position(), ((SubLevelBlockEntityBatches) levelRenderState).sable$subLevelBreaking());
    }
}
