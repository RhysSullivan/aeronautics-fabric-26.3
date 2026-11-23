package dev.simulated_team.simulated.mixin.create_render;

import dev.simulated_team.simulated.mixin_interface.create_render.FlywheelTextureQueue;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/** PORT-26.3: lets {@link FlywheelTexturePreloadMixin} ask whether a texture is loaded without loading it. */
@Mixin(TextureManager.class)
public class TextureManagerAccessMixin implements FlywheelTextureQueue {
    @Shadow
    @Final
    private Map<Identifier, AbstractTexture> byPath;

    @Override
    public boolean simulated$isLoaded(final Identifier id) {
        return this.byPath.containsKey(id);
    }
}
