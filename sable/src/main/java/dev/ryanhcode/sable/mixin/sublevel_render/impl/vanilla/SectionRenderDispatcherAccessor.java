package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT-26.3: the builder pack a dispatcher's synchronous compiles write into. */
@Mixin(SectionRenderDispatcher.class)
public interface SectionRenderDispatcherAccessor {
    @Accessor("fixedBuffers")
    SectionBufferBuilderPack sable$fixedBuffers();
}
