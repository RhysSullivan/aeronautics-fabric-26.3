package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import dev.ryanhcode.sable.mixinterface.entity.SableRenderDataHolder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT-26.3: each frame's extraction starts with the entity dispatcher's per-frame render data empty (see
 * {@link EntityRenderDispatcherMixin#sable$clearRenderData()}).
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @Inject(method = "extract", at = @At("HEAD"))
    private void sable$newFrame(final CallbackInfo ci) {
        ((SableRenderDataHolder) this.levelRenderer.entityRenderDispatcher()).sable$clearRenderData();
    }
}
