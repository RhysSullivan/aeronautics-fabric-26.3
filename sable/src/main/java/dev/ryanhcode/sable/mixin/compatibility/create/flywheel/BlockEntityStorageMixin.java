package dev.ryanhcode.sable.mixin.compatibility.create.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.client.flywheel.api.visual.BlockEntityVisual;
import com.zurrtum.create.client.flywheel.api.visualization.BlockEntityVisualizer;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationContext;
import com.zurrtum.create.client.flywheel.impl.visualization.storage.BlockEntityStorage;
import com.zurrtum.create.client.flywheel.impl.visualization.storage.Storage;
import dev.ryanhcode.sable.compatibility.create.flywheel.SubLevelFlywheelEmbeddings;
import dev.ryanhcode.sable.mixinterface.sublevel_render.SubLevelFlywheelStorage;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PORT-26.3: creates the visuals of sub-level block entities inside their sub-level's embedding. */
@Mixin(value = BlockEntityStorage.class, remap = false)
public abstract class BlockEntityStorageMixin extends Storage<BlockEntity> implements SubLevelFlywheelStorage {
    @org.spongepowered.asm.mixin.Shadow
    public abstract com.zurrtum.create.client.flywheel.api.visual.@org.jetbrains.annotations.Nullable BlockEntityVisual<?> visualAtPos(long pos);

    @Unique
    private final SubLevelFlywheelEmbeddings sable$embeddings = new SubLevelFlywheelEmbeddings();

    @WrapOperation(method = "createRaw", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/zurrtum/create/client/flywheel/api/visualization/BlockEntityVisualizer;createVisual(Lcom/zurrtum/create/client/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)Lcom/zurrtum/create/client/flywheel/api/visual/BlockEntityVisual;"))
    private BlockEntityVisual<?> sable$createInSubLevelEmbedding(final BlockEntityVisualizer<BlockEntity> visualizer, final VisualizationContext context,
                                                                 final BlockEntity blockEntity, final float partialTick,
                                                                 final Operation<BlockEntityVisual<?>> original) {
        return original.call(visualizer, this.sable$embeddings.contextFor(context, blockEntity), blockEntity, partialTick);
    }

    @Inject(method = "remove(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("HEAD"))
    private void sable$onRemove(final BlockEntity blockEntity, final CallbackInfo ci) {
        this.sable$embeddings.onRemoved(blockEntity);
    }

    @Inject(method = "recreateAll", at = @At("HEAD"))
    private void sable$onRecreateAll(final VisualizationContext context, final float partialTick, final CallbackInfo ci) {
        this.sable$embeddings.clear();
    }

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void sable$onInvalidate(final CallbackInfo ci) {
        this.sable$embeddings.clear();
    }

    @Override
    public void sable$preFrame(final Level level) {
        SubLevelFlywheelEmbeddings.snapshotPoses(level);
        final java.util.List<BlockEntity> relight = new java.util.ArrayList<>();
        for (final BlockEntity orphan : this.sable$embeddings.updateFrame(relight)) {
            this.remove(orphan);
        }
        for (final BlockEntity blockEntity : relight) {
            if (this.visualAtPos(blockEntity.getBlockPos().asLong()) instanceof final com.zurrtum.create.client.flywheel.api.visual.LightUpdatedVisual lit) {
                lit.updateLight(1.0f);
            }
        }
    }
}
