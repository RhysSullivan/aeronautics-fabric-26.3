package dev.ryanhcode.sable.render;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects light-emitting blocks in moving sub-levels into their parent world's
 * light engine. On the server these are authoritative block-light sources, so
 * vanilla light packets and gameplay light checks use the propagated result.
 */
public final class SubLevelDynamicLights {
    private static final Map<LevelLightEngine, DynamicLightState> LIGHTS_BY_ENGINE =
            new IdentityHashMap<>();
    private static final DynamicLightState EMPTY_STATE = new DynamicLightState(
            new Long2IntOpenHashMap(),
            new Long2ObjectOpenHashMap<>(),
            new LongOpenHashSet(),
            new IdentityHashMap<>()
    );
    private static final ThreadLocal<ArrayDeque<DynamicLightState>> ACTIVE_STATES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private SubLevelDynamicLights() {
    }

    /**
     * Every this many ticks each body's contribution is recomputed anyway (a world chunk that loads under a projected
     * light), each body on its own tick, and the world's light checks and section states are re-asserted for all.
     */
    private static final int RESCAN_TICKS = 100;
    /** {@code -Dsable.debug.dynLightTiming=true}: mean time of this pass, logged every 200 ticks per side. */
    private static final boolean TIMING = Boolean.getBoolean("sable.debug.dynLightTiming");
    private static long timedNanosServer, timedNanosClient, maxNanosServer, maxNanosClient;
    private static int timedTicksServer, timedTicksClient;

    public static void tick(final Level level) {
        final long start = TIMING ? System.nanoTime() : 0L;
        tickInner(level);
        if (TIMING) {
            final long t = System.nanoTime() - start;
            if (level.isClientSide()) {
                timedNanosClient += t;
                maxNanosClient = Math.max(maxNanosClient, t);
                if (++timedTicksClient == 200) {
                    org.slf4j.LoggerFactory.getLogger("sable/dyn_lights").info("dynamic lights (client): {} us per tick (mean of 200), worst {} us, levels kept {}",
                            timedNanosClient / 200 / 1000.0, maxNanosClient / 1000.0, cachedLevelCount());
                    timedTicksClient = 0; timedNanosClient = 0; maxNanosClient = 0;
                }
            } else {
                timedNanosServer += t;
                maxNanosServer = Math.max(maxNanosServer, t);
                if (++timedTicksServer == 200) {
                    org.slf4j.LoggerFactory.getLogger("sable/dyn_lights").info("dynamic lights (server): {} us per tick (mean of 200), worst {} us, levels kept {}",
                            timedNanosServer / 200 / 1000.0, maxNanosServer / 1000.0, cachedLevelCount());
                    timedTicksServer = 0; timedNanosServer = 0; maxNanosServer = 0;
                }
            }
        }
    }

    private static void tickInner(final Level level) {
        final LevelLightEngine lightEngine = level.getLightEngine();
        final boolean rescan = level.getGameTime() % RESCAN_TICKS == 0;
        final DynamicLightState nextState = collectState(level);
        final DynamicLightState previousState;

        synchronized (LIGHTS_BY_ENGINE) {
            previousState = LIGHTS_BY_ENGINE.put(lightEngine, nextState);
        }

        // A source can occupy an otherwise empty parent-world section. Keep
        // those sections active so the vanilla light engine will process it.
        for (final long packedSection : nextState.sections) {
            if (rescan || previousState == null || !previousState.sections.contains(packedSection)) {
                lightEngine.updateSectionStatus(SectionPos.of(packedSection), false);
            }
        }

        if (previousState != null) {
            for (final long packedSection : previousState.sections) {
                if (!nextState.sections.contains(packedSection)) {
                    restoreSectionStatus(level, lightEngine, packedSection);
                }
            }
        }

        final LongOpenHashSet positionsToCheck = new LongOpenHashSet();
        if (rescan || previousState == null) {
            positionsToCheck.addAll(nextState.lights.keySet());
            if (previousState != null) {
                positionsToCheck.addAll(previousState.lights.keySet());
            }
        } else {
            // PORT-26.3: only sources that appeared, went or changed strength
            for (final Long2IntMap.Entry entry : nextState.lights.long2IntEntrySet()) {
                if (previousState.lights.get(entry.getLongKey()) != entry.getIntValue()) {
                    positionsToCheck.add(entry.getLongKey());
                }
            }
            for (final long packedPos : previousState.lights.keySet()) {
                if (!nextState.lights.containsKey(packedPos)) {
                    positionsToCheck.add(packedPos);
                }
            }
        }

        for (final Long2ObjectMap.Entry<BlockState> entry : nextState.blocks.long2ObjectEntrySet()) {
            if (previousState == null || previousState.blocks.get(entry.getLongKey()) != entry.getValue()) {
                positionsToCheck.add(entry.getLongKey());
            }
        }
        if (previousState != null) {
            for (final long packedPos : previousState.blocks.keySet()) {
                if (!nextState.blocks.containsKey(packedPos)) {
                    positionsToCheck.add(packedPos);
                }
            }
        }

        if (positionsToCheck.isEmpty()) {
            return;
        }

        for (final long packedPos : positionsToCheck) {
            lightEngine.checkBlock(BlockPos.of(packedPos));
        }

        // ServerLevel uses ThreadedLevelLightEngine. Its check/update methods
        // enqueue work and its runner is invoked automatically on the chunk
        // lighting executor; calling runLightUpdates here deliberately throws.
        if (level.isClientSide()) {
            lightEngine.runLightUpdates();
        }
    }

    /** A level going away (client disconnect or dimension change, server level closed): drop everything kept for it. */
    public static void clear(final Level level) {
        synchronized (LIGHTS_BY_ENGINE) {
            LIGHTS_BY_ENGINE.remove(level.getLightEngine());
        }
        synchronized (BODY_CACHE) {
            BODY_CACHE.remove(level);
        }
    }

    /** Levels with kept body light (the debug timing line prints it). */
    public static int cachedLevelCount() {
        synchronized (BODY_CACHE) {
            return BODY_CACHE.size();
        }
    }

    public static void beginLightUpdates(final LevelLightEngine engine) {
        final DynamicLightState state;
        synchronized (LIGHTS_BY_ENGINE) {
            state = LIGHTS_BY_ENGINE.get(engine);
        }
        ACTIVE_STATES.get().push(state == null ? EMPTY_STATE : state);
    }

    public static void endLightUpdates(final LevelLightEngine engine) {
        final ArrayDeque<DynamicLightState> activeStates = ACTIVE_STATES.get();
        if (!activeStates.isEmpty()) {
            activeStates.pop();
        }
        if (activeStates.isEmpty()) {
            ACTIVE_STATES.remove();
        }
    }

    public static int getLightEmission(final long packedPos) {
        final ArrayDeque<DynamicLightState> activeStates = ACTIVE_STATES.get();
        return activeStates.isEmpty() ? 0 : activeStates.peek().lights.get(packedPos);
    }

    public static BlockState getOccludingBlockState(final long packedPos) {
        final ArrayDeque<DynamicLightState> activeStates = ACTIVE_STATES.get();
        return activeStates.isEmpty() ? null : activeStates.peek().blocks.get(packedPos);
    }

    /**
     * Returns an upper-bound mask for light projected by one sub-level.
     *
     * <p>The parent light engine stores only the maximum contribution, so a
     * renderer cannot otherwise distinguish a stable world light from its own
     * voxel-snapped moving source. The mask lets sub-level geometry keep using
     * its plot-space light for its own sources while still accepting stronger
     * parent-world lights.</p>
     */
    public static OwnLightMask getOwnLightMask(
            final LevelLightEngine engine,
            final SubLevel subLevel
    ) {
        synchronized (LIGHTS_BY_ENGINE) {
            final DynamicLightState state = LIGHTS_BY_ENGINE.get(engine);
            if (state == null) {
                return OwnLightMask.EMPTY;
            }
            return state.ownLights.getOrDefault(subLevel, OwnLightMask.EMPTY);
        }
    }

    /** PORT-26.3: one body's contribution, kept while its pose and its plot's light-change count stay the same. */
    private static final class BodyLights {
        final dev.ryanhcode.sable.companion.math.Pose3d pose = new dev.ryanhcode.sable.companion.math.Pose3d();
        int changeCount;
        boolean hasSources;
        final Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet sections = new LongOpenHashSet();
        @org.jetbrains.annotations.Nullable OwnLightMask own;
    }

    /**
     * Kept body light per level. Its values reach their level (body -> level), so a weak map would never let go: each
     * entry is removed by {@link #clear(Level)} when its level goes away.
     */
    private static final Map<Level, Map<SubLevel, BodyLights>> BODY_CACHE = new IdentityHashMap<>();

    /**
     * Whether a body is still where its kept light was computed: within 0.05 of a block and about half a degree (the
     * projected light is snapped to whole blocks, so this is not visible). Measured against the pose the light was
     * computed at, not the last tick's, so a slow drift still recomputes once it adds up; a pile of resting pieces that
     * never quite sleeps no longer recomputes every tick.
     */
    private static boolean samePose(final Pose3dc a, final Pose3dc b) {
        return a.position().distanceSquared(b.position()) < 0.05 * 0.05
                && Math.abs(a.orientation().dot(b.orientation())) > 1.0 - 1.0E-5;
    }

    private static DynamicLightState collectState(final Level level) {
        final Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet sections = new LongOpenHashSet();
        final Map<SubLevel, OwnLightMask> ownLights = new IdentityHashMap<>();
        lights.defaultReturnValue(0);

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return new DynamicLightState(lights, blocks, sections, ownLights);
        }

        final Map<SubLevel, BodyLights> cache;
        synchronized (BODY_CACHE) {
            cache = BODY_CACHE.computeIfAbsent(level, l -> new IdentityHashMap<>());
        }
        final java.util.Set<SubLevel> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (final SubLevel subLevel : container.getAllSubLevels()) {
            seen.add(subLevel);
            final Pose3dc pose = subLevel.logicalPose();
            final int changeCount = subLevel.getPlot().lightChangeCount();
            BodyLights body = cache.get(subLevel);
            // each body is also recomputed once every RESCAN_TICKS, on its own tick (spread, not all at once)
            final boolean ownRescan = Math.floorMod(level.getGameTime() + System.identityHashCode(subLevel), RESCAN_TICKS) == 0;
            final boolean keep = !ownRescan && body != null && body.changeCount == changeCount
                    && (!body.hasSources || samePose(body.pose, pose));
            if (!keep) {
                body = computeBody(level, subLevel, pose);
                body.changeCount = changeCount;
                cache.put(subLevel, body);
            }
            if (!body.hasSources) {
                continue;
            }
            for (final Long2IntMap.Entry entry : body.lights.long2IntEntrySet()) {
                if (entry.getIntValue() > lights.get(entry.getLongKey())) {
                    lights.put(entry.getLongKey(), entry.getIntValue());
                }
            }
            for (final Long2ObjectMap.Entry<BlockState> entry : body.blocks.long2ObjectEntrySet()) {
                final BlockState blockState = entry.getValue();
                final BlockState existingState = blocks.get(entry.getLongKey());
                if (existingState == null
                        || blockState.getLightDampening() > existingState.getLightDampening()
                        || blockState.getLightDampening() == existingState.getLightDampening()
                        && blockState.useShapeForLightOcclusion()
                        && !existingState.useShapeForLightOcclusion()) {
                    blocks.put(entry.getLongKey(), blockState);
                }
            }
            sections.addAll(body.sections);
            if (body.own != null) {
                ownLights.put(subLevel, body.own);
            }
        }
        cache.keySet().retainAll(seen);

        return new DynamicLightState(lights, blocks, sections, ownLights);
    }

    /** One body's light sources projected into the world, and the light-blocking cells around them. */
    private static BodyLights computeBody(final Level level, final SubLevel subLevel, final Pose3dc pose) {
        final BodyLights result = new BodyLights();
        result.pose.set(pose);
        final Long2IntOpenHashMap lights = result.lights;
        final Long2ObjectOpenHashMap<BlockState> blocks = result.blocks;
        final LongOpenHashSet sections = result.sections;
        lights.defaultReturnValue(0);
        final List<LocalLightSource> localSources = new ArrayList<>();
        subLevel.getPlot().getLoadedChunks().forEach(holder ->
                holder.getChunk().findBlockLightSources((sourcePos, state) -> {
                    final int emission = state.getLightEmission();
                    if (emission > 0) {
                        localSources.add(new LocalLightSource(sourcePos.immutable(), emission));
                    }
                })
        );
        if (localSources.isEmpty()) {
            return result;
        }
        result.hasSources = true;

        final Vector3d physicalCenter = new Vector3d();
        final BlockPos.MutableBlockPos physicalPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos localPos = new BlockPos.MutableBlockPos();
        final LongOpenHashSet occlusionCandidates = new LongOpenHashSet();
        final List<ProjectedLightSource> projectedSources = new ArrayList<>();

        for (final LocalLightSource source : localSources) {
            if (!projectPosition(level, pose, source.pos, physicalCenter, physicalPos)) {
                continue;
            }

            projectedSources.add(new ProjectedLightSource(
                    physicalPos.getX(),
                    physicalPos.getY(),
                    physicalPos.getZ(),
                    source.emission
            ));
            final long packedSourcePos = physicalPos.asLong();
            if (source.emission > lights.get(packedSourcePos)) {
                lights.put(packedSourcePos, source.emission);
            }
            sections.add(SectionPos.blockToSection(packedSourcePos));

            final int radius = Math.min(15, source.emission);
            final int radiusSquared = radius * radius;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z > radiusSquared) {
                            continue;
                        }

                        final int candidateY = source.pos.getY() + y;
                        if (level.isOutsideBuildHeight(candidateY)) {
                            continue;
                        }
                        occlusionCandidates.add(BlockPos.asLong(
                                source.pos.getX() + x,
                                candidateY,
                                source.pos.getZ() + z
                        ));
                    }
                }
            }
        }

        for (final long packedLocalPos : occlusionCandidates) {
            localPos.set(packedLocalPos);
            final BlockState blockState = level.getBlockState(localPos);
            if (blockState.isAir()
                    || blockState.getLightDampening() <= 0 && !blockState.useShapeForLightOcclusion()) {
                continue;
            }
            if (!projectPosition(level, pose, localPos, physicalCenter, physicalPos)) {
                continue;
            }

            final long packedPhysicalPos = physicalPos.asLong();
            final BlockState existingState = blocks.get(packedPhysicalPos);
            if (existingState == null
                    || blockState.getLightDampening() > existingState.getLightDampening()
                    || blockState.getLightDampening() == existingState.getLightDampening()
                    && blockState.useShapeForLightOcclusion()
                    && !existingState.useShapeForLightOcclusion()) {
                blocks.put(packedPhysicalPos, blockState);
            }
            sections.add(SectionPos.blockToSection(packedPhysicalPos));
        }

        if (!projectedSources.isEmpty()) {
            result.own = new OwnLightMask(List.copyOf(projectedSources));
        }
        return result;
    }

    private static boolean projectPosition(
            final Level level,
            final Pose3dc pose,
            final BlockPos localPos,
            final Vector3d physicalCenter,
            final BlockPos.MutableBlockPos physicalPos
    ) {
        pose.transformPosition(
                physicalCenter.set(
                        localPos.getX() + 0.5,
                        localPos.getY() + 0.5,
                        localPos.getZ() + 0.5
                ),
                physicalCenter
        );
        physicalPos.set(physicalCenter.x, physicalCenter.y, physicalCenter.z);
        return !level.isOutsideBuildHeight(physicalPos) && level.hasChunkAt(physicalPos);
    }

    private static void restoreSectionStatus(
            final Level level,
            final LevelLightEngine lightEngine,
            final long packedSection
    ) {
        final SectionPos sectionPos = SectionPos.of(packedSection);
        if (sectionPos.y() < level.getMinSectionY() || sectionPos.y() >= level.getMaxSectionY()) {
            return;
        }

        final BlockPos origin = sectionPos.origin();
        if (!level.hasChunkAt(origin)) {
            return;
        }

        final LevelChunk chunk = level.getChunkAt(origin);
        final int sectionIndex = level.getSectionIndexFromSectionY(sectionPos.y());
        lightEngine.updateSectionStatus(sectionPos, chunk.getSection(sectionIndex).hasOnlyAir());
    }

    private record DynamicLightState(
            Long2IntOpenHashMap lights,
            Long2ObjectOpenHashMap<BlockState> blocks,
            LongOpenHashSet sections,
            Map<SubLevel, OwnLightMask> ownLights
    ) {
    }

    public static final class OwnLightMask {
        private static final OwnLightMask EMPTY = new OwnLightMask(List.of());

        private final List<ProjectedLightSource> sources;

        private OwnLightMask(final List<ProjectedLightSource> sources) {
            this.sources = sources;
        }

        public int upperBound(final long packedPos) {
            final int x = BlockPos.getX(packedPos);
            final int y = BlockPos.getY(packedPos);
            final int z = BlockPos.getZ(packedPos);
            int upperBound = 0;

            for (final ProjectedLightSource source : this.sources) {
                final int distance = Math.abs(x - source.x)
                        + Math.abs(y - source.y)
                        + Math.abs(z - source.z);
                upperBound = Math.max(upperBound, source.emission - distance);
            }

            return upperBound;
        }
    }

    private record LocalLightSource(BlockPos pos, int emission) {
    }

    private record ProjectedLightSource(int x, int y, int z, int emission) {
    }
}
