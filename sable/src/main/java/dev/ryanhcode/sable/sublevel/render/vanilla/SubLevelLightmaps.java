package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PORT-26.3: per-sub-level lightmaps, so a sub-level's terrain loses sky light under cover as its block entities,
 * entities and particles already do (the sub-level's sky-light scale, 0 to 15). Upstream Sable did this with a Veil
 * shader uniform, which the port removed; 26.3's terrain shader takes light only from the lightmap texture, so each
 * sky scale in use gets its own 16x16 copy of the game's lightmap with the sky rows remapped, rendered each frame
 * before the main pass, and bound in place of the game's lightmap while that sub-level's terrain draws.
 *
 * <p>The copy also carries the world's block light where the body is drawn ({@link
 * dev.ryanhcode.sable.sublevel.ClientSubLevel#getBlockLightFloor}) as a floor on the block-light axis, weighted by the
 * plot's sky light (an exposed face takes all of it, a closed room none). With no world block light the copy is the
 * sky remap alone, as before.
 */
public final class SubLevelLightmaps {
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[16];
    /** One copy per (sky scale, block light floor) pair, index {@code scale * 16 + floor}, made on first use. */
    private static final @Nullable GpuTexture[] TEXTURES = new GpuTexture[256];
    private static final @Nullable GpuTextureView[] VIEWS = new GpuTextureView[256];
    /** The client tick each copy was last rendered in ({@link #onClientTick}): a copy is rendered at most once a client
     * tick (the game's lightmap itself changes at most once a client tick), not once a frame. */
    private static final long[] RENDERED_AT = new long[256];

    static {
        java.util.Arrays.fill(RENDERED_AT, Long.MIN_VALUE);
    }
    private static @Nullable GpuTextureView override;

    static {
        for (int scale = 0; scale < 16; scale++) {
            PIPELINES[scale] = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Sable.sablePath("pipeline/sublevel_lightmap_" + scale))
                    .withVertexShader(Sable.sablePath("core/sublevel_lightmap"))
                    .withFragmentShader(Sable.sablePath("core/sublevel_lightmap"))
                    .withShaderDefine("SKY_SCALE", scale)
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .build());
        }
    }

    private SubLevelLightmaps() {
    }

    /** Registers the pipelines (class initialisation); call during client setup, before shaders load. */
    public static void init() {
    }

    public static void beginFrame() {
    }

    /** Client ticks seen (render thread), whatever the server's game time does (frozen, paused, another level). */
    private static long clientTicks;

    /** Once per client tick, on the render thread (the game's own lightmap changes at most once per client tick). */
    public static void onClientTick() {
        clientTicks++;
    }

    /** The level changed (dimension change, reconnect): every copy is rendered afresh on its next use. */
    public static void invalidate() {
        java.util.Arrays.fill(RENDERED_AT, Long.MIN_VALUE);
    }

    private static long tick() {
        return clientTicks;
    }

    /**
     * The lightmap view to draw a sub-level with this sky scale and world block light floor, rendered on first use this
     * frame; null at full sky with no floor (the game's own lightmap). Outside any render pass. The floor reaches the
     * shader as the draw's first vertex (three vertices per floor level), so 16 pipelines cover all 256 pairs.
     */
    public static @Nullable GpuTextureView prepare(final int skyScale, final int blockFloor) {
        final int scale = Math.max(0, Math.min(15, skyScale));
        final int floor = Math.max(0, Math.min(15, blockFloor));
        if (scale == 15 && floor == 0) {
            return null;
        }
        final int index = scale * 16 + floor;
        final long now = tick();
        if (RENDERED_AT[index] != now || TEXTURES[index] == null) {
            RENDERED_AT[index] = now;
            final GpuDevice device = RenderSystem.getDevice();
            if (TEXTURES[index] == null) {
                TEXTURES[index] = device.createTexture("Sable sub-level lightmap " + scale + "/" + floor,
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
                VIEWS[index] = device.createTextureView(TEXTURES[index]);
            }
            final CommandEncoder encoder = device.createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "Sable sub-level lightmap", VIEWS[index], Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[scale]));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", Minecraft.getInstance().gameRenderer.lightmap(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 3 * floor, 0);
            }
        }
        return VIEWS[index];
    }

    /** While set, a sub-level's terrain draw binds this lightmap instead of the game's. */
    public static void setOverride(final @Nullable GpuTextureView view) {
        override = view;
    }

    public static @Nullable GpuTextureView override() {
        return override;
    }
}
