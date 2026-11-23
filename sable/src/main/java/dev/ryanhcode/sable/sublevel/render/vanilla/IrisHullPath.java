package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * PORT-26.3: sub-level terrain while an Iris shader pack is in use (main pass and shadow pass).
 * <p>
 * Sable draws each sub-level's sections with vanilla's terrain pipelines and a {@code TerrainUniform} block
 * ({@code ModelViewMat, TextureSize}) plus a per-section {@code ChunkSection} block. Iris swaps those pipelines for the
 * pack's terrain programs, which read the {@code TerrainUniform} binding as its {@code DynamicTransforms} layout
 * ({@code ModelViewMat, TextureMat, ColorModulator, ModelOffset}) and ignore {@code ChunkSection}: the colour modulator
 * and offset come from past the end of the slice, so hulls vanished. With a pack on, Sable instead draws the same section
 * buffers with the block pipelines (same vertex layout and fragment logic; Iris maps solid and cutout to the same
 * terrain programs) and one {@code DynamicTransforms} slice per section, whose {@code ModelOffset} carries the section
 * origin. Iris has no hook for third-party terrain in its shadow pass other than its public shadow-render callback,
 * so the same draws are repeated there with the shadow model view.
 * <p>
 * Iris is optional: everything here is reflective and inert when it is absent or no pack is in use.
 * Switches (launch-time system properties): {@code sable.iris.hullPath} = {@code block} (default) or {@code terrain}
 * (the old draw, for comparison); {@code sable.iris.hullShadows} = {@code false} to leave hulls out of the shadow pass;
 * {@code sable.iris.hullPhase} = {@code false} to skip setting Iris's render phase;
 * {@code sable.iris.shipLightmaps} = {@code false} binds the game's lightmap instead of the sub-level's sky-scaled one
 * (Iris binds whatever is given as {@code Sampler2} to the lightmap the pack samples); {@code sable.debug.blockHullPath}
 * = {@code true} uses the block path with no pack at all (tests the geometry on vanilla shaders).
 */
public final class IrisHullPath {
    private static final boolean FORCE = Boolean.getBoolean("sable.debug.blockHullPath");
    private static final boolean TERRAIN_PATH = "terrain".equalsIgnoreCase(System.getProperty("sable.iris.hullPath", "block"));
    private static final boolean SHADOWS = !"false".equalsIgnoreCase(System.getProperty("sable.iris.hullShadows", "true"));
    private static final boolean PHASE = !"false".equalsIgnoreCase(System.getProperty("sable.iris.hullPhase", "true"));
    private static final boolean SHIP_LIGHTMAPS = !"false".equalsIgnoreCase(System.getProperty("sable.iris.shipLightmaps", "true"));
    private static final boolean SINGLE_DRAWS = "single".equalsIgnoreCase(System.getProperty("sable.iris.hullDrawMode", "multi"));
    private static boolean drawDiagnostic;
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();

    /** One section's draw of one layer: the slice of the shared section buffers plus the index of its section offset. */
    public record Draw(GpuBuffer vertexBuffer, @Nullable GpuBuffer indexBuffer, @Nullable IndexType indexType,
                       int firstIndex, int indexCount, int baseVertex, int offsetIndex) {
    }

    /**
     * One sub-level's draws for this frame. {@code local} takes plot coordinates (relative to the anchor) to
     * camera-relative world coordinates for the camera at {@code cameraX/Y/Z}; the full model view is base * local.
     */
    public record Hull(Matrix4f local, double cameraX, double cameraY, double cameraZ, List<Vector3f> offsets,
                       EnumMap<ChunkSectionLayer, List<Draw>> draws, int largestIndexCount, @Nullable GpuTextureView lightmap) {
    }

    private static final List<Hull> HULLS = new ArrayList<>();
    private static @Nullable Matrix4f mainBase;
    private static @Nullable GpuBufferSlice terrainFog;

    private static boolean probed;
    private static @Nullable Object api;
    private static @Nullable MethodHandle isPackInUse;
    private static @Nullable MethodHandle getPipelineNullable;
    private static @Nullable MethodHandle setPhase;
    private static @Nullable MethodHandle getPhase;
    private static @Nullable Class<? extends Enum> phaseClass;
    private static boolean phaseFailed;
    private static java.lang.reflect.@Nullable Field shadowFrustum;
    private static final boolean CULL_SHADOWS = !"false".equalsIgnoreCase(System.getProperty("sable.iris.cullShadowSections", "true"));
    private static int shadowSectionsDrawn, shadowSectionsTotal;

    private static int frameState = -1; // -1 unknown, 0 off, 1 on
    private static boolean logNextDraw;
    private static boolean logNextShadow;

    private IrisHullPath() {
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            final Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            isPackInUse = lookup.findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(api);
            final Class<?> callbackClass = Class.forName("net.irisshaders.iris.api.v0.IrisShadowRenderCallback");
            final Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "renderShadow" -> {
                        renderShadow((Matrix4f) args[0], (Double) args[2], (Double) args[3], (Double) args[4]);
                        return null;
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(proxy);
                    }
                    case "equals" -> {
                        return proxy == args[0];
                    }
                    case "toString" -> {
                        return "Sable sub-level shadows";
                    }
                    default -> {
                        return null;
                    }
                }
            });
            apiClass.getMethod("registerShadowRenderCallback", callbackClass).invoke(api, callback);
            Sable.LOGGER.info("PORT-26.3: Iris found; sub-level terrain will use the block pipelines while a shader pack is in use");
        } catch (final ClassNotFoundException e) {
            api = null;
            isPackInUse = null;
            return;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            Sable.LOGGER.error("PORT-26.3: Iris found but its API could not be used; sub-level terrain may not draw with a shader pack", e);
            isPackInUse = null;
            return;
        }
        try {
            shadowFrustum = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer").getField("FRUSTUM");
        } catch (final ReflectiveOperationException | RuntimeException e) {
            Sable.LOGGER.warn("PORT-26.3: Iris's shadow frustum not found; sub-level shadow draws are not culled ({})", e.toString());
        }
        // the render phase (optional: the program choice does not depend on it, only phase-keyed uniforms do)
        try {
            final Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            final Object manager = iris.getMethod("getPipelineManager").invoke(null);
            final Class<?> pipelineClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            @SuppressWarnings("unchecked") final Class<? extends Enum> phase = (Class<? extends Enum>) Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getPipelineNullable = lookup.findVirtual(manager.getClass(), "getPipelineNullable", MethodType.methodType(pipelineClass)).bindTo(manager);
            setPhase = lookup.findVirtual(pipelineClass, "setPhase", MethodType.methodType(void.class, phase));
            getPhase = lookup.findVirtual(pipelineClass, "getPhase", MethodType.methodType(phase));
            phaseClass = phase;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            Sable.LOGGER.warn("PORT-26.3: could not reach Iris's render phase; sub-level terrain draws in phase NONE ({})", e.toString());
            phaseFailed = true;
        }
    }

    /** True while sub-level terrain should take the block path (an Iris pack is in use, or the debug switch is on). */
    public static boolean active() {
        if (FORCE) {
            return true;
        }
        if (TERRAIN_PATH) {
            return false;
        }
        return packInUse();
    }

    public static boolean packInUse() {
        probe();
        final MethodHandle handle = isPackInUse;
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (final Throwable t) {
            isPackInUse = null;
            Sable.LOGGER.error("PORT-26.3: Iris isShaderPackInUse failed; treating shaders as off", t);
            return false;
        }
    }

    /**
     * Starts a frame. Returns true when the block path's on/off state changed since the last frame, so the caller can
     * rebuild sub-level meshes: with a pack on, Iris builds them in its wider terrain vertex format.
     */
    public static boolean beginFrame(final Matrix4fc baseModelView) {
        HULLS.clear();
        mainBase = new Matrix4f(baseModelView);
        final int state = active() ? 1 : 0;
        final boolean changed = frameState != -1 && state != frameState;
        if (state != frameState) {
            logNextDraw = true;
            logNextShadow = true;
            final var format = ChunkSectionLayer.SOLID.pipeline(false).getVertexFormatBinding(0);
            Sable.LOGGER.info("PORT-26.3: sub-level terrain path {} (pack in use: {}, forced: {}, shadows: {}, phase: {}); solid pipeline format {} bytes",
                    state == 1 ? "block" : "terrain", packInUse(), FORCE, SHADOWS, PHASE && !phaseFailed, format.getVertexSize());
        }
        frameState = state;
        return changed;
    }

    /** The level render's terrain fog: Iris's shadow callback runs outside the main pass, where nothing binds {@code Fog}. */
    public static void setTerrainFog(final @Nullable GpuBufferSlice fog) {
        terrainFog = fog;
    }

    public static void add(final Hull hull) {
        HULLS.add(hull);
    }

    public static boolean hasHulls() {
        return !HULLS.isEmpty();
    }

    private static RenderPipeline pipeline(final ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderPipelines.SOLID_BLOCK;
            case CUTOUT -> RenderPipelines.CUTOUT_BLOCK;
            case TRANSLUCENT -> RenderPipelines.TRANSLUCENT_BLOCK;
        };
    }

    private static String phaseName(final ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> "TERRAIN_SOLID";
            case CUTOUT -> "TERRAIN_CUTOUT";
            case TRANSLUCENT -> "TERRAIN_TRANSLUCENT";
        };
    }

    /** Sets Iris's render phase by name and returns the phase it replaced (to restore), or null when not possible. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable Object swapPhase(final @Nullable Object phase) {
        if (!PHASE || phaseFailed || setPhase == null || getPhase == null || getPipelineNullable == null || phaseClass == null || phase == null) {
            return null;
        }
        try {
            final Object pipeline = getPipelineNullable.invoke();
            if (pipeline == null) {
                return null;
            }
            final Object previous = getPhase.invoke(pipeline);
            final Object next = phase instanceof final String name ? Enum.valueOf((Class) phaseClass, name) : phase;
            setPhase.invoke(pipeline, next);
            return previous;
        } catch (final Throwable t) {
            phaseFailed = true;
            Sable.LOGGER.warn("PORT-26.3: setting Iris's render phase failed; sub-level terrain draws in the current phase", t);
            return null;
        }
    }

    /** Draws every hull's layers in {@code layers} into an open main-pass render pass. */
    public static void renderMain(final ChunkSectionLayer[] layers, final RenderPass renderPass, final GpuSampler sampler, final GpuTextureView atlas) {
        if (HULLS.isEmpty() || mainBase == null) {
            return;
        }
        final GpuSampler terrainSampler = packInUse() ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true) : sampler;
        // Iris opens its own pass for translucents (beginTranslucents) with nothing bound; the pack's programs then read
        // empty slots (Projection, Fog, Globals, Lighting). Bind them here for every pass the hull draws into.
        RenderSystem.bindDefaultUniforms(renderPass);
        if (terrainFog != null) {
            renderPass.setUniform("Fog", terrainFog);
        }
        final boolean logNow = diagnosticDue(layers.length > 0 && layers[0] == ChunkSectionLayer.TRANSLUCENT ? 1 : 0);
        drawDiagnostic = logNow;
        if (logNow) {
            Sable.LOGGER.info("PORT-26.3: hull diagnostic: draw mode {}, uniform buffer offset alignment {}, GL error before hull draws 0x{}",
                    SINGLE_DRAWS ? "single" : "multi", org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT),
                    Integer.toHexString(org.lwjgl.opengl.GL11.glGetError()));
        }
        for (final ChunkSectionLayer layer : layers) {
            if (logNow) {
                logLayer(layer);
            }
            final Object previous = swapPhase(phaseName(layer));
            try {
                for (final Hull hull : HULLS) {
                    draw(hull, layer, renderPass, new Matrix4f(mainBase).mul(hull.local()), terrainSampler, atlas, null);
                }
            } finally {
                swapPhase(previous);
            }
        }
        if (logNextDraw) {
            logNextDraw = false;
            int draws = 0;
            for (final Hull hull : HULLS) {
                for (final ChunkSectionLayer layer : layers) {
                    draws += hull.draws().get(layer).size();
                }
            }
            Sable.LOGGER.info("PORT-26.3: sub-level block path drew {} section draws for {} sub-levels ({})", draws, HULLS.size(), java.util.Arrays.toString(layers));
        }
    }

    private static final long[] lastDiagnostic = new long[2];

    /** Every 10 s while a pack is in use (and {@code sable.iris.hullLog} is not false): one diagnostic block. */
    private static boolean diagnosticDue(final int group) {
        if ("false".equalsIgnoreCase(System.getProperty("sable.iris.hullLog")) || !packInUse()) {
            return false;
        }
        final long now = System.currentTimeMillis();
        if (now - lastDiagnostic[group] < 10_000L) {
            return false;
        }
        lastDiagnostic[group] = now;
        return true;
    }

    /** One layer's draws per sub-level: sections, draws, the offsets drawn, and the Iris program key the pipeline maps to. */
    private static void logLayer(final ChunkSectionLayer layer) {
        final StringBuilder b = new StringBuilder();
        int i = 0;
        for (final Hull hull : HULLS) {
            final List<Draw> draws = hull.draws().get(layer);
            b.append(" [sub-level ").append(i++).append(": ").append(hull.offsets().size()).append(" sections, ")
                    .append(draws == null ? 0 : draws.size()).append(" draws");
            if (draws != null) {
                b.append(", offsets");
                for (final Draw d : draws) {
                    final Vector3f o = hull.offsets().get(d.offsetIndex());
                    b.append(String.format(java.util.Locale.ROOT, " (%.1f %.1f %.1f; %d idx)", o.x, o.y, o.z, d.indexCount()));
                }
            }
            b.append(']');
        }
        Sable.LOGGER.info("PORT-26.3: hull diagnostic {} -> pipeline {} -> Iris program {}:{}", layer, pipeline(layer).getLocation(), irisProgram(pipeline(layer)), b);
    }

    /** The Iris shader key a pipeline maps to now (reflective; "?" when not available). */
    private static String irisProgram(final RenderPipeline renderPipeline) {
        try {
            final Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            final Object manager = iris.getMethod("getPipelineManager").invoke(null);
            final Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            if (pipeline == null) {
                return "none";
            }
            final Class<?> pipelines = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
            for (final java.lang.reflect.Method m : pipelines.getMethods()) {
                if (m.getName().equals("getPipeline") && m.getParameterCount() == 2) {
                    final Object key = m.invoke(null, pipeline, renderPipeline);
                    if (key == null) {
                        return "null (no override: vanilla program)";
                    }
                    String program = "?";
                    try {
                        final Object map = pipeline.getClass().getMethod("getShaderMap").invoke(pipeline);
                        final Object p = map.getClass().getMethod("getShader", key.getClass()).invoke(map, key);
                        program = p == null ? "missing" : p.getClass().getSimpleName();
                    } catch (final ReflectiveOperationException | RuntimeException ignored) {
                    }
                    return key + " (" + program + ")";
                }
            }
            return "?";
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "? (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static void renderShadow(final Matrix4f shadowModelView, final double cameraX, final double cameraY, final double cameraZ) {
        if (!SHADOWS || HULLS.isEmpty() || !active()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final GpuTextureView atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        final GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);
        final var target = minecraft.gameRenderer.mainRenderTarget();
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Sable sub-level shadows",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            // a new pass binds nothing: Projection (Iris's shadow projection at this point), Globals, Lighting, Fog
            RenderSystem.bindDefaultUniforms(renderPass);
            if (terrainFog != null) {
                renderPass.setUniform("Fog", terrainFog);
            }
            final net.minecraft.client.renderer.culling.Frustum frustum = shadowFrustum();
            final java.util.Map<Hull, boolean[]> visible = new java.util.IdentityHashMap<>();
            shadowSectionsDrawn = 0;
            shadowSectionsTotal = 0;
            for (final Hull hull : HULLS) {
                final boolean[] v = sectionsVisible(hull, frustum);
                visible.put(hull, v);
                for (final boolean b : v) {
                    shadowSectionsTotal++;
                    if (b) shadowSectionsDrawn++;
                }
            }
            for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                for (final Hull hull : HULLS) {
                    // local is relative to the camera the frame was prepared with; shift to the shadow pass's camera
                    final Matrix4f modelView = new Matrix4f(shadowModelView)
                            .translate((float) (hull.cameraX() - cameraX), (float) (hull.cameraY() - cameraY), (float) (hull.cameraZ() - cameraZ))
                            .mul(hull.local());
                    draw(hull, layer, renderPass, modelView, sampler, atlas, visible.get(hull));
                }
            }
        }
        if (logNextShadow) {
            logNextShadow = false;
            Sable.LOGGER.info("PORT-26.3: sub-level hulls drawn into Iris's shadow pass ({} sub-levels, {} of {} sections in the shadow frustum, camera offset {} {} {})", HULLS.size(),
                    shadowSectionsDrawn, shadowSectionsTotal,
                    String.format(java.util.Locale.ROOT, "%.3f", HULLS.get(0).cameraX() - cameraX), String.format(java.util.Locale.ROOT, "%.3f", HULLS.get(0).cameraY() - cameraY),
                    String.format(java.util.Locale.ROOT, "%.3f", HULLS.get(0).cameraZ() - cameraZ));
        }
    }

    private static net.minecraft.client.renderer.culling.@Nullable Frustum shadowFrustum() {
        if (!CULL_SHADOWS || shadowFrustum == null) {
            return null;
        }
        try {
            return (net.minecraft.client.renderer.culling.Frustum) shadowFrustum.get(null);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            shadowFrustum = null;
            return null;
        }
    }

    /**
     * Which of a hull's sections the shadow frustum can see: each 16-block section box, carried through the hull's
     * transform to the world as the box around its eight corners (conservative under rotation and scale).
     */
    private static boolean[] sectionsVisible(final Hull hull, final net.minecraft.client.renderer.culling.@Nullable Frustum frustum) {
        final boolean[] visible = new boolean[hull.offsets().size()];
        if (frustum == null) {
            java.util.Arrays.fill(visible, true);
            return visible;
        }
        final Vector3f corner = new Vector3f();
        for (int i = 0; i < visible.length; i++) {
            final Vector3f o = hull.offsets().get(i);
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (int c = 0; c < 8; c++) {
                hull.local().transformPosition(corner.set(o.x + ((c & 1) * 16), o.y + (((c >> 1) & 1) * 16), o.z + (((c >> 2) & 1) * 16)));
                minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
            }
            visible[i] = frustum.isVisible(new net.minecraft.world.phys.AABB(minX + hull.cameraX(), minY + hull.cameraY(), minZ + hull.cameraZ(),
                    maxX + hull.cameraX(), maxY + hull.cameraY(), maxZ + hull.cameraZ()));
        }
        return visible;
    }

    private static void draw(final Hull hull, final ChunkSectionLayer layer, final RenderPass renderPass, final Matrix4f modelView,
                             final GpuSampler sampler, final GpuTextureView atlas, final boolean @Nullable [] visible) {
        List<Draw> draws = hull.draws().get(layer);
        if (draws == null || draws.isEmpty()) {
            return;
        }
        if (visible != null) {
            final List<Draw> kept = new ArrayList<>(draws.size());
            for (final Draw draw : draws) {
                if (visible[draw.offsetIndex()]) {
                    kept.add(draw);
                }
            }
            if (kept.isEmpty()) {
                return;
            }
            draws = kept;
        }
        final DynamicGpuData.Transform[] transforms = new DynamicGpuData.Transform[hull.offsets().size()];
        for (int i = 0; i < transforms.length; i++) {
            transforms[i] = new DynamicGpuData.Transform(modelView, WHITE, hull.offsets().get(i), IDENTITY);
        }
        final GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(transforms);
        final List<RenderPass.Draw<GpuBufferSlice[]>> passDraws = new ArrayList<>(draws.size());
        for (final Draw draw : draws) {
            final int index = draw.offsetIndex();
            passDraws.add(new RenderPass.Draw<>(0, draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(), draw.firstIndex(), draw.indexCount(),
                    draw.baseVertex(), (ubos, uploader) -> uploader.setUniform("DynamicTransforms", ubos[index])));
        }
        final RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        if (hull.largestIndexCount() != 0) {
            sequential.requestIndexCount(hull.largestIndexCount());
        }
        final GpuBuffer sequentialBuffer = hull.largestIndexCount() == 0 ? null : sequential.getBuffer();
        final IndexType sequentialType = hull.largestIndexCount() == 0 ? null : sequential.type();

        renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(layer)));
        renderPass.setUniform("Sampler0", atlas, sampler);
        final GpuTextureView lightmap = SHIP_LIGHTMAPS && hull.lightmap() != null ? hull.lightmap() : Minecraft.getInstance().gameRenderer.lightmap();
        renderPass.setUniform("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if (SINGLE_DRAWS) {
            // one draw per section with the transform bound on the pass (as vanilla draws entities), no per-draw uniforms
            for (int i = 0; i < passDraws.size(); i++) {
                final RenderPass.Draw<GpuBufferSlice[]> d = passDraws.get(i);
                renderPass.setUniform("DynamicTransforms", slices[draws.get(i).offsetIndex()]);
                renderPass.drawMultipleIndexed(List.of(new RenderPass.Draw<GpuBufferSlice[]>(d.slot(), d.vertexBuffer(), d.indexBuffer(), d.indexType(),
                        d.firstIndex(), d.indexCount(), d.baseVertex())), sequentialBuffer, sequentialType, List.of(), slices);
            }
        } else {
            renderPass.drawMultipleIndexed(passDraws, sequentialBuffer, sequentialType, List.of("DynamicTransforms"), slices);
        }
        if (drawDiagnostic) {
            final StringBuilder b = new StringBuilder();
            for (final Draw d : draws) {
                final GpuBufferSlice slice = slices[d.offsetIndex()];
                b.append(" section ").append(d.offsetIndex()).append(": ubo offset ").append(slice.offset()).append(" length ").append(slice.length());
            }
            Sable.LOGGER.info("PORT-26.3: hull diagnostic {} draws:{}; GL error after 0x{}", layer, b, Integer.toHexString(org.lwjgl.opengl.GL11.glGetError()));
        }
    }
}
