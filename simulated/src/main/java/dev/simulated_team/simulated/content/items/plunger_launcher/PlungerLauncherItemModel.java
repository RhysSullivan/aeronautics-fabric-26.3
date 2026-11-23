package dev.simulated_team.simulated.content.items.plunger_launcher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.zurrtum.create.client.foundation.model.BakedModelHelper;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.client.render.FirstPersonItemFocus;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import dev.simulated_team.simulated.mixin_interface.PlayerLaunchedPlungerExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * PORT-26.3: the plunger launcher with its animated plungers and tether spool, on 26.3's item model API. The launcher
 * body is drawn by a small special renderer so the rope's focus point can be captured from the final pose (as the
 * 1.21.11 model did); the two plungers and their spool parts are layers with a local transform. The reload animation
 * and the rules for which plunger shows are unchanged.
 */
public final class PlungerLauncherItemModel implements ItemModel {
    public static final Identifier ID = Simulated.path("model/plunger_launcher");
    private static final Identifier ITEM = Simulated.path("item/plunger_launcher/item");
    private static final Identifier BODY = Simulated.path("item/plunger_launcher/plunger_tether");
    private static final Identifier JOINT = Simulated.path("item/plunger_launcher/spool_joint");
    private static final Identifier SPOOL = Simulated.path("item/plunger_launcher/tether_spool");
    private static final int[] TINTS = new int[]{-1};

    private final ModelRenderProperties settings;
    private final ItemQuads item;
    private final ItemQuads body;
    private final ItemQuads joint;
    private final ItemQuads spool;

    private PlungerLauncherItemModel(final ModelRenderProperties settings, final ItemQuads item, final ItemQuads body, final ItemQuads joint,
                                     final ItemQuads spool) {
        this.settings = settings;
        this.item = item;
        this.body = body;
        this.joint = joint;
        this.spool = spool;
    }

    @Override
    public void update(final ItemStackRenderState state, final ItemStack stack, final ItemModelResolver resolver, final ItemDisplayContext displayContext,
                       @Nullable final ClientLevel level, @Nullable final ItemOwner owner, final int seed) {
        state.appendModelIdentityElement(this);
        state.setAnimated();

        final Minecraft minecraft = Minecraft.getInstance();
        final AbstractClientPlayer focusOwner = owner != null && owner.asLivingEntity() instanceof final AbstractClientPlayer holder ? holder : null;
        final Player player = focusOwner != null ? focusOwner : minecraft.player;
        final float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        final boolean captureWorldFocus = focusOwner != null
                && (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        final Matrix4f common = new Matrix4f().translate(0.5f, 0.5f, 0.5f).scale(0.8f, 0.8f, 0.8f).translate(0, 0, 0.15f);

        final ItemStackRenderState.LayerRenderState base = state.newLayer();
        this.settings.applyToLayer(base, displayContext);
        base.setLocalTransform(common);
        base.setFoilType(stack.hasFoil() ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE);
        base.setupSpecialModel(new BodyLayer(this.item, displayContext, displayContext.firstPerson() || captureWorldFocus, partialTicks, focusOwner), null);

        boolean renderFirst = player == null;
        boolean renderSecond = player == null;
        float cooldown = 0;
        if (player != null) {
            cooldown = player.getCooldowns().getCooldownPercent(stack, partialTicks);
            final LaunchedPlungerEntity launched = player instanceof final PlayerLaunchedPlungerExtension extension
                    ? extension.simulated$getLaunchedPlunger() : null;
            if (cooldown <= 0.6f || launched != null && launched.getOther() == null) {
                renderFirst = (launched == null || launched.isRemoved() || launched.getOther() != null) && cooldown <= 0.4f;
                renderSecond = true;
            }
        }
        if (renderFirst) {
            this.addPlunger(state, displayContext, common, true, cooldown);
        }
        if (renderSecond) {
            this.addPlunger(state, displayContext, common, false, cooldown);
        }
    }

    private void addPlunger(final ItemStackRenderState state, final ItemDisplayContext displayContext, final Matrix4fc common, final boolean first,
                            final float cooldown) {
        final Matrix4f bodyTransform = new Matrix4f(common).translate(2f / 16 * (first ? -1 : 1), -1f / 16, -5f / 16);
        if (cooldown > 0 && PlungerLauncherItem.reloadCooldown) {
            final float start = first ? 0.1f : 0.3f;
            final float end = first ? 0.4f : 0.6f;
            float slideIn = Mth.clamp(Mth.map(cooldown, start, end, 0, 1), 0, 1);
            slideIn = (float) Math.pow(slideIn, 3);
            bodyTransform.translate(0, 0, -slideIn / 12f);
        }
        // these partials are authored around [0, 0, 0], unlike the launcher base, so no re-centring
        this.layer(state, displayContext, this.body, bodyTransform);
        this.layer(state, displayContext, this.joint, bodyTransform);
        this.layer(state, displayContext, this.spool, new Matrix4f(bodyTransform).translate(0, 0, 3f / 16));
    }

    private void layer(final ItemStackRenderState state, final ItemDisplayContext displayContext, final ItemQuads quads, final Matrix4fc transform) {
        final ItemStackRenderState.LayerRenderState layer = state.newLayer();
        this.settings.applyToLayer(layer, displayContext);
        layer.setQuads(quads);
        layer.setLocalTransform(transform);
    }

    /** The launcher body; captures the tether's focus point (two sixteenths in front of the body) from the final pose. */
    private record BodyLayer(ItemQuads quads, ItemDisplayContext displayContext, boolean captureFocus, float partialTicks,
                             @Nullable AbstractClientPlayer focusOwner) implements SpecialModelRenderer<Object> {
        @Override
        public void submit(@Nullable final Object argument, final PoseStack matrices, final SubmitNodeCollector queue, final int lightCoords,
                           final int overlayCoords, final boolean hasFoil, final int outlineColor) {
            if (this.captureFocus) {
                matrices.pushPose();
                matrices.translate(2f / 16, -1f / 16, -2f / 16);
                if (this.displayContext.firstPerson()) {
                    PlungerLauncherItemRenderer.captureFirstPersonFocus(matrices, Minecraft.getInstance(), this.partialTicks);
                } else {
                    PlungerLauncherItemRenderer.captureThirdPersonFocus(this.focusOwner, this.displayContext, matrices, this.partialTicks);
                    if (FirstPersonItemFocus.isLocalPlayerBodyRender(this.displayContext, this.focusOwner, Minecraft.getInstance())) {
                        PlungerLauncherItemRenderer.captureFirstPersonBodyFocus(matrices);
                    }
                }
                matrices.popPose();
            }
            matrices.pushPose();
            matrices.translate(-0.5f, -0.5f, -0.5f);
            queue.submitItem(matrices, this.displayContext, lightCoords, overlayCoords, outlineColor, TINTS, this.quads,
                    hasFoil ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE);
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
            resolver.markDependency(BODY);
            resolver.markDependency(JOINT);
            resolver.markDependency(SPOOL);
        }

        @Override
        public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation) {
            final ModelBaker baker = context.blockModelBaker();
            final ResolvedModel model = baker.getModel(ITEM);
            final TextureSlots textures = model.getTopTextureSlots();
            return new PlungerLauncherItemModel(
                    ModelRenderProperties.fromResolvedModel(baker, model, textures),
                    BakedModelHelper.bakeItemQuads(baker, ITEM),
                    BakedModelHelper.bakeItemQuads(baker, BODY),
                    BakedModelHelper.bakeItemQuads(baker, JOINT),
                    BakedModelHelper.bakeItemQuads(baker, SPOOL));
        }
    }
}
