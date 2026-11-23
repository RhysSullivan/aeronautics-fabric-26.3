package dev.ryanhcode.sable.mixin.compatibility.create.flywheel;

import com.zurrtum.create.client.flywheel.lib.visual.AbstractVisual;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractVisual.class, remap = false)
public interface AbstractVisualAccessor {
    @Accessor("level")
    Level sable$level();
}
