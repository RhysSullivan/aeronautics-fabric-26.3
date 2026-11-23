package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.IrisHullPath;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * PORT-26.3: sub-level terrain on the 26.3 renderer (replaces the 1.21.x LevelRendererMixin).
 * <p>
 * 26.3 draws terrain with one ModelViewMat per {@link ChunkSectionsToRender} (TerrainUniform) and per-section data of
 * only an integer origin and a fade value, so a rotated sub-level cannot ride inside vanilla's draws. Each sub-level gets
 * its own {@link ChunkSectionsToRender.DrawSeparate}, rendered in the same pass straight after vanilla's terrain group.
 * <p>
 * The vertex shader computes {@code pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset}, with
 * CameraBlockPos = floor(camera) and CameraOffset = floor(camera) - camera. With an integer anchor A near the pivot and
 * ChunkPosition = sectionOrigin - A + CameraBlockPos, pos = plotVertex - A - frac(camera), so the model view
 * {@code base * T(pivotWorld - camera) * R * S * T(A + frac(camera) - pivotPlot)} places the plot vertex in camera space
 * while every term stays small (plots live far away; float precision would be lost otherwise).
 * The anchor A is the camera's position in the sub-level's frame (floored), so the shader's fog distance |pos| is the
 * distance to the camera; cylindrical fog is exact for yaw and approximate under pitch or roll.
 */
@Mixin(value = LevelRenderer.class, priority = 1002)
public abstract class LevelRendererSubLevelTerrainMixin {

    // 26.3: LevelRenderer no longer keeps the level (LevelExtractor does); the client's current level is the same one
    @Unique
    private @Nullable ClientLevel sable$level() {
        return Minecraft.getInstance().level;
    }

    @Unique
    private final List<ChunkSectionsToRender> sable$subLevelTerrain = new ArrayList<>();

    // PORT-26.3: each entry's lightmap (sky rows scaled by its sub-level's sky light), or null at full sky
    @Unique
    private final List<com.mojang.renderpearl.api.textures.@Nullable GpuTextureView> sable$subLevelLightmaps = new ArrayList<>();

    @Unique
    private @Nullable DynamicGpuDataStorageMapped<DynamicGpuData.ChunkSectionInfo> sable$sectionInfos;

    @Unique
    private static boolean sable$warnedMisaligned;

    @Unique
    private static boolean sable$warnedDrawFailure;

    @Unique
    private static int sable$skyLogFrame;

    @Unique
    private @Nullable GpuBufferSlice sable$terrainFog;

    // Wraps the call in render(...) rather than the method, so it still runs when Sodium wraps the same call and returns
    // its own terrain object instead of calling vanilla's prepareChunkRenders.
    @WrapOperation(method = "render", require = 1, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareChunkRenders(Lorg/joml/Matrix4fc;Z)Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareChunkRendersIndirect(Lorg/joml/Matrix4fc;Z)Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;")})
    private ChunkSectionsToRender sable$prepareWithSubLevels(final LevelRenderer instance, final Matrix4fc baseModelView, final boolean respectTranslucentOrder,
                                                             final Operation<ChunkSectionsToRender> original) {
        final ChunkSectionsToRender terrain = original.call(instance, baseModelView, respectTranslucentOrder);
        this.sable$prepareSubLevelTerrain(baseModelView);
        return terrain;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sable$captureTerrainFog(final com.mojang.blaze3d.resource.GraphicsResourceAllocator resourceAllocator, final boolean renderOutline,
                                         final CameraRenderState cameraState, final GpuBufferSlice terrainFog, final org.joml.Vector4f fogColor,
                                         final boolean shouldRenderSky, final boolean consistentDepthRequired, final CallbackInfo ci) {
        IrisHullPath.setTerrainFog(terrainFog);
        this.sable$terrainFog = terrainFog;
    }

    @Unique
    private void sable$prepareSubLevelTerrain(final Matrix4fc baseModelView) {
        this.sable$subLevelTerrain.clear();
        this.sable$subLevelLightmaps.clear();
        dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.beginFrame();
        final boolean pathChanged = IrisHullPath.beginFrame(baseModelView);
        final boolean irisPath = IrisHullPath.active();
        if (this.sable$level() == null) {
            return;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.sable$level());
        final SectionRenderDispatcher dispatcher = dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelSectionDispatcher.get();
        if (container == null || dispatcher == null) {
            return;
        }
        if (pathChanged) {
            // a shader pack was switched on or off: Iris builds meshes in its wider vertex format while a pack is in use
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                subLevel.getRenderData().rebuild();
            }
        }
        // compile dirty sub-level sections, and upload them when the dispatcher is Sable's own (the Sodium case)
        final net.minecraft.client.Camera mainCamera = Minecraft.getInstance().gameRenderer.mainCamera();
        final RenderRegionCache regionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates updates = Minecraft.getInstance().options.prioritizeChunkUpdates().get();
        dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelSectionDispatcher.setCameraIfOwn(dispatcher, mainCamera.position());
        for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
            subLevel.getRenderData().compileSections(updates, regionCache, mainCamera);
        }
        dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelSectionDispatcher.uploadIfOwn(dispatcher, mainCamera.position());

        if (this.sable$sectionInfos == null) {
            this.sable$sectionInfos = new DynamicGpuDataStorageMapped<>("Sable sub-level sections", DynamicGpuData.CHUNK_SECTION_UBO_SIZE, 128, 2);
        } else {
            this.sable$sectionInfos.endFrame();
        }

        final GpuTextureView atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        final Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        final int camBlockX = Mth.floor(camera.x), camBlockY = Mth.floor(camera.y), camBlockZ = Mth.floor(camera.z);
        final double fracX = camera.x - camBlockX, fracY = camera.y - camBlockY, fracZ = camera.z - camBlockZ;

        dispatcher.lock();
        try {
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                if (!(subLevel.getRenderData() instanceof final VanillaChunkedSubLevelRenderData renderData)) {
                    continue;
                }
                final Pose3dc pose = subLevel.renderPose();
                final Vector3dc pivot = pose.rotationPoint();
                // PORT-26.3 (fog): anchor at the camera's position in the sub-level's own frame, not at the pivot. The
                // shader measures fog from pos = plotVertex - anchor - frac(camera), so |pos| is now the true distance to
                // the camera (rotation keeps lengths) to within a block; anchored at the pivot, far ships got too little fog.
                final org.joml.Vector3d localCamera = new dev.ryanhcode.sable.companion.math.Pose3d(pose)
                        .transformPositionInverse(new org.joml.Vector3d(camera.x, camera.y, camera.z));
                final int anchorX = Mth.floor(localCamera.x), anchorY = Mth.floor(localCamera.y), anchorZ = Mth.floor(localCamera.z);

                final Matrix4f local = new Matrix4f(renderData.getTransformation(camera.x, camera.y, camera.z))
                        .translate((float) (anchorX + fracX - pivot.x()), (float) (anchorY + fracY - pivot.y()), (float) (anchorZ + fracZ - pivot.z()));
                final Matrix4f modelView = new Matrix4f(baseModelView).mul(local);
                // PORT-26.3 (Iris): the same section slices, drawn with the block pipelines and a per-section ModelOffset
                // also used without a pack for a sub-level whose sections are not aligned to the vertex size (see below)
                final List<org.joml.Vector3f> hullOffsets = new ArrayList<>();
                final EnumMap<ChunkSectionLayer, List<IrisHullPath.Draw>> hullDraws = new EnumMap<>(ChunkSectionLayer.class);
                for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                    hullDraws.put(layer, new ArrayList<>());
                }
                boolean misaligned = false;

                final List<DynamicGpuData.ChunkSectionInfo> infos = new ArrayList<>();
                final EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> draws = new EnumMap<>(ChunkSectionLayer.class);
                for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                    draws.put(layer, new ArrayList<>());
                }
                int largestIndexCount = 0;

                for (final SectionRenderDispatcher.RenderSection section : renderData.allRenderSections()) {
                    final SectionMesh mesh = section.getSectionMesh();
                    final BlockPos origin = section.getRenderOrigin();
                    int infoIndex = -1;
                    for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        final SectionMesh.SectionDraw draw = mesh.getSectionDraw(layer);
                        final SectionRenderDispatcher.RenderSectionBufferSlice slice = dispatcher.getRenderSectionSlice(mesh, layer);
                        if (slice == null || draw == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) {
                            continue;
                        }
                        if (infoIndex == -1) {
                            infoIndex = infos.size();
                            infos.add(new DynamicGpuData.ChunkSectionInfo(
                                    origin.getX() - anchorX + camBlockX, origin.getY() - anchorY + camBlockY, origin.getZ() - anchorZ + camBlockZ, 1.0F));
                            {
                                // terrain.vsh: Position + (ChunkPosition - CameraBlockPos) + CameraOffset = Position + origin - anchor - frac
                                hullOffsets.add(new org.joml.Vector3f((float) (origin.getX() - anchorX - fracX),
                                        (float) (origin.getY() - anchorY - fracY), (float) (origin.getZ() - anchorZ - fracZ)));
                            }
                        }
                        final VertexFormat format = layer.pipeline(false).getVertexFormatBinding(0);
                        final GpuBuffer indexBuffer;
                        final IndexType indexType;
                        int firstIndex = 0;
                        if (!draw.hasCustomIndexBuffer()) {
                            largestIndexCount = Math.max(largestIndexCount, draw.indexCount());
                            indexBuffer = null;
                            indexType = null;
                        } else {
                            indexBuffer = slice.indexBuffer();
                            indexType = draw.indexType();
                            firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                        }
                        final int baseVertex = (int) (slice.vertexBufferOffset() / format.getVertexSize());
                        final int index = infoIndex;
                        if (slice.vertexBufferOffset() % format.getVertexSize() != 0) {
                            misaligned = true;
                        }
                        {
                            hullDraws.get(layer).add(new IrisHullPath.Draw(slice.vertexBuffer(), indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex, index, slice.vertexBufferOffset()));
                        }
                        draws.get(layer).add(new RenderPass.Draw<>(0, slice.vertexBuffer(), indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex,
                                (sectionUbos, uploader) -> uploader.setUniform("ChunkSection", sectionUbos[index])));
                    }
                }
                if (infos.isEmpty()) {
                    continue;
                }
                if (largestIndexCount != 0) {
                    RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).requestIndexCount(largestIndexCount);
                }
                if (Boolean.getBoolean("sable.debug.skyLog") && (sable$skyLogFrame++ % 40) == 0) { // PORT-26.3: test-only
                    final var box = subLevel.boundingBox();
                    final var c = box.center(new org.joml.Vector3d());
                    final var lvl = this.sable$level();
                    Sable.LOGGER.info("PORT-26.3: sub-level sky light scale {} at {} {} {} (bounds y {}..{}); world sky at centre+0.1 {}, block light {}",
                            subLevel.getLatestSkyLightScale(), String.format(java.util.Locale.ROOT, "%.1f", c.x()), String.format(java.util.Locale.ROOT, "%.1f", c.y()),
                            String.format(java.util.Locale.ROOT, "%.1f", c.z()), String.format(java.util.Locale.ROOT, "%.1f", box.minY()), String.format(java.util.Locale.ROOT, "%.1f", box.maxY()),
                            lvl.getBrightness(net.minecraft.world.level.LightLayer.SKY, BlockPos.containing(c.x(), c.y() + 0.1, c.z())),
                            lvl.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(c.x(), c.y() + 0.1, c.z())));
                    // the plot's own light, which the hull's mesh is built from: sky light over the air cells inside the
                    // plot's bounds (all 15 under open sky), and directly above the plot's top
                    final var pb = subLevel.getPlot().getBoundingBox();
                    int air = 0, dark = 0, skySum = 0;
                    final BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
                    for (int x = pb.minX(); x <= pb.maxX(); x++) for (int y = pb.minY(); y <= pb.maxY(); y++) for (int z = pb.minZ(); z <= pb.maxZ(); z++) {
                        q.set(x, y, z);
                        if (!lvl.getBlockState(q).isAir()) continue;
                        final int sky = lvl.getBrightness(net.minecraft.world.level.LightLayer.SKY, q);
                        air++; skySum += sky; if (sky == 0) dark++;
                    }
                    final int mx = (pb.minX() + pb.maxX()) / 2, mz = (pb.minZ() + pb.maxZ()) / 2;
                    Sable.LOGGER.info("PORT-26.3: sub-level plot light: bounds {} {} {} .. {} {} {}; air cells {}, mean sky {}, sky 0 in {}; sky above the top {}; path {}, ship lightmap {}",
                            pb.minX(), pb.minY(), pb.minZ(), pb.maxX(), pb.maxY(), pb.maxZ(), air,
                            String.format(java.util.Locale.ROOT, "%.2f", air == 0 ? 0.0 : skySum / (double) air), dark,
                            lvl.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(mx, pb.maxY() + 1, mz)),
                            irisPath ? "pack" : (misaligned ? "byte offsets" : "vanilla"), subLevel.getLatestSkyLightScale() >= 15 ? "none (full sky)" : "scaled");
                }
                // sable.debug.hullSkyScale (test-only): the hull's terrain drawn at this sky scale while block entities keep the
                // real one, the dark-hull symptom on purpose (the brightness test's positive control)
                final int sable$forcedScale = Integer.getInteger("sable.debug.hullSkyScale", -1);
                final GpuTextureView lightmap = dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.prepare(
                        sable$forcedScale >= 0 ? sable$forcedScale : subLevel.getLatestSkyLightScale());
                // PORT-26.3: vanilla's draw takes "byte offset / vertex size" as each section's base vertex, which is only
                // right when the section arena is aligned to the meshes' vertex size. Sable's own arena takes its alignment
                // from the vertex format when it is built, which can be Iris's 52-byte one (built during a level render with a
                // pack on) while the meshes are now vanilla's 28 bytes (the pack turned off since). Such a sub-level is drawn
                // at its sections' byte offsets instead, as under a pack (IrisHullPath).
                if (irisPath || misaligned) {
                    IrisHullPath.add(new IrisHullPath.Hull(local, camera.x, camera.y, camera.z, hullOffsets, hullDraws, largestIndexCount, lightmap));
                    if (!irisPath && !sable$warnedMisaligned) {
                        sable$warnedMisaligned = true;
                        Sable.LOGGER.info("PORT-26.3: sub-level sections are not aligned to the {}-byte vertex in the section arena; drawing them at their byte offsets",
                                ChunkSectionLayer.SOLID.vertexFormat().getVertexSize());
                    }
                }
                if (!irisPath && misaligned) {
                    continue;
                }
                final GpuBufferSlice terrainTransform = RenderSystem.getDynamicUniforms().writeTerrainTransform(modelView, atlas.getWidth(0), atlas.getHeight(0));
                final GpuBufferSlice[] sectionUbos = this.sable$sectionInfos.writeData(infos.toArray(new DynamicGpuData.ChunkSectionInfo[0]));
                this.sable$subLevelTerrain.add(new ChunkSectionsToRender.DrawSeparate(terrainTransform, draws, largestIndexCount, sectionUbos));
                this.sable$subLevelLightmaps.add(lightmap);
            }
        } finally {
            dispatcher.unlock();
        }
    }

    @WrapOperation(method = {"executeSolid", "executeClassicTransparency"}, require = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    private void sable$renderSubLevelGroup(final ChunkSectionsToRender instance, final ChunkSectionLayerGroup group, final RenderPass renderPass,
                                           final GpuSampler sampler, final GpuTextureView atlas, final boolean wireframe, final Operation<Void> original) {
        original.call(instance, group, renderPass, sampler, atlas, wireframe);
        if (IrisHullPath.active()) {
            if (!wireframe) {
                IrisHullPath.renderMain(group.layers(), renderPass, sampler, atlas);
            }
            return;
        }
        if (!this.sable$subLevelTerrain.isEmpty()) {
            // PORT-26.3: Iris (installed, even with shaders off) replaces the translucent pass with a fresh one in which
            // nothing is bound; vanilla's terrain draw then finds no Projection, Globals, Lighting or Fog and crashed
            // ("bufferView is null") as soon as a sub-level had translucent blocks. Bind them for every pass we draw into.
            RenderSystem.bindDefaultUniforms(renderPass);
            if (this.sable$terrainFog != null) {
                renderPass.setUniform("Fog", this.sable$terrainFog);
            }
        }
        try {
            for (int i = 0; i < this.sable$subLevelTerrain.size(); i++) {
                dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.setOverride(this.sable$subLevelLightmaps.get(i));
                try {
                    this.sable$subLevelTerrain.get(i).renderGroup(group, renderPass, sampler, atlas, wireframe);
                } catch (final RuntimeException e) {
                    // fail safe: a sub-level that cannot be drawn this frame is skipped, never the client
                    if (!sable$warnedDrawFailure) {
                        sable$warnedDrawFailure = true;
                        Sable.LOGGER.error("PORT-26.3: a sub-level's {} terrain could not be drawn and is skipped (logged once)", group, e);
                    }
                }
            }
        } finally {
            dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.setOverride(null);
        }
        // sub-levels whose sections are not aligned to the vertex size in the arena (see prepare)
        if (!wireframe && IrisHullPath.hasHulls()) {
            IrisHullPath.renderMain(group.layers(), renderPass, sampler, atlas);
        }
    }

    @WrapOperation(method = "executeOit", require = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderOit(Lcom/mojang/renderpearl/api/textures/GpuSampler;Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"))
    private void sable$renderSubLevelOit(final ChunkSectionsToRender instance, final GpuSampler sampler, final OitStage stage,
                                         final OitRenderPassProvider.Parameters params, final GpuTextureView atlas, final GpuTextureView lightmap,
                                         final Operation<Void> original) {
        original.call(instance, sampler, stage, params, atlas, lightmap);
        for (int i = 0; i < this.sable$subLevelTerrain.size(); i++) {
            final com.mojang.renderpearl.api.textures.GpuTextureView own = this.sable$subLevelLightmaps.get(i);
            this.sable$subLevelTerrain.get(i).renderOit(sampler, stage, params, atlas, own != null ? own : lightmap);
        }
    }

    @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
    private void sable$isSectionCompiled(final BlockPos blockPos, final long fadeDuration, final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevelContainer container = this.sable$level() == null ? null : SubLevelContainer.getContainer(this.sable$level());
        if (container == null || !container.inBounds(blockPos)) {
            return;
        }
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.sable$level(), blockPos);
        if (subLevel == null) {
            cir.setReturnValue(false);
            return;
        }
        final SubLevelRenderData renderData = subLevel.getRenderData();
        final SectionPos sectionPos = SectionPos.of(blockPos);
        cir.setReturnValue(renderData.isSectionCompiled(sectionPos.x(), sectionPos.y(), sectionPos.z()));
    }
}
