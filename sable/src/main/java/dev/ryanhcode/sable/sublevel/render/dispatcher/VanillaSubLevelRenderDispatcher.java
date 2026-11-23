package dev.ryanhcode.sable.sublevel.render.dispatcher;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

    // mc26.1 port branch: the Veil-driven dynamic shading / sky-light shadow
    // uniforms and the spherical-fog override were stripped together with the
    // Veil shader preprocessors; plots use plain vanilla chunk shading.

    public VanillaSubLevelRenderDispatcher() {
    }

    @Override
    public void onResourceManagerReload(@NotNull final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        // PORT-26.3: every sub-level, a single block included, uses chunked render data (the single-block fast path
        // and its VanillaSingleSubLevelRenderData were removed: its Tesselator/ShaderInstance pipeline is gone in 26.x).
        if (renderData instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
            chunkedRenderData.resize();
            chunkedRenderData.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.mainCamera());
        }
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        final SectionRenderDispatcher sectionRenderDispatcher = dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelSectionDispatcher.get(); // PORT-26.3: own dispatcher under Sodium
        return new VanillaChunkedSubLevelRenderData(subLevel, sectionRenderDispatcher);
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Frustum cullFrustum, final boolean isSpectator) {
        // TODO
    }


    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        // PORT-TODO(mc26.1): block entities on sub-levels are not rendered yet.
        // The vanilla path moved to extract/submit (LevelRenderState +
        // SubmitNodeCollector); the old camera-spoofing dispatcher hook and
        // per-BE render call were removed with it. Re-implement by extracting
        // plot block entities with the plot transform applied to their poses.
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
