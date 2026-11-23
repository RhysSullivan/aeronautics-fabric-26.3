package dev.eriksonn.aeronautics.index.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class AeroRenderTypes {
    public static final Identifier LEVITITE_SHADER = Aeronautics.path("levitite/levitite");
    private static final Identifier FIRE_PALETTE =
            Aeronautics.path("textures/effects/fire_palette.png");

    private AeroRenderTypes() {
    }

    public static void init() {
        // Force pipeline registration before Minecraft's first shader reload.
    }

    // PORT-26.3: the burner flame's core shader (aeronautics:core/burner_flame: palette-textured, animated, its
    // parameters packed into the vertex colour) on a 26.3 pipeline, as in 1.21.11: no blending (the shader discards
    // what is not flame), depth tested and written, no culling.
    private static final RenderPipeline BURNER_FLAME_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Aeronautics.path("pipeline/burner_flame"))
            .withVertexShader(Aeronautics.path("core/burner_flame"))
            .withFragmentShader(Aeronautics.path("core/burner_flame"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build());
    private static final RenderType BURNER_FLAME = RenderType.create("aeronautics_burner_flame",
            RenderSetup.builder(BURNER_FLAME_PIPELINE).withTexture("Sampler0", FIRE_PALETTE).createRenderSetup());

    public static RenderType burnerFlame() {
        return BURNER_FLAME;
    }

    /** With a shader pack on: the flame as vanilla's animated fire sprite on plain entity geometry, which Iris handles. */
    public static RenderType burnerFlameShaderPackFallback() {
        return RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    public static RenderType irisCompositeBurnerFlame() {
        return burnerFlameShaderPackFallback();
    }

    public static RenderType levitite() {
        return RenderTypes.translucentMovingBlock();
    }

    public static RenderType levititeGhosts() {
        return RenderTypes.translucentMovingBlock();
    }
}
