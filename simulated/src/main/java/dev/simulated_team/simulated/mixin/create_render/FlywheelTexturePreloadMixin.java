package dev.simulated_team.simulated.mixin.create_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.client.flywheel.backend.engine.MaterialRenderState;
import com.zurrtum.create.client.flywheel.backend.engine.TextureBinder;
import dev.simulated_team.simulated.mixin_interface.create_render.FlywheelTextureQueue;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT-26.3: Create Fly 26.3-6.0.9-2's Flywheel fetches its textures with {@code TextureManager.getTexture} while it
 * draws, inside vanilla's open main render pass. A texture not loaded yet is loaded and uploaded right there, and
 * 26.3's command encoder refuses any upload inside a render pass ("Close the existing render pass before performing
 * additional commands"), always, on every GPU. Without Sodium (which leaves no pass open at that point) Flywheel
 * therefore dies on its first frame and falls back to {@code flywheel:off}. Create Fly fixed this after that release
 * (commit a25c2f0, "Fix Flywheel texture register and load": textures prepared before drawing).
 * <p>
 * Backport: an unloaded texture is queued and the block atlas is bound for that one frame; the queue is loaded at the
 * start of the next level render ({@link VisualizationManagerTexturePreloadMixin}), outside any pass. With
 * {@code require = 0}, these no-op on a Create Fly release that no longer fetches textures here.
 */
@Mixin(value = {MaterialRenderState.class, TextureBinder.class}, remap = false)
public class FlywheelTexturePreloadMixin {
    @WrapOperation(method = {"setupTexture", "bind", "bindCrumbling", "byName"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
    private static AbstractTexture simulated$noUploadInPass(final TextureManager manager, final Identifier id, final Operation<AbstractTexture> original) {
        if (((FlywheelTextureQueue) manager).simulated$isLoaded(id)) {
            return original.call(manager, id);
        }
        FlywheelTextureQueue.QUEUE.add(id);
        return original.call(manager, TextureAtlas.LOCATION_BLOCKS);
    }
}
