package dev.ryanhcode.sable.sublevel.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.compatibility.entityculling.EntityCullingCompat;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import dev.ryanhcode.sable.Sable;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * PORT-26.3: block entities on sub-levels, on the 26.3 extract and submit phases.
 * <p>
 * Extract: for every sub-level, the block entities of its compiled sections are extracted with the block-entity
 * dispatcher's camera moved into the sub-level's plot space (so distance checks, sign text and similar see the camera
 * where it is relative to the ship). Their light is the plot's light with the sky part scaled by the sub-level's sky
 * light, as for its terrain: every light lookup made while a sub-level's block entities are extracted is scaled
 * ({@code LightCoordsUtilMixin}), because many renderers (Create's among them) look light up themselves. The compiled section lists are used rather than the plot chunks' maps because Flywheel's
 * section-compile hook already leaves out block entities it draws itself, so the fallback path and the Flywheel path
 * never draw the same block entity twice.
 * <p>
 * Submit: each sub-level's states are submitted with the pose {@code T(pivotWorld - camera) * R * S * T(blockPos - pivot)},
 * built from the same render pose as the sub-level's terrain, with every term small (plots live about a million blocks
 * out, where float positions would lose their fraction). While a sub-level's states are submitted the camera render
 * state is moved into its plot space as well, for renderers that face the camera.
 * <p>
 * Plot-space block entities that vanilla or Sodium put in the main list (the globally rendered set holds every
 * block entity whose renderer draws off screen, plot ones included) are dropped from it first.
 */
public final class SubLevelBlockEntityRendering {
    private SubLevelBlockEntityRendering() {
    }

    /** The sub-level whose block entities are being extracted (render thread), so light lookups in their renderers get its sky scale. */
    private static @Nullable ClientSubLevel extracting;

    public static @Nullable ClientSubLevel extracting() {
        return extracting;
    }

    /** One sub-level's extracted block entities and the pose they are submitted with. */
    public record Batch(Matrix4f cameraRelative, Vector3d rotationPoint, Quaternionf orientation, Vec3 localCamera,
                        List<BlockEntityRenderState> states) {
    }

    /**
     * Extracts and submits every sub-level's block entities for a pass with its own view and its own extraction (a
     * shader pack's shadow pass), with {@code base} as the base pose. Nothing is culled to the main camera's frustum.
     * Returns the number of block entities submitted.
     */
    public static int extractAndSubmitForPass(final @Nullable ClientLevel level, final BlockEntityRenderDispatcher dispatcher, final Vec3 camera,
                                              final float partialTick, final SubmitNodeCollector collector, final CameraRenderState cameraState,
                                              final org.joml.Matrix4fc base) {
        final List<Batch> batches = new ArrayList<>();
        extract(level, dispatcher, camera, null, partialTick, new ArrayList<>(), batches);
        submit(batches, dispatcher, collector, cameraState, base);
        int count = 0;
        for (final Batch batch : batches) {
            count += batch.states().size();
        }
        return count;
    }

    public static void extract(final @Nullable ClientLevel level, final BlockEntityRenderDispatcher dispatcher, final Vec3 camera,
                               final net.minecraft.client.renderer.culling.@Nullable Frustum frustum,
                               final float partialTick, final List<BlockEntityRenderState> mainStates, final List<Batch> out) {
        out.clear();
        if (level == null) {
            return;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        mainStates.removeIf(state -> container.inBounds(state.blockPos));
        if (Boolean.getBoolean("sable.debug.skipSubLevelBlockEntities")) {
            return; // measuring aid: the same scene without sub-level block entities (frame cost)
        }

        try (final EntityCullingCompat.Scope ignored = EntityCullingCompat.suspendBlockEntityCulling()) {
            for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
                if (!(subLevel.getRenderData() instanceof final VanillaChunkedSubLevelRenderData renderData)) {
                    continue;
                }
                // a ship whose bounds (last and current tick) are off screen only keeps block entities whose renderers draw
                // outside their block (lasers, beams): vanilla never frustum-culls those either
                final boolean shipVisible = frustum == null || frustum.isVisible(toAabb(subLevel.boundingBox()));
                // the same pose the terrain uses this frame, copied: renderPose() hands out a shared, cached object
                final Pose3d pose = new Pose3d(subLevel.renderPose());
                final Vector3d local = pose.transformPositionInverse(new Vector3d(camera.x, camera.y, camera.z));
                final Vec3 localCamera = new Vec3(local.x, local.y, local.z);
                final Matrix4f cameraRelative = cameraRelative(pose, camera);
                final Vector3d rotationPoint = new Vector3d(pose.rotationPoint());

                final List<BlockEntityRenderState> states = new ArrayList<>();
                dispatcher.prepare(localCamera);
                extracting = subLevel;
                try {
                    for (final SectionRenderDispatcher.RenderSection section : renderData.allRenderSections()) {
                        for (final BlockEntity blockEntity : section.getSectionMesh().getRenderableBlockEntities()) {
                            if (blockEntity.isRemoved()) {
                                continue;
                            }
                            final BlockEntityRenderer<BlockEntity, ?> renderer = dispatcher.getRenderer(blockEntity);
                            if (renderer == null || (!shipVisible && !renderer.shouldRenderOffScreen())) {
                                continue;
                            }
                            final ModelFeatureRenderer.CrumblingOverlay crumbling =
                                    crumbling(level, blockEntity.getBlockPos(), cameraRelative, rotationPoint);
                            final BlockEntityRenderState state = dispatcher.tryExtractRenderState(blockEntity, partialTick, crumbling,
                                    renderer.shouldRenderOffScreen());
                            if (state == null) {
                                continue;
                            }
                            states.add(state);
                        }
                    }
                } finally {
                    extracting = null;
                    dispatcher.prepare(camera);
                }
                if (!states.isEmpty()) {
                    out.add(new Batch(cameraRelative, rotationPoint, new Quaternionf(pose.orientation()), localCamera, states));
                }
            }
        }
    }

    public static void submit(final List<Batch> batches, final BlockEntityRenderDispatcher dispatcher, final SubmitNodeCollector collector,
                              final CameraRenderState camera) {
        submit(batches, dispatcher, collector, camera, null);
    }

    public static void submit(final List<Batch> batches, final BlockEntityRenderDispatcher dispatcher, final SubmitNodeCollector collector,
                              final CameraRenderState camera, final org.joml.@Nullable Matrix4fc base) {
        if (batches.isEmpty()) {
            return;
        }
        final Vec3 cameraPos = camera.pos;
        final BlockPos cameraBlockPos = camera.blockPos;
        final Quaternionf cameraOrientation = camera.orientation;
        final PoseStack poseStack = new PoseStack();
        try {
            for (final Batch batch : batches) {
                camera.pos = batch.localCamera();
                camera.blockPos = BlockPos.containing(batch.localCamera());
                camera.orientation = new Quaternionf(batch.orientation()).conjugate().mul(cameraOrientation);
                for (final BlockEntityRenderState state : batch.states()) {
                    poseStack.pushPose();
                    if (base != null) {
                        poseStack.mulPose(new Matrix4f(base));
                    }
                    poseStack.mulPose(batch.cameraRelative());
                    poseStack.translate(
                            (float) (state.blockPos.getX() - batch.rotationPoint().x),
                            (float) (state.blockPos.getY() - batch.rotationPoint().y),
                            (float) (state.blockPos.getZ() - batch.rotationPoint().z));
                    dispatcher.submit(state, poseStack, collector, camera);
                    poseStack.popPose();
                }
            }
        } finally {
            camera.pos = cameraPos;
            camera.blockPos = cameraBlockPos;
            camera.orientation = cameraOrientation;
        }
    }

    /** A break-progress decal on a sub-level block, with the pose of its sub-level this frame. */
    public record Breaking(Matrix4f cameraRelative, Vector3d rotationPoint, BlockPos pos, BlockState state, int progress) {
    }

    /**
     * Vanilla extracts break progress only within 32 blocks of the camera in world coordinates, which plot positions
     * never are. Sub-level entries are measured from the camera in the sub-level's plot space instead.
     */
    public static void extractBreaking(final @Nullable ClientLevel level, final Vec3 camera, final List<Breaking> out) {
        out.clear();
        if (level == null) {
            return;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        for (final Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> entry : level.destructionProgress().long2ObjectEntrySet()) {
            final BlockPos pos = BlockPos.of(entry.getLongKey());
            final SortedSet<BlockDestructionProgress> progress = entry.getValue();
            if (progress == null || progress.isEmpty() || !container.inBounds(pos)) {
                continue;
            }
            final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(pos.getX() >> 4, pos.getZ() >> 4);
            if (subLevel == null) {
                continue;
            }
            final Pose3d pose = new Pose3d(subLevel.renderPose());
            final Vector3d local = pose.transformPositionInverse(new Vector3d(camera.x, camera.y, camera.z));
            if (pos.distToCenterSqr(local.x, local.y, local.z) > 1024.0) {
                continue;
            }
            out.add(new Breaking(cameraRelative(pose, camera), new Vector3d(pose.rotationPoint()), pos, level.getBlockState(pos), progress.last().getProgress()));
        }
    }

    public static void submitBreaking(final List<Breaking> breaking, final ModelManager modelManager, final SubmitNodeCollector collector) {
        if (breaking.isEmpty()) {
            return;
        }
        final PoseStack poseStack = new PoseStack();
        final List<BlockStateModelPart> parts = new ArrayList<>();
        final RandomSource random = RandomSource.createThreadLocalInstance();
        for (final Breaking b : breaking) {
            if (b.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            poseStack.pushPose();
            poseStack.mulPose(b.cameraRelative());
            poseStack.translate((float) (b.pos().getX() - b.rotationPoint().x), (float) (b.pos().getY() - b.rotationPoint().y),
                    (float) (b.pos().getZ() - b.rotationPoint().z));
            poseStack.translate(b.state().getOffset(b.pos()));
            final BlockStateModel model = modelManager.getBlockStateModelSet().get(b.state());
            random.setSeed(b.state().getSeed(b.pos()));
            model.collectParts(random, parts);
            collector.submitBreakingBlockModel(poseStack, List.copyOf(parts), b.progress(), model.hasMaterialFlag(1));
            parts.clear();
            poseStack.popPose();
        }
    }

    /**
     * For a block outline on a sub-level block: replaces vanilla's {@code translate(pos - camera)} with the
     * sub-level's pose. Returns false when the position is not on a sub-level.
     */
    public static boolean transformOutline(final PoseStack poseStack, final BlockPos pos, final Vec3 camera) {
        final ClientLevel level = Minecraft.getInstance().level;
        final ClientSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || !container.inBounds(pos)) {
            return false;
        }
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(pos.getX() >> 4, pos.getZ() >> 4);
        if (subLevel == null) {
            return false;
        }
        final Pose3d pose = new Pose3d(subLevel.renderPose());
        poseStack.mulPose(cameraRelative(pose, camera));
        poseStack.translate((float) (pos.getX() - pose.rotationPoint().x()), (float) (pos.getY() - pose.rotationPoint().y()),
                (float) (pos.getZ() - pose.rotationPoint().z()));
        return true;
    }

    public static net.minecraft.world.phys.AABB toAabb(final dev.ryanhcode.sable.companion.math.BoundingBox3dc b) {
        return new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** {@code T(pivotWorld - camera) * R * S}: plot positions relative to the pivot to camera-relative world space. */
    private static Matrix4f cameraRelative(final Pose3dc pose, final Vec3 camera) {
        final Matrix4f store = new Matrix4f();
        store.translate((float) (pose.position().x() - camera.x), (float) (pose.position().y() - camera.y), (float) (pose.position().z() - camera.z));
        store.rotate(new Quaternionf(pose.orientation()));
        store.scale((float) pose.scale().x(), (float) pose.scale().y(), (float) pose.scale().z());
        return store;
    }

    private static ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling(final ClientLevel level, final BlockPos blockPos,
                                                                             final Matrix4f cameraRelative, final Vector3d rotationPoint) {
        final SortedSet<BlockDestructionProgress> progress = level.destructionProgress().get(blockPos.asLong());
        if (progress == null || progress.isEmpty()) {
            return null;
        }
        final PoseStack poseStack = new PoseStack();
        poseStack.mulPose(cameraRelative);
        poseStack.translate((float) (blockPos.getX() - rotationPoint.x), (float) (blockPos.getY() - rotationPoint.y),
                (float) (blockPos.getZ() - rotationPoint.z));
        return new ModelFeatureRenderer.CrumblingOverlay(progress.last().getProgress(), poseStack.last());
    }
}
