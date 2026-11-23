package dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.util.LightCoordsUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.compat.create.RenderBridge;
import dev.simulated_team.simulated.compat.create.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.content.physics_staff.OptionalShaderMods;
import dev.simulated_team.simulated.util.SimColors;
import com.zurrtum.create.client.catnip.render.CachedBuffers;
import com.zurrtum.create.client.catnip.render.SuperByteBuffer;
import com.zurrtum.create.client.ponder.api.level.PonderLevel;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import net.minecraft.client.Minecraft;
import dev.simulated_team.simulated.compat.minecraft.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class HotAirBurnerRenderer extends SmartBlockEntityRenderer<HotAirBurnerBlockEntity> {
    private static final float FLAME_ANIMATION_PERIOD = 10.0F / 3.0F;
    private static final float FLAME_SIZE = 2.0F;
    private static final float FLAME_QUAD_Y = 4.0F / 16.0F;

    public HotAirBurnerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final HotAirBurnerBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final float signalStrength = Math.max(0, be.getSignalStrength() / 15F);
        final SuperByteBuffer indicator = CachedBuffers.partial(AeroPartialModels.HOT_AIR_BURNER_INDICATOR, be.getBlockState());
        final VertexConsumer vb = buffer.getBuffer(RenderTypes.cutoutMovingBlock());
        indicator.light(light)
                .color(SimColors.redstone(signalStrength))
                .renderInto(ms.last(), vb);

        if (signalStrength <= 0.0) {
            return;
        }

        ms.pushPose();
        // The shader trims the bottom edge of the quad. Starting one model
        // pixel below the burner plate keeps the visible flame rooted on it.
        ms.translate(-0.5, FLAME_QUAD_Y, 0.5);

        final BlockPos pos = be.getBlockPos();
        final Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        final Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        if (be.getLevel() instanceof PonderLevel && minecraft.getCameraEntity() != null) {
            camera = minecraft.getCameraEntity().getPosition(partialTicks);
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(be);
        if (subLevel != null) {
            camera = subLevel.logicalPose().transformPositionInverse(camera);
        }

        final float angle = (float) Math.atan2(
                camera.z() - center.z(),
                camera.x() - center.x()
        );
        final HotAirBurnerBlock.Variant variant =
                be.getBlockState().getValue(HotAirBurnerBlock.VARIANT);
        final float palette =
                variant == HotAirBurnerBlock.Variant.FIRE ? 0.25F : 0.75F;
        final float flameRenderTime =
                (float) Mth.lerp(partialTicks, be.lastRenderTime, be.renderTime)
                        + be.getTimeOffset();
        final float animationPhase = positiveModulo(
                flameRenderTime,
                FLAME_ANIMATION_PERIOD
        ) / FLAME_ANIMATION_PERIOD;

        ms.translate(1.0F, 0.0F, 0.0F);
        ms.rotate(Axis.YP.rotation((float) (-angle + Math.PI * 0.5F)));
        ms.translate(-1.0F, 0.0F, 0.0F);
        renderFlameOrEnqueue(
                ms.last().pose(),
                buffer,
                animationPhase,
                be.getFlameIntensity(partialTicks),
                palette
        );
        ms.popPose();

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    private static float positiveModulo(final float value, final float modulus) {
        final float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private static void renderFlameOrEnqueue(
            final Matrix4f pose,
            final MultiBufferSource buffer,
            final float animationPhase,
            final float intensity,
            final float palette
    ) {
        // PORT-26.3: the flame's own shader on a 26.3 pipeline. With a shader pack on, Iris would not know that
        // pipeline (and the flame's parameters live in its vertex colours, so a stand-in shader shows a flat wedge):
        // draw vanilla's animated fire sprite instead, on plain entity geometry that Iris lights like any other.
        if (OptionalShaderMods.isShaderPackActive()) {
            // a world frame: queued and drawn with the flame's own shader after the pack's final pass
            if (IrisBurnerFlameRenderQueue.enqueue(new Matrix4f(pose), animationPhase, intensity, palette)) {
                return;
            }
            renderFallbackFlame(pose, buffer.getBuffer(AeroRenderTypes.burnerFlameShaderPackFallback()), palette);
            return;
        }
        renderFlame(
                pose,
                buffer.getBuffer(AeroRenderTypes.burnerFlame()),
                animationPhase,
                intensity,
                palette
        );
    }

    private static final SpriteId FIRE = ModelBakery.FIRE_0;
    private static final SpriteId SOUL_FIRE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("soul_fire_0");

    /** A one-block-wide fire sprite standing on the burner, in the flame's billboard space (the flame quad is 2 x 2). */
    private static void renderFallbackFlame(final Matrix4f pose, final VertexConsumer consumer, final float palette) {
        final TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(palette < 0.5F ? FIRE : SOUL_FIRE);
        final float x0 = 0.55F, x1 = 1.45F, y0 = 0.35F, y1 = 1.65F; // above the cage, about as tall as the shader flame
        fallbackVertex(consumer, pose, x0, y0, sprite.getU0(), sprite.getV1());
        fallbackVertex(consumer, pose, x1, y0, sprite.getU1(), sprite.getV1());
        fallbackVertex(consumer, pose, x1, y1, sprite.getU1(), sprite.getV0());
        fallbackVertex(consumer, pose, x0, y1, sprite.getU0(), sprite.getV0());
    }

    private static void fallbackVertex(final VertexConsumer consumer, final Matrix4f pose, final float x, final float y, final float u, final float v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    static void renderFlame(
            final Matrix4f pose,
            final VertexConsumer consumer,
            final float animationPhase,
            final float intensity,
            final float palette
    ) {
        // Keep each queued quad self-contained. Ponder and Sable can replay
        // buffered vertices, but cannot safely replay per-draw shader uniforms.
        addFlameVertex(
                consumer,
                pose,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                1.0F,
                animationPhase,
                intensity,
                palette
        );
        addFlameVertex(
                consumer,
                pose,
                FLAME_SIZE,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                animationPhase,
                intensity,
                palette
        );
        addFlameVertex(
                consumer,
                pose,
                FLAME_SIZE,
                FLAME_SIZE,
                0.0F,
                1.0F,
                0.0F,
                animationPhase,
                intensity,
                palette
        );
        addFlameVertex(
                consumer,
                pose,
                0.0F,
                FLAME_SIZE,
                0.0F,
                0.0F,
                0.0F,
                animationPhase,
                intensity,
                palette
        );
    }

    private static void addFlameVertex(
            final VertexConsumer consumer,
            final Matrix4f pose,
            final float x,
            final float y,
            final float z,
            final float u,
            final float v,
            final float animationPhase,
            final float intensity,
            final float palette
    ) {
        consumer.addVertex(pose, x, y, z)
                .setUv(u, v)
                .setColor(animationPhase, intensity, palette, 1.0F);
    }
}
