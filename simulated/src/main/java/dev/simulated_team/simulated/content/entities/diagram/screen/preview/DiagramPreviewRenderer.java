package dev.simulated_team.simulated.content.entities.diagram.screen.preview;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4fStack;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * PORT-26.3: the diagram screen's ship preview, through 26.3's picture-in-picture GUI renderers (the mechanism behind
 * the inventory's player preview), replacing the 1.21 Veil framebuffers and post pipeline.
 * <p>
 * Per preview ({@link DiagramFramebuffer}) it owns a colour, a depth and a final texture at the preview's GUI-pixel
 * size (as the 1.21 framebuffers were; the outline shader's border masks are written in those pixels). When the
 * screen marks the view dirty (at the screen's own preview frame rate) the ship's cached block mesh
 * ({@link DiagramShipMesh}) is drawn into colour and depth with the screen's orthographic view, the same one its force
 * arrows are placed with, and the 1.21 outline shader ({@code simulated:core/diagram_outline}) turns them into the
 * paletted, dithered, outlined picture in the final texture. Every GUI frame the final texture is blitted.
 */
public final class DiagramPreviewRenderer extends PictureInPictureRenderer<DiagramPreviewRenderState> {
    private static final Identifier PALETTE = Simulated.path("textures/effects/diagram_palette.png");
    private static final Identifier DITHER = Simulated.path("textures/effects/dither.png");
    private static final BindGroupLayout SAMPLERS = BindGroupLayout.builder()
            .withUniform("DiffuseSampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DiffuseDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Palette", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Dither", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    /** The two looks in use: the diagram screen and its sticky notes (colours, palette row, fade as in 1.21.11). */
    private static final RenderPipeline OUTLINE_SCREEN = outlinePipeline("screen", 0x2E3032, 0x696965, 0.25F, 1.0F);
    private static final RenderPipeline OUTLINE_NOTE = outlinePipeline("note", 0x6e684d, 0x59543e, 0.75F, 1.15F);

    private final Map<DiagramFramebuffer, Targets> targets = new IdentityHashMap<>();
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Simulated diagram preview");
    private final SubmitNodeStorage storage = new SubmitNodeStorage();

    private static RenderPipeline outlinePipeline(final String name, final int lineColor, final int lineShadowColor,
                                                  final float paletteOffset, final float fadeScale) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Simulated.path("pipeline/diagram_outline_" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Simulated.path("core/diagram_outline"))
                .withShaderDefine("LINE_COLOR", lineColor)
                .withShaderDefine("LINE_SHADOW_COLOR", lineShadowColor)
                .withShaderDefine("PALETTE_OFFSET", paletteOffset)
                .withShaderDefine("FADE_SCALE", fadeScale)
                .withBindGroupLayout(SAMPLERS)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build());
    }

    /** Registers the outline pipelines (class initialisation); call during client setup, before shaders load. */
    public static void init() {
    }

    private record Targets(int width, int height, GpuTexture color, GpuTextureView colorView, GpuTexture depth, GpuTextureView depthView,
                           GpuTexture output, GpuTextureView outputView) {
        void close() {
            this.colorView.close();
            this.color.close();
            this.depthView.close();
            this.depth.close();
            this.outputView.close();
            this.output.close();
        }
    }

    @Override
    public void prepare(final DiagramPreviewRenderState state, final GuiRenderState guiRenderState, final FeatureRenderDispatcher dispatcher,
                        final int guiScale) {
        this.dropFreed();
        final DiagramFramebuffer fbo = state.fbo();
        if (Boolean.getBoolean("simulated.debug.diagramLog")) {
            Simulated.LOGGER.info("diagram prepare: fbo {} dirty {} subLevel {}", fbo, fbo.dirty, fbo.subLevel);
        }
        Targets t = this.targets.get(fbo);
        if (t == null) {
            t = create(fbo.width, fbo.height);
            this.targets.put(fbo, t);
            fbo.dirty = true;
        }
        if (fbo.dirty && fbo.subLevel != null && !fbo.subLevel.isRemoved()) {
            fbo.dirty = false;
            this.draw(fbo, t, dispatcher);
        }
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(Boolean.getBoolean("simulated.debug.diagramRaw") ? t.colorView() : t.outputView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                state.pose(), state.x0(), state.y0(), state.x1(), state.y1(),
                0.0F, 1.0F, 1.0F, 0.0F, -1, state.scissorArea()));
    }

    private void draw(final DiagramFramebuffer fbo, final Targets t, final FeatureRenderDispatcher dispatcher) {
        final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(t.color(), GuiRenderer.CLEAR_COLOR, t.depth(), 0.0);
        final DiagramShipMesh mesh = DiagramShipMesh.get(fbo.subLevel);
        if (Boolean.getBoolean("simulated.debug.diagramLog")) {
            Simulated.LOGGER.info("diagram preview draw: {}x{} mesh {} radius {} distance {} camera {} rotation {}", t.width(), t.height(),
                    mesh == null ? "none" : mesh.originX + "," + mesh.originY + "," + mesh.originZ + (mesh.buffer.isEmpty() ? " empty" : ""),
                    fbo.radius, fbo.cameraDistance, fbo.cameraLocal, fbo.viewRotation);
        }
        if (mesh != null) {
            this.projection.setupOrtho(-1000.0F, 1000.0F, t.width(), t.height(), true);
            RenderSystem.setProjectionMatrix(this.projectionBuffer.getBuffer(this.projection), ProjectionType.ORTHOGRAPHIC);
            final Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                // texture pixels per block follow the screen's orthographic projection (+-radius vertically), so the
                // force arrows the screen places with that projection land on the drawn ship
                final float pixelsPerBlock = t.height() / (2.0F * fbo.radius);
                final float zSign = Boolean.getBoolean("simulated.debug.diagramFlipZ") ? 1.0F : -1.0F;
                final PoseStack poseStack = new PoseStack();
                poseStack.translate(t.width() / 2.0F, t.height() / 2.0F, 0.0F);
                // view space has +y up, the GUI target +y down
                poseStack.scale(pixelsPerBlock, -pixelsPerBlock, zSign * pixelsPerBlock);
                poseStack.translate(0.0F, 0.0F, fbo.cameraDistance); // centre the ship in depth
                poseStack.rotate(fbo.viewRotation);
                poseStack.translate((float) (mesh.originX - fbo.cameraLocal.x), (float) (mesh.originY - fbo.cameraLocal.y),
                        (float) (mesh.originZ - fbo.cameraLocal.z));
                mesh.buffer.reset().light(LightCoordsUtil.FULL_BRIGHT).submit(poseStack, this.storage);
                try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(this.storage);
                     RenderPass pass = encoder.createRenderPass(() -> "Simulated diagram preview", t.colorView(), Optional.empty(),
                             t.depthView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
            } finally {
                modelView.popMatrix();
            }
        }
        final var textures = Minecraft.getInstance().getTextureManager();
        final GpuTextureView palette = textures.getTexture(PALETTE).getTextureView();
        final GpuTextureView dither = textures.getTexture(DITHER).getTextureView();
        try (RenderPass pass = encoder.createRenderPass(() -> "Simulated diagram outline", t.outputView(), Optional.of(new org.joml.Vector4f(0, 0, 0, 0)))) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(fbo.paletteOffset > 0.5F ? OUTLINE_NOTE : OUTLINE_SCREEN));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DiffuseSampler0", t.colorView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("DiffuseDepthSampler", t.depthView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("Palette", palette, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("Dither", dither, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static Targets create(final int width, final int height) {
        final GpuDevice device = RenderSystem.getDevice();
        final int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        final GpuTexture color = device.createTexture("Simulated diagram colour", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        final GpuTexture depth = device.createTexture("Simulated diagram depth", usage, GpuFormat.D32_FLOAT, width, height, 1, 1);
        final GpuTexture output = device.createTexture("Simulated diagram", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        return new Targets(width, height, color, device.createTextureView(color), depth, device.createTextureView(depth), output,
                device.createTextureView(output));
    }

    private void dropFreed() {
        final Iterator<Map.Entry<DiagramFramebuffer, Targets>> iterator = this.targets.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<DiagramFramebuffer, Targets> entry = iterator.next();
            if (entry.getKey().freed) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    @Override
    public void close() {
        this.targets.values().forEach(Targets::close);
        this.targets.clear();
        this.projectionBuffer.close();
        super.close();
    }

    @Override
    public Class<DiagramPreviewRenderState> getRenderStateClass() {
        return DiagramPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(final DiagramPreviewRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector) {
        // unused: prepare() is overridden to own its textures and run the outline pass
    }

    @Override
    protected String getTextureLabel() {
        return "simulated_diagram";
    }
}
