package dev.ryanhcode.sable.sublevel.render.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * PORT-26.3: the section dispatcher that compiles and uploads sub-level sections.
 * <p>
 * Without Sodium this is the level renderer's own dispatcher. Sodium replaces it with one that ignores compile work
 * ({@code IgnoringSectionRenderDispatcher}), so then Sable keeps a private dispatcher of its own, builds it with a
 * compiler for the current models, and uploads its buffers once per frame before the main pass.
 */
public final class SubLevelSectionDispatcher {
    private static @Nullable SectionRenderDispatcher own;
    private static @Nullable Object ownModels;
    private static boolean ownAo;
    private static boolean ownCutoutLeaves;

    private SubLevelSectionDispatcher() {
    }

    /** The dispatcher sub-level sections live in, or null before the level renderer has one. */
    public static @Nullable SectionRenderDispatcher get() {
        final Minecraft minecraft = Minecraft.getInstance();
        final SectionRenderDispatcher vanilla = minecraft.levelRenderer.sectionRenderDispatcher();
        if (vanilla != null && vanilla.getClass() == SectionRenderDispatcher.class) {
            return vanilla;
        }
        return ownDispatcher(minecraft);
    }

    public static boolean isOwn(final SectionRenderDispatcher dispatcher) {
        return dispatcher == own;
    }

    /**
     * PORT-26.3: the builders behind Sable's own dispatcher (native memory, closed only explicitly), made once and kept
     * for the life of the client: a dispatcher made again after {@link #reset()} reuses them, so a reset allocates
     * nothing. A compile task still running in a disposed dispatcher holds its builder pack from the pool until it
     * finishes, so the next dispatcher cannot be handed a pack in use; the fixed pack is only used on the main thread.
     */
    private static @Nullable RenderBuffers ownBuffers;

    private static RenderBuffers ownBuffers() {
        if (ownBuffers == null) {
            ownBuffers = new RenderBuffers(2);
        }
        return ownBuffers;
    }

    private static SectionRenderDispatcher ownDispatcher(final Minecraft minecraft) {
        final Options options = minecraft.options;
        final Object models = minecraft.getModelManager().getBlockStateModelSet();
        final boolean ao = options.ambientOcclusion().get();
        final boolean cutoutLeaves = options.cutoutLeaves().get();
        if (own == null || models != ownModels || ao != ownAo || cutoutLeaves != ownCutoutLeaves) {
            final SectionCompiler compiler = new SectionCompiler(ao, cutoutLeaves, minecraft.getModelManager().getBlockStateModelSet(),
                    minecraft.getModelManager().getFluidStateModelSet(), minecraft.getBlockColors());
            if (own == null) {
                // own buffers: with Sodium the game's section buffer pool is allocated empty, so compile tasks would wait forever
                own = new SectionRenderDispatcher(Util.backgroundExecutor(), ownBuffers(), compiler, section -> { });
                // the compile queue sorts by camera distance and stalls on a null camera position
                own.setCameraPosition(minecraft.gameRenderer.mainCamera().position());
            } else {
                own.setCompiler(compiler);
            }
            ownModels = models;
            ownAo = ao;
            ownCutoutLeaves = cutoutLeaves;
        }
        return own;
    }

    /**
     * PORT-26.3: the client level is changing (a new level or a disconnect). As vanilla disposes its own section dispatcher
     * then, Sable's is disposed and made again on next use (with the same builders, see {@link #ownBuffers}); otherwise
     * meshes still staged in it keep the old level's block entities, and through them the old level, for the life of
     * the game.
     */
    public static void reset() {
        if (own != null) {
            own.dispose();
            own = null;
            ownModels = null;
        }
    }

    /**
     * PORT-26.3: a synchronous compile into the dispatcher's fixed builder pack failed part way. Vertices already written
     * but never built would become part of the next build of that layer, and a mesh built for an earlier layer before
     * the failure is never closed, so its builder's result count never returns to 0 and the builder never rewinds (it
     * grows with every later compile). Call on the render thread, outside a compile.
     * <p>
     * A sync compile closes every mesh it built before it returns (RebuildTask.doTask), so whatever the pack still holds
     * belongs to the compile that threw. Each builder is reset the way {@code discardResults} resets it, without building
     * from it first (the failure may be a layer too large to build, which would throw again): both offsets and the result
     * count to 0, and the generation moved on, so the lost meshes are invalid and closing them later does nothing. A
     * freed builder (generation -1) is left alone.
     */
    public static void drainAfterFailedSyncCompile(final SectionRenderDispatcher dispatcher) {
        final net.minecraft.client.renderer.SectionBufferBuilderPack pack =
                ((dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla.SectionRenderDispatcherAccessor) dispatcher).sable$fixedBuffers();
        for (final net.minecraft.client.renderer.chunk.ChunkSectionLayer layer : net.minecraft.client.renderer.chunk.ChunkSectionLayer.values()) {
            final dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla.ByteBufferBuilderAccessor buffer =
                    (dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla.ByteBufferBuilderAccessor) pack.buffer(layer);
            final int generation = buffer.sable$generation();
            if (generation == -1) {
                continue;
            }
            buffer.sable$setWriteOffset(0L);
            buffer.sable$setNextResultOffset(0L);
            buffer.sable$setResultCount(0);
            buffer.sable$setGeneration(generation + 1 == -1 ? 0 : generation + 1);
        }
    }

    /** Keeps Sable's own dispatcher's camera current; call before scheduling compiles. */
    public static void setCameraIfOwn(final SectionRenderDispatcher dispatcher, final Vec3 camera) {
        if (dispatcher == own) {
            dispatcher.setCameraPosition(camera);
        }
    }

    /** Uploads compiled sub-level sections when the dispatcher is Sable's own (vanilla uploads its own). Call outside a render pass. */
    public static void uploadIfOwn(final SectionRenderDispatcher dispatcher, final Vec3 camera) {
        if (dispatcher != own) {
            return;
        }
        dispatcher.setCameraPosition(camera);
        dispatcher.lock();
        try {
            dispatcher.uploadTerrainBuffersToGpu();
        } finally {
            dispatcher.unlock();
        }
    }
}
