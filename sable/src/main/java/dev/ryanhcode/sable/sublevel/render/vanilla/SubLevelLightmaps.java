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
 */
public final class SubLevelLightmaps {
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[15];
    private static final @Nullable GpuTexture[] TEXTURES = new GpuTexture[15];
    private static final @Nullable GpuTextureView[] VIEWS = new GpuTextureView[15];
    private static final boolean[] USED = new boolean[15];
    private static @Nullable GpuTextureView override;

    static {
        for (int scale = 0; scale < 15; scale++) {
            PIPELINES[scale] = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Sable.sablePath("pipeline/sublevel_lightmap_" + scale))
                    .withVertexShader("core/screenquad")
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
        java.util.Arrays.fill(USED, false);
    }

    /** The lightmap view to draw a sub-level with this sky scale, rendered on first use this frame; null at full sky. Outside any render pass. */
    public static @Nullable GpuTextureView prepare(final int skyScale) {
        if (skyScale >= 15 || skyScale < 0) {
            return null;
        }
        if (!USED[skyScale]) {
            USED[skyScale] = true;
            final GpuDevice device = RenderSystem.getDevice();
            if (TEXTURES[skyScale] == null) {
                TEXTURES[skyScale] = device.createTexture("Sable sub-level lightmap " + skyScale,
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
                VIEWS[skyScale] = device.createTextureView(TEXTURES[skyScale]);
            }
            final CommandEncoder encoder = device.createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(() -> "Sable sub-level lightmap", VIEWS[skyScale], Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINES[skyScale]));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", Minecraft.getInstance().gameRenderer.lightmap(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
        }
        return VIEWS[skyScale];
    }

    /** While set, a sub-level's terrain draw binds this lightmap instead of the game's. */
    public static void setOverride(final @Nullable GpuTextureView view) {
        override = view;
    }

    public static @Nullable GpuTextureView override() {
        return override;
    }
}
