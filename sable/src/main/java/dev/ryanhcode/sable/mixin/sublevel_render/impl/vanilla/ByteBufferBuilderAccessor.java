package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** PORT-26.3: resetting a builder after a failed compile without building from it (see SubLevelSectionDispatcher). */
@Mixin(ByteBufferBuilder.class)
public interface ByteBufferBuilderAccessor {
    @Accessor("writeOffset")
    void sable$setWriteOffset(long offset);

    @Accessor("nextResultOffset")
    void sable$setNextResultOffset(long offset);

    @Accessor("resultCount")
    void sable$setResultCount(int count);

    @Accessor("generation")
    int sable$generation();

    @Accessor("generation")
    void sable$setGeneration(int generation);
}
