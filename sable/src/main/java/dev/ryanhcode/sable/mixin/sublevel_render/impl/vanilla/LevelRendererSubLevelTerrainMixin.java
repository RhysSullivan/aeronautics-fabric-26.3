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
                final List<org.joml.Vector3f> hullOffsets = irisPath ? new ArrayList<>() : null;
                final EnumMap<ChunkSectionLayer, List<IrisHullPath.Draw>> hullDraws = irisPath ? new EnumMap<>(ChunkSectionLayer.class) : null;
                if (irisPath) {
                    for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        hullDraws.put(layer, new ArrayList<>());
                    }
                }

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
                            if (irisPath) {
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
                        if (irisPath) {
                            hullDraws.get(layer).add(new IrisHullPath.Draw(slice.vertexBuffer(), indexBuffer, indexType, firstIndex, draw.indexCount(), baseVertex, index));
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
                final GpuBufferSlice terrainTransform = RenderSystem.getDynamicUniforms().writeTerrainTransform(modelView, atlas.getWidth(0), atlas.getHeight(0));
                final GpuBufferSlice[] sectionUbos = this.sable$sectionInfos.writeData(infos.toArray(new DynamicGpuData.ChunkSectionInfo[0]));
                this.sable$subLevelTerrain.add(new ChunkSectionsToRender.DrawSeparate(terrainTransform, draws, largestIndexCount, sectionUbos));
                final GpuTextureView lightmap = dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.prepare(subLevel.getLatestSkyLightScale());
                this.sable$subLevelLightmaps.add(lightmap);
                if (irisPath) {
                    IrisHullPath.add(new IrisHullPath.Hull(local, camera.x, camera.y, camera.z, hullOffsets, hullDraws, largestIndexCount, lightmap));
                }
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
        try {
            for (int i = 0; i < this.sable$subLevelTerrain.size(); i++) {
                dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.setOverride(this.sable$subLevelLightmaps.get(i));
                this.sable$subLevelTerrain.get(i).renderGroup(group, renderPass, sampler, atlas, wireframe);
            }
        } finally {
            dev.ryanhcode.sable.sublevel.render.vanilla.SubLevelLightmaps.setOverride(null);
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
