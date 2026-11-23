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
                final RenderBuffers buffers = new RenderBuffers(2);
                own = new SectionRenderDispatcher(Util.backgroundExecutor(), buffers, compiler, section -> { });
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
