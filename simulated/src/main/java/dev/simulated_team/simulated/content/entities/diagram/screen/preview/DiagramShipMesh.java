package dev.simulated_team.simulated.content.entities.diagram.screen.preview;

import com.zurrtum.create.client.catnip.render.EntityBlockSbbBuilder;
import com.zurrtum.create.client.catnip.render.SuperByteBuffer;
import com.zurrtum.create.client.flywheel.lib.model.baked.ModelConsumer;
import com.zurrtum.create.client.flywheel.lib.model.baked.ModelRenderHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PORT-26.3: the diagram preview's mesh of a ship: its block models tessellated once into a Create Fly
 * {@link SuperByteBuffer} (as Create Fly builds contraption meshes), relative to the plot's minimum corner so float
 * positions stay small. Rebuilt at most every {@link #REBUILD_MILLIS} while a preview shows the ship (ships rarely
 * change while the diagram is open). Blocks only: parts drawn by block-entity renderers or Flywheel are not in it.
 */
public final class DiagramShipMesh {
    private static final long REBUILD_MILLIS = 3000;
    private static final long MAX_VOLUME = 128L * 128L * 128L;
    private static final Map<UUID, DiagramShipMesh> MESHES = new HashMap<>();

    public final SuperByteBuffer buffer;
    public final int originX, originY, originZ;
    private final long builtAt;

    private DiagramShipMesh(final SuperByteBuffer buffer, final int originX, final int originY, final int originZ) {
        this.buffer = buffer;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.builtAt = System.currentTimeMillis();
    }

    public static @Nullable DiagramShipMesh get(final ClientSubLevel subLevel) {
        final DiagramShipMesh cached = MESHES.get(subLevel.getUniqueId());
        if (cached != null && System.currentTimeMillis() - cached.builtAt < REBUILD_MILLIS) {
            return cached;
        }
        final DiagramShipMesh built = build(subLevel);
        if (built == null) {
            MESHES.remove(subLevel.getUniqueId());
        } else {
            MESHES.put(subLevel.getUniqueId(), built);
        }
        return built;
    }

    public static void clear() {
        MESHES.clear();
    }

    private static @Nullable DiagramShipMesh build(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume <= 0 || volume > MAX_VOLUME || !(subLevel.getLevel() instanceof final BlockAndTintGetter level)) {
            return null;
        }
        final BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        final EntityBlockSbbBuilder builder = new EntityBlockSbbBuilder();
        final ModelConsumer consumer = ModelRenderHelper.getCullHelper(builder);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockModelLighter.enableCaching();
        try {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        final BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        consumer.tesselateBlock(x - bounds.minX(), y - bounds.minY(), z - bounds.minZ(), level, pos.immutable(), state,
                                models.get(state), state.getSeed(pos));
                    }
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }
        return new DiagramShipMesh(builder.build(), bounds.minX(), bounds.minY(), bounds.minZ());
    }
}
