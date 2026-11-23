package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT-26.3: while a sub-level's block entities are extracted, light looked up through {@code LightCoordsUtil} (by
 * vanilla's base extraction and by renderers that look it up themselves, Create's among them) gets the sub-level's
 * sky-light scale, as its terrain does. Render thread only; anything else is untouched.
 */
@Mixin(LightCoordsUtil.class)
public class LightCoordsUtilMixin {
    @ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"))
    private static int sable$subLevelSkyScale(final int light) {
        final ClientSubLevel subLevel = SubLevelBlockEntityRendering.extracting();
        if (subLevel == null || !Minecraft.getInstance().isSameThread()) {
            return light;
        }
        return subLevel.scaleLightColor(light);
    }
}
