package dev.simulated_team.simulated.index;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.zurrtum.create.client.AllSpecialTextures;
import com.zurrtum.create.client.foundation.render.CreateRenderTypes;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * PORT-26.3: 26.3 rebuilt render pipelines (bind-group layouts, snippets, colour-target and depth-stencil states),
 * and the custom laser and see-through lock shaders are written for the old uniform layout. Until those are ported,
 * each type here is the closest vanilla 26.3 render type. Listed in {@code PORT-26.3.md}:
 * <ul>
 *   <li>lasers: the two-pass laser is back on 26.3 pipelines (laserAttenuation/laserEmission); with a shader pack on
 *   they draw as additive lightning;</li>
 *   <li>lock icon: depth-tested text instead of see-through;</li>
 *   <li>staff selection faces: translucent entity on the checkered texture (same look, vanilla pipeline);</li>
 *   <li>translucent block and ghost block previews: vanilla translucent moving block.</li>
 * </ul>
 * The original pipelines are in this file's history on the fabric-1.21.11 branch.
 */
public final class SimRenderTypes {

    private SimRenderTypes() {
    }

    public static RenderType staffOverlay() {
        return RenderTypes.lightning();
    }

    public static RenderType staffSelectionEdge() {
        return RenderTypes.lightning();
    }

    public static RenderType staffSelectionFace() {
        return RenderTypes.entityTranslucentCull(AllSpecialTextures.CHECKERED.getLocation());
    }

    // PORT-26.3: the laser's two commutative passes on 26.3 pipelines (simulated:core/laser). Attenuation darkens what
    // is behind the beam by its alpha, emission adds its colour; submitted in that order, intersecting beams keep
    // stable colours whichever is drawn first (as in 1.21.11). No depth write; the world still occludes the beam.
    private static final RenderPipeline.Snippet LASER_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader(Simulated.path("core/laser"))
            .withFragmentShader(Simulated.path("core/laser"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    private static final RenderPipeline LASER_ATTENUATION_PIPELINE = RenderPipelines.register(RenderPipeline.builder(LASER_SNIPPET)
            .withLocation(Simulated.path("pipeline/laser_attenuation"))
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE)))
            .build());
    private static final RenderPipeline LASER_EMISSION_PIPELINE = RenderPipelines.register(RenderPipeline.builder(LASER_SNIPPET)
            .withLocation(Simulated.path("pipeline/laser_emission"))
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)))
            .build());
    private static final RenderType LASER_ATTENUATION = RenderType.create("simulated_laser_attenuation",
            RenderSetup.builder(LASER_ATTENUATION_PIPELINE).createRenderSetup());
    private static final RenderType LASER_EMISSION = RenderType.create("simulated_laser_emission",
            RenderSetup.builder(LASER_EMISSION_PIPELINE).createRenderSetup());

    public static RenderType laserAttenuation() {
        return LASER_ATTENUATION;
    }

    public static RenderType laserEmission() {
        return LASER_EMISSION;
    }

    /** With a shader pack on: vanilla lightning, which Iris maps to its own program (the custom pipelines are not). */
    public static RenderType laser() {
        return RenderTypes.lightning();
    }

    public static RenderType lateLaser() {
        return RenderTypes.lightning();
    }

    public static RenderType lateLaserAttenuation() {
        return RenderTypes.lightning();
    }

    public static RenderType irisCompositeLaser() {
        return RenderTypes.lightning();
    }

    public static RenderType irisCompositeLaserAttenuation() {
        return RenderTypes.lightning();
    }

    public static RenderType irisCompositeLaserGlow() {
        return RenderTypes.lightning();
    }

    public static RenderType lens() {
        return RenderTypes.cutoutMovingBlock();
    }

    public static RenderType lock() {
        return RenderTypes.text(Simulated.path("textures/gui/lock.png"));
    }

    // Iris cannot classify mod-defined terrain pipelines. A vanilla entity
    // pipeline keeps the standalone rope texture visible in shader passes.
    public static RenderType rope() {
        return RenderTypes.entityCutout(Simulated.path("textures/block/rope_particle.png"));
    }

    public static RenderType blockTranslucent() {
        return RenderTypes.translucentMovingBlock();
    }

    public static RenderType ghostBlock() {
        return RenderTypes.translucentMovingBlock();
    }

    public static RenderType itemGlowingSolid(final boolean shadersActive) {
        return shadersActive ? Sheets.cutoutBlockItemSheet() : CreateRenderTypes.itemGlowingSolid();
    }

    public static RenderType itemGlowingTranslucent(final boolean shadersActive) {
        return shadersActive ? Sheets.translucentBlockItemSheet() : CreateRenderTypes.itemGlowingTranslucent();
    }

    public static RenderType spring(final Identifier texture) {
        // SpringRenderer emits a second, inward-facing copy of the mesh itself;
        // disabling culling would draw both copies on top of each other and z-fight.
        return RenderTypes.entityCutout(texture);
    }
}
