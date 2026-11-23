package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import dev.ryanhcode.sable.mixinterface.sublevel_render.SubLevelBlockEntityBatches;
import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.resources.model.ModelManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** PORT-26.3: submits sub-level block entities straight after the world's, each sub-level with its own pose. */
@Mixin(LevelRenderer.class)
public class LevelRendererBlockEntityMixin {
    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    @Final
    private ModelManager modelManager;

    @Inject(method = "submitFeatures", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void sable$submitSubLevelBlockEntities(final LevelRenderState levelRenderState, final SubmitNodeCollector collector,
                                                   final boolean renderOutline, final CallbackInfo ci) {
        final List<SubLevelBlockEntityRendering.Batch> batches = ((SubLevelBlockEntityBatches) levelRenderState).sable$subLevelBlockEntities();
        SubLevelBlockEntityRendering.submit(batches, this.blockEntityRenderDispatcher, collector, levelRenderState.cameraRenderState);
        batches.clear();
    }

    @Inject(method = "submitFeatures", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;submitBlockDestroyAnimation(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"))
    private void sable$submitSubLevelBreaking(final LevelRenderState levelRenderState, final SubmitNodeCollector collector,
                                              final boolean renderOutline, final CallbackInfo ci) {
        final List<SubLevelBlockEntityRendering.Breaking> breaking = ((SubLevelBlockEntityBatches) levelRenderState).sable$subLevelBreaking();
        SubLevelBlockEntityRendering.submitBreaking(breaking, this.modelManager, collector);
        breaking.clear();
    }

    /** PORT-26.3: the block outline on a sub-level block gets the sub-level's pose (replaces fabric/mixin/block_outline_render). */
    @WrapOperation(method = "submitBlockOutline", require = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void sable$subLevelOutlinePose(final PoseStack poseStack, final double x, final double y, final double z, final Operation<Void> original,
                                           @Local(argsOnly = true) final LevelRenderState levelRenderState) {
        if (levelRenderState.blockOutlineRenderState == null
                || !SubLevelBlockEntityRendering.transformOutline(poseStack, levelRenderState.blockOutlineRenderState.pos(), levelRenderState.cameraRenderState.pos)) {
            original.call(poseStack, x, y, z);
        }
    }
}
