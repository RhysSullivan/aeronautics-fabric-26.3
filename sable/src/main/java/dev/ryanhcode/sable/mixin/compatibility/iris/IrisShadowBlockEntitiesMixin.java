package dev.ryanhcode.sable.mixin.compatibility.iris;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT-26.3: sub-level block entities in Iris's shadow pass. Iris extracts block entities for its shadow pass itself,
 * through Sodium, which only knows the world's; this extracts the sub-levels' block entities for the shadow pass too
 * (with the camera Iris passes) and submits them into its storage with the shadow model view as the base pose.
 * Applied only with Iris loaded (compatibility package); {@code sable.iris.shipBlockEntityShadows=false} turns it off.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public class IrisShadowBlockEntitiesMixin {
    @org.spongepowered.asm.mixin.Unique
    private static boolean sable$logged;

    @Inject(method = "renderBlockEntities", at = @At("RETURN"), cancellable = true, require = 0)
    private void sable$subLevelBlockEntities(final CallbackInfoReturnable<Integer> cir,
                                             @Local(argsOnly = true) final PoseStack modelView,
                                             @Local(argsOnly = true) final SubmitNodeStorage storage,
                                             @Local(argsOnly = true) final LevelRenderState levelRenderState,
                                             @Local(argsOnly = true) final net.minecraft.client.Camera camera) {
        if ("false".equalsIgnoreCase(System.getProperty("sable.iris.shipBlockEntityShadows"))) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final int count = SubLevelBlockEntityRendering.extractAndSubmitForPass(minecraft.level, minecraft.getBlockEntityRenderDispatcher(),
                camera.position(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), storage, levelRenderState.cameraRenderState,
                modelView.last().pose());
        if (!sable$logged && count > 0) {
            sable$logged = true;
            dev.ryanhcode.sable.Sable.LOGGER.info("PORT-26.3: sub-level block entities submitted into Iris's shadow pass: {}", count);
        }
        if (count > 0) {
            cir.setReturnValue(cir.getReturnValue() + count);
        }
    }
}
