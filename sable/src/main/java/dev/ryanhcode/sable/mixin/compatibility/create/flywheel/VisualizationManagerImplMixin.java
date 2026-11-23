package dev.ryanhcode.sable.mixin.compatibility.create.flywheel;

import com.zurrtum.create.client.flywheel.api.backend.RenderContext;
import com.zurrtum.create.client.flywheel.impl.visualization.VisualManagerImpl;
import com.zurrtum.create.client.flywheel.impl.visualization.VisualizationManagerImpl;
import com.zurrtum.create.client.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.ryanhcode.sable.mixinterface.sublevel_render.SubLevelFlywheelStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PORT-26.3: moves sub-level embeddings to this frame's render poses before Flywheel's frame plan runs. */
@Mixin(value = VisualizationManagerImpl.class, remap = false)
public class VisualizationManagerImplMixin {
    @Shadow
    @Final
    private VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntities;

    @Shadow
    @Final
    private LevelAccessor level;

    // after the wait for the last tick plan, so no tick-plan worker is adding visuals while embeddings change
    @Inject(method = "beginFrame", require = 1, at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/client/flywheel/impl/visual/DistanceUpdateLimiterImpl;tick()V"))
    private void sable$updateSubLevelEmbeddings(final RenderContext context, final CallbackInfo ci) {
        if (this.level instanceof final Level level) {
            ((SubLevelFlywheelStorage) this.blockEntities.getStorage()).sable$preFrame(level);
        }
    }
}
