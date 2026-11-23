package dev.simulated_team.simulated.content.physics_staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.zurrtum.create.client.catnip.animation.AnimationTickHolder;
import com.zurrtum.create.client.foundation.model.BakedModelHelper;
import com.zurrtum.create.client.foundation.render.CreateRenderTypes;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.client.render.FirstPersonItemFocus;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.SimMathUtils;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3fc;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * PORT-26.3: the animated physics staff on 26.3's item model API, in the shape of Create Fly's own animated items
 * (layers with a local transform; glowing parts on Create's item-glowing render types at full brightness). The static
 * parts (body, core, ring, the two sigma petals) are layers with a local transform; the two cubes are drawn by a small
 * special renderer because they need the final pose at submit time, for the first-person orientation and for the
 * beam's focus capture. The animation itself is unchanged from the 1.21.11 model.
 */
public final class PhysicsStaffItemModel implements ItemModel {
    public static final Identifier ID = Simulated.path("model/creative_physics_staff");
    private static final Identifier ITEM = Simulated.path("item/creative_physics_staff/item");
    private static final Identifier CORE = Simulated.path("item/creative_physics_staff/core");
    private static final Identifier CORE_GLOW = Simulated.path("item/creative_physics_staff/core_glow");
    private static final Identifier RING = Simulated.path("item/creative_physics_staff/ring");
    private static final Identifier SIGMA = Simulated.path("item/creative_physics_staff/sigma");
    private static final Identifier INNER_CUBE = Simulated.path("item/creative_physics_staff/inner_cube");
    private static final Identifier OUTER_CUBE = Simulated.path("item/creative_physics_staff/outer_cube");
    private static final int[] TINTS = new int[]{-1};

    private final ModelRenderProperties settings;
    private final ItemQuads item;
    private final ItemQuads core;
    private final ItemQuads coreGlow;
    private final ItemQuads ring;
    private final ItemQuads sigma;
    private final ItemQuads innerCube;
    private final ItemQuads outerCube;

    private PhysicsStaffItemModel(final ModelRenderProperties settings, final ItemQuads item, final ItemQuads core, final ItemQuads coreGlow,
                                  final ItemQuads ring, final ItemQuads sigma, final ItemQuads innerCube, final ItemQuads outerCube) {
        this.settings = settings;
        this.item = item;
        this.core = core;
        this.coreGlow = coreGlow;
        this.ring = ring;
        this.sigma = sigma;
        this.innerCube = innerCube;
        this.outerCube = outerCube;
    }

    @Override
    public void update(final ItemStackRenderState state, final ItemStack stack, final ItemModelResolver resolver, final ItemDisplayContext displayContext,
                       @Nullable final ClientLevel level, @Nullable final ItemOwner owner, final int seed) {
        state.appendModelIdentityElement(this);
        state.setAnimated();

        final Minecraft minecraft = Minecraft.getInstance();
        final PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;
        final Player player = SimDistUtil.getClientPlayer();
        final float partialTicks = AnimationTickHolder.getPartialTicks();
        final StaffAnimation animation = animation(stack, displayContext, handler, player, partialTicks);
        final boolean captureBodyFocus = FirstPersonItemFocus.isLocalPlayerBodyRender(displayContext, owner, minecraft);
        final Matrix4f common = commonTransform(displayContext, handler, player, minecraft, partialTicks, animation.tilt());
        final int fullBright = LightCoordsUtil.FULL_BRIGHT;

        layer(state, displayContext, this.item, centred(common, null));
        customLayer(state, displayContext, fullBright, this.core, centred(common, null));
        customLayer(state, displayContext, fullBright, this.coreGlow, centred(common, null));
        layer(state, displayContext, this.ring, centred(common, m -> m.translate(0, 6.5f / 16, 0)));
        for (int side = 0; side < 2; side++) {
            final int currentSide = side;
            layer(state, displayContext, this.sigma, centred(common, m -> m
                    .translate(0, 9f / 16, 0)
                    .rotate(Axis.YP.rotationDegrees(currentSide * 180))
                    .translate(-3f / 16, 0, 0)
                    .rotate(Axis.ZP.rotationDegrees(animation.openAmount() * 20))));
        }

        // the cubes: centre, common transform and lift here; orientation, scale and focus at submit time
        final Matrix4f cubeBase = new Matrix4f().translate(0.5f, 0.5f, 0.5f).mul(common).translate(0, 15f / 16, 0);
        final boolean firstPerson = displayContext.firstPerson();
        final ItemStackRenderState.LayerRenderState inner = state.newLayer();
        this.settings.applyToLayer(inner, displayContext);
        inner.setLocalTransform(cubeBase);
        inner.setupSpecialModel(new CubeLayer(this.innerCube, firstPerson, animation.cubeScale(), firstPerson || captureBodyFocus, partialTicks), null);
        final ItemStackRenderState.LayerRenderState outer = state.newLayer();
        this.settings.applyToLayer(outer, displayContext);
        outer.setLocalTransform(cubeBase);
        outer.setupSpecialModel(new CubeLayer(this.outerCube, firstPerson, animation.cubeScale() * 1.2f, false, partialTicks), null);
    }

    private void layer(final ItemStackRenderState state, final ItemDisplayContext displayContext, final ItemQuads quads, final Matrix4fc transform) {
        final ItemStackRenderState.LayerRenderState layer = state.newLayer();
        this.settings.applyToLayer(layer, displayContext);
        layer.setQuads(quads);
        layer.setLocalTransform(transform);
    }

    private void customLayer(final ItemStackRenderState state, final ItemDisplayContext displayContext, final int light, final ItemQuads quads,
                             final Matrix4fc transform) {
        final ItemStackRenderState.LayerRenderState layer = state.newLayer();
        this.settings.applyToLayer(layer, displayContext);
        layer.setLocalTransform(transform);
        layer.setupSpecialModel(new LitLayer(quads, light), null);
    }

    /** {@code T(0.5) * common * extra * T(-0.5)}: the 1.21 renderer applied its transforms about the item's centre. */
    private static Matrix4f centred(final Matrix4fc common, final @Nullable Consumer<Matrix4f> extra) {
        final Matrix4f m = new Matrix4f().translate(0.5f, 0.5f, 0.5f).mul(common);
        if (extra != null) {
            extra.accept(m);
        }
        return m.translate(-0.5f, -0.5f, -0.5f);
    }

    private StaffAnimation animation(final ItemStack stack, final ItemDisplayContext displayContext, final PhysicsStaffClientHandler handler,
                                     @Nullable final Player player, final float partialTicks) {
        float openAmount = 0;
        float cubeScale = 0;
        final boolean held = displayContext.firstPerson()
                || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        if (player != null && held) {
            if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                openAmount = Mth.lerp(partialTicks, handler.previousExtension, handler.extension);
                cubeScale = Mth.lerp(partialTicks, handler.previousCubeScale, handler.cubeScale);
            } else if (Minecraft.getInstance().level != null) {
                for (final UUID playerId : handler.beams.keySet()) {
                    final Player otherPlayer = Minecraft.getInstance().level.getPlayerByUUID(playerId);
                    if (otherPlayer != null && (otherPlayer.getMainHandItem() == stack || otherPlayer.getOffhandItem() == stack)) {
                        final PhysicsStaffClientHandler.PhysicsBeam beam = handler.beams.get(playerId);
                        openAmount = Mth.lerp(partialTicks, beam.previousExtension, beam.extension);
                        cubeScale = Mth.lerp(partialTicks, beam.previousCubeScale, beam.cubeScale);
                        break;
                    }
                }
            }
        }

        cubeScale = Mth.clamp(Mth.lerp(cubeScale, -0.05f, 1), 0, 1) * 0.8f;
        final float tilt = Mth.lerp(partialTicks, handler.previousTilt, handler.tilt);
        return new StaffAnimation(openAmount, cubeScale, tilt);
    }

    private static Matrix4f commonTransform(final ItemDisplayContext displayContext, final PhysicsStaffClientHandler handler, @Nullable final Player player,
                                            final Minecraft minecraft, final float partialTicks, final float tilt) {
        final Matrix4f matrix = new Matrix4f();
        if (!displayContext.firstPerson()) {
            return matrix;
        }
        final PhysicsStaffClientHandler.ClientDragSession dragSession = handler.getDragSession();
        final Quaternionf quaternion = new Quaternionf();
        if (dragSession != null && player != null) {
            final Quaternionf cameraRotation = minecraft.gameRenderer.mainCamera().rotation();
            final Vector3d globalAnchor = ((ClientSubLevel) dragSession.dragSubLevel()).renderPose()
                    .transformPosition(new Vector3d(dragSession.dragLocalAnchor()));
            final Vector3d direction = globalAnchor.sub(JOMLConversion.toJOML(player.getEyePosition(partialTicks))).normalize();
            cameraRotation.transformInverse(direction);
            final Quaternionf aim = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 0, -1), direction);
            matrix.rotate(quaternion.identity().rotateY(-Mth.HALF_PI));
            matrix.rotate(aim.slerp(quaternion.identity(), 0.6f));
            matrix.rotate(quaternion.identity().rotateY(Mth.HALF_PI));
        }
        final float handMultiplier = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;
        matrix.rotate(quaternion.identity().rotateZ((float) Math.toRadians((tilt * 0.5f + 0.5f) * -61) * handMultiplier));
        return matrix;
    }

    private record StaffAnimation(float openAmount, float cubeScale, float tilt) {
    }

    /** A layer of item quads drawn at a fixed light (the glowing core), as Create Fly's custom item layers do. */
    private record LitLayer(ItemQuads quads, int light) implements SpecialModelRenderer<Object> {
        @Override
        public void submit(@Nullable final Object argument, final PoseStack matrices, final SubmitNodeCollector queue, final int lightCoords,
                           final int overlayCoords, final boolean hasFoil, final int outlineColor) {
            queue.submitItem(matrices, ItemDisplayContext.NONE, this.light, overlayCoords, outlineColor, TINTS, this.quads,
                    ItemStackRenderState.FoilType.NONE);
        }

        @Override
        public void getExtents(final Consumer<Vector3fc> output) {
        }

        @Override
        public Object extractArgument(final ItemStack stack) {
            return null;
        }
    }

    /** The cubes: first-person orientation from the drag session, scale, and (inner cube) the beam's focus capture. */
    private record CubeLayer(ItemQuads quads, boolean firstPerson, float scale, boolean captureFocus, float partialTicks)
            implements SpecialModelRenderer<Object> {
        @Override
        public void submit(@Nullable final Object argument, final PoseStack matrices, final SubmitNodeCollector queue, final int lightCoords,
                           final int overlayCoords, final boolean hasFoil, final int outlineColor) {
            matrices.pushPose();
            if (this.firstPerson) {
                final PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;
                final PhysicsStaffClientHandler.ClientDragSession dragSession = handler.getDragSession();
                if (dragSession != null) {
                    handler.lastCubeOrientation.set(dragSession.dragOrientation());
                }
                final Matrix4f orientation = new Matrix4f(matrices.last().pose());
                orientation.m30(0).m31(0).m32(0);
                orientation.invert();
                orientation.rotate(handler.lastCubeOrientation);
                matrices.mulPose(orientation);
            }
            matrices.scale(this.scale, this.scale, this.scale);
            if (this.captureFocus) {
                if (this.firstPerson) {
                    PhysicsStaffItemRenderer.captureFirstPersonFocus(matrices, Minecraft.getInstance(), this.partialTicks);
                } else {
                    PhysicsStaffItemRenderer.captureFirstPersonBodyFocus(matrices);
                }
            }
            matrices.translate(-0.5f, -0.5f, -0.5f);
            queue.submitItem(matrices, ItemDisplayContext.NONE, LightCoordsUtil.FULL_BRIGHT, overlayCoords, outlineColor, TINTS, this.quads,
                    ItemStackRenderState.FoilType.NONE);
            matrices.popPose();
        }

        @Override
        public void getExtents(final Consumer<Vector3fc> output) {
        }

        @Override
        public Object extractArgument(final ItemStack stack) {
            return null;
        }
    }

    public static final class Unbaked implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(final Resolver resolver) {
            resolver.markDependency(ITEM);
            resolver.markDependency(CORE);
            resolver.markDependency(CORE_GLOW);
            resolver.markDependency(RING);
            resolver.markDependency(SIGMA);
            resolver.markDependency(INNER_CUBE);
            resolver.markDependency(OUTER_CUBE);
        }

        @Override
        public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation) {
            final ModelBaker baker = context.blockModelBaker();
            final ResolvedModel model = baker.getModel(ITEM);
            final TextureSlots textures = model.getTopTextureSlots();
            final ModelRenderProperties settings = ModelRenderProperties.fromResolvedModel(baker, model, textures);
            return new PhysicsStaffItemModel(
                    settings,
                    BakedModelHelper.bakeItemQuads(baker, ITEM),
                    BakedModelHelper.replaceItemQuadLayer(BakedModelHelper.bakeQuads(baker, CORE), ChunkSectionLayer.SOLID, CreateRenderTypes.itemGlowingSolid()),
                    BakedModelHelper.replaceItemQuadLayer(BakedModelHelper.bakeQuads(baker, CORE_GLOW), ChunkSectionLayer.TRANSLUCENT, CreateRenderTypes.itemGlowingTranslucent()),
                    BakedModelHelper.bakeItemQuads(baker, RING),
                    BakedModelHelper.bakeItemQuads(baker, SIGMA),
                    BakedModelHelper.replaceItemQuadLayer(BakedModelHelper.bakeQuads(baker, INNER_CUBE), ChunkSectionLayer.SOLID, CreateRenderTypes.itemGlowingSolid()),
                    BakedModelHelper.replaceItemQuadLayer(BakedModelHelper.bakeQuads(baker, OUTER_CUBE), ChunkSectionLayer.TRANSLUCENT, CreateRenderTypes.itemGlowingTranslucent())
            );
        }
    }
}
