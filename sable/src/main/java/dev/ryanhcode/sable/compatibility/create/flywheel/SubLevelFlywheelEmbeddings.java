package dev.ryanhcode.sable.compatibility.create.flywheel;

import com.zurrtum.create.client.flywheel.api.visualization.VisualEmbedding;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationContext;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PORT-26.3: Flywheel visuals of block entities on sub-levels (Create Fly's bundled Flywheel).
 * <p>
 * Flywheel keeps tracking these block entities itself (its section-compile hook adds them, as for any block entity),
 * but their visuals are created inside one {@link VisualEmbedding} per sub-level instead of the root context. The
 * embedding's render origin is the sub-level's centre block, so instance positions stay small, and its model matrix
 * {@code T(pivotWorld - rootOrigin) * R * S * T(embeddingOrigin - pivot)} is set from the sub-level's render pose every
 * frame on the render thread, before Flywheel's frame plan runs. The pose is snapshotted there because visuals can be
 * created on Flywheel's worker threads, where the sub-level's cached render pose must not be touched.
 * <p>
 * The light of embedded visuals is the plot's light with its sky part scaled by the sub-level's sky light (as for the
 * terrain); Flywheel's own shader light is not extended to sub-levels.
 */
public final class SubLevelFlywheelEmbeddings {
    /** Render-thread snapshot of every sub-level's render pose this frame. */
    private static final Map<ClientSubLevel, Pose3d> POSES = new ConcurrentHashMap<>();

    private final Map<ClientSubLevel, Entry> embeddings = new ConcurrentHashMap<>();

    private static final class Entry {
        final VisualEmbedding embedding;
        final VisualizationContext parent;
        final List<BlockEntity> blockEntities = new ArrayList<>();
        int skyLightScale = -1;

        Entry(final VisualEmbedding embedding, final VisualizationContext parent) {
            this.embedding = embedding;
            this.parent = parent;
        }

        VisualEmbedding embedding() { return this.embedding; }
        VisualizationContext parent() { return this.parent; }
        List<BlockEntity> blockEntities() { return this.blockEntities; }
    }

    /** Render thread, once per frame before Flywheel's frame plan. */
    public static void snapshotPoses(final @Nullable Level level) {
        POSES.clear();
        if (!(level instanceof final ClientLevel clientLevel)) {
            return;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(clientLevel);
        if (container == null) {
            return;
        }
        for (final ClientSubLevel subLevel : container.getAllSubLevels()) {
            POSES.put(subLevel, new Pose3d(subLevel.renderPose()));
        }
    }

    public static @Nullable ClientSubLevel containing(final @Nullable Level level, final BlockPos pos) {
        if (!(level instanceof final ClientLevel clientLevel)) {
            return null;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(clientLevel);
        if (container == null || !container.inBounds(pos)) {
            return null;
        }
        return dev.ryanhcode.sable.Sable.HELPER.getContainingClient(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** The context a visual for this block entity is created in: its sub-level's embedding, or the root context. */
    public VisualizationContext contextFor(final VisualizationContext root, final BlockEntity blockEntity) {
        final ClientSubLevel subLevel = containing(blockEntity.getLevel(), blockEntity.getBlockPos());
        if (subLevel == null) {
            return root;
        }
        final Entry entry = this.embeddings.computeIfAbsent(subLevel, s -> {
            final VisualEmbedding embedding = root.createEmbedding(s.getPlot().getCenterBlock());
            final Entry created = new Entry(embedding, root);
            update(s, created);
            return created;
        });
        synchronized (entry.blockEntities()) {
            entry.blockEntities().add(blockEntity);
        }
        return entry.embedding();
    }

    public void onRemoved(final BlockEntity blockEntity) {
        final ClientSubLevel subLevel = containing(blockEntity.getLevel(), blockEntity.getBlockPos());
        final Entry entry = subLevel == null ? null : this.embeddings.get(subLevel);
        if (entry != null) {
            synchronized (entry.blockEntities()) {
                entry.blockEntities().remove(blockEntity);
            }
        }
    }

    /** Before Flywheel recreates every visual (render origin moved, backend or resources reloaded). */
    public void clear() {
        for (final Entry entry : this.embeddings.values()) {
            entry.embedding().delete();
        }
        this.embeddings.clear();
    }

    /**
     * Render thread, once per frame after {@link #snapshotPoses}: moves every embedding to its sub-level's pose, and
     * drops the embeddings of removed sub-levels. Returns the block entities whose visuals must be removed.
     */
    public List<BlockEntity> updateFrame(final List<BlockEntity> relight) {
        final List<BlockEntity> orphaned = new ArrayList<>();
        final Iterator<Map.Entry<ClientSubLevel, Entry>> iterator = this.embeddings.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<ClientSubLevel, Entry> mapEntry = iterator.next();
            final ClientSubLevel subLevel = mapEntry.getKey();
            final Entry entry = mapEntry.getValue();
            if (subLevel.isRemoved()) {
                synchronized (entry.blockEntities()) {
                    orphaned.addAll(entry.blockEntities());
                }
                entry.embedding().delete();
                iterator.remove();
                continue;
            }
            update(subLevel, entry);
            // the sky part of a ship's light follows where the ship is; relight its visuals when that changes
            // (PORT-26.3: and the world block light floor; both in one key)
            final int sky = subLevel.getLatestSkyLightScale() * 16 + subLevel.getBlockLightFloor();
            if (entry.skyLightScale != sky) {
                if (entry.skyLightScale != -1) {
                    synchronized (entry.blockEntities()) {
                        relight.addAll(entry.blockEntities());
                    }
                }
                entry.skyLightScale = sky;
            }
        }
        return orphaned;
    }

    private static void update(final ClientSubLevel subLevel, final Entry entry) {
        final Pose3dc pose = POSES.get(subLevel);
        if (pose == null) {
            return; // created before its first snapshot; placed on the next frame
        }
        final Vec3i rootOrigin = entry.parent().renderOrigin();
        final Vec3i localOrigin = entry.embedding().renderOrigin();
        final Matrix4f transform = new Matrix4f()
                .translate((float) (pose.position().x() - rootOrigin.getX()), (float) (pose.position().y() - rootOrigin.getY()),
                        (float) (pose.position().z() - rootOrigin.getZ()))
                .rotate(new Quaternionf(pose.orientation()))
                .scale((float) pose.scale().x(), (float) pose.scale().y(), (float) pose.scale().z())
                .translate((float) (localOrigin.getX() - pose.rotationPoint().x()), (float) (localOrigin.getY() - pose.rotationPoint().y()),
                        (float) (localOrigin.getZ() - pose.rotationPoint().z()));
        entry.embedding().transforms(transform, transform.normal(new Matrix3f()));
    }

    /**
     * World position of a plot block's centre relative to {@code origin}, from this frame's snapshot pose, or null when
     * the ship has no snapshot yet. Safe on Flywheel's worker threads (reads the snapshot only).
     */
    public static org.joml.@Nullable Vector3d worldCentre(final ClientSubLevel subLevel, final BlockPos pos, final Vec3i origin) {
        final Pose3dc pose = POSES.get(subLevel);
        if (pose == null) {
            return null;
        }
        final org.joml.Vector3d v = pose.transformPosition(new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        return v.sub(origin.getX(), origin.getY(), origin.getZ());
    }

    public static double scale(final ClientSubLevel subLevel) {
        final Pose3dc pose = POSES.get(subLevel);
        return pose == null ? 1.0 : Math.max(pose.scale().x(), Math.max(pose.scale().y(), pose.scale().z()));
    }

    public static int scaleLight(final @Nullable Level level, final BlockPos pos, final int light) {
        final ClientSubLevel subLevel = containing(level, pos);
        return subLevel == null ? light : subLevel.scaleLightColor(light);
    }
}
