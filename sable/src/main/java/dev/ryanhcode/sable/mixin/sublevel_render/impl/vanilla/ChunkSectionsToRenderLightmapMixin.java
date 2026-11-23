package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PORT-26.3: a sub-level's terrain draw binds its own lightmap (see {@link SubLevelLightmaps}). */
@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderLightmapMixin {
    @WrapOperation(method = "renderGroup", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;lightmap()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView sable$subLevelLightmap(final GameRenderer gameRenderer, final Operation<GpuTextureView> original) {
        final GpuTextureView override = SubLevelLightmaps.override();
        return override != null ? override : original.call(gameRenderer);
    }
}
