package dev.ryanhcode.sable.physics.chunk;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public enum VoxelNeighborhoodState {
    EMPTY(0x000000),
    FACE(0x8bd21c),
    EDGE(0xe9eb0b),
    CORNER(0xeb6c0b),
    INTERIOR(0x000000);

    // PORT-26.3: the two caches below were Int2BooleanOpenHashMaps keyed by state.hashCode() and filled with
    // computeIfAbsent. That corrupts the map when it is entered again while computing (the shape lookup reads the level:
    // a block entity or chunk load there runs other queued server work, which can reach these caches) or by two threads,
    // and the next rehash throws ArrayIndexOutOfBoundsException (seen on a test server). They are now keyed by the state
    // itself in a ConcurrentHashMap, and the value is computed outside any map operation and stored with putIfAbsent:
    // safe under re-entry and under concurrent callers; the computed values are unchanged.
    public static BiFunction<BlockGetter, BlockState, Boolean> IS_SOLID_MEMOIZED = new BiFunction<>() {
        private final ConcurrentHashMap<BlockState, Boolean> cache = new ConcurrentHashMap<>();

        @Override
        public Boolean apply(final BlockGetter blockGetter, final BlockState state) {
            final Boolean known = this.cache.get(state);
            if (known != null) {
                return known;
            }
            // TODO add the blockgetter and position as context
            final boolean solid;
            if (state.isAir()) {
                solid = false;
            } else if (state.getBlock() instanceof MovingPistonBlock) {
                solid = true;
            } else {
                solid = !state.getCollisionShape(blockGetter, BlockPos.ZERO).isEmpty();
            }
            final Boolean raced = this.cache.putIfAbsent(state, solid);
            return raced != null ? raced : solid;
        }
    };

    public static BiFunction<BlockGetter, BlockState, Boolean> IS_FULL_BLOCK = new BiFunction<>() {
        private final ConcurrentHashMap<BlockState, Boolean> cache = new ConcurrentHashMap<>();

        @Override
        public Boolean apply(final BlockGetter blockGetter, final BlockState state) {
            final Boolean known = this.cache.get(state);
            if (known != null) {
                return known;
            }
            // TODO add the blockgetter and position as context
            final boolean full = !state.isAir() && state.isCollisionShapeFullBlock(blockGetter, BlockPos.ZERO);
            final Boolean raced = this.cache.putIfAbsent(state, full);
            return raced != null ? raced : full;
        }
    };

    private final int debugColor;

    VoxelNeighborhoodState(final int debugColor) {
        this.debugColor = debugColor;
    }

    public static boolean isSolid(final BlockGetter blockGetter, final BlockPos pos, final BlockState state) {
        return IS_SOLID_MEMOIZED.apply(blockGetter, state);
    }

    public static boolean isFullBlock(final BlockGetter blockGetter, final BlockPos pos, final BlockState state) {
        return IS_FULL_BLOCK.apply(blockGetter, state);
    }

    public static boolean isLiquid(final BlockState state) {
        return state.liquid() || state.getBlock() instanceof KelpPlantBlock || state.getBlock() instanceof KelpBlock;
    }

    public static VoxelNeighborhoodState getState(final LevelAccelerator level, final BlockPos pos, @Nullable final LevelChunk chunk) {
        final ChunkPos initialPos = ChunkPos.containing(pos);
        final BlockState state = chunk != null ? level.getBlockState(chunk, pos) : level.getBlockState(pos);

        if (isLiquid(state) || BlockWithSubLevelCollisionCallback.hasCallback(state))
            return CORNER;

        if (!isSolid(level, pos, state)) {
            return EMPTY;
        }

        if (!isFullBlock(level, pos, state)) {
            return CORNER;
        }

        boolean allSolid = true;
        boolean cornerSolid = true;
        int bothSidesCount = 0;

        for (final Direction.Axis axis : Direction.Axis.VALUES) {
            final BlockPos nPos = pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE, axis));
            final BlockPos pPos = pos.relative(Direction.get(Direction.AxisDirection.POSITIVE, axis));

            final BlockState nState = chunk != null && ChunkPos.containing(nPos).equals(initialPos) ? level.getBlockState(chunk, nPos) : level.getBlockState(nPos);
            final BlockState pState = chunk != null && ChunkPos.containing(pPos).equals(initialPos) ? level.getBlockState(chunk, pPos) : level.getBlockState(pPos);

            final boolean negativeSolid = isSolid(level, nPos, nState) && isFullBlock(level, nPos, nState);
            final boolean positiveSolid = isSolid(level, pPos, pState) && isFullBlock(level, pPos, pState);

            if (!negativeSolid || !positiveSolid) {
                allSolid = false;
            }

            if (negativeSolid && positiveSolid) {
                cornerSolid = false;
                bothSidesCount++;
            }
        }

        if (allSolid) {
            return INTERIOR;
        }

        if (bothSidesCount == 1) {
            return EDGE;
        }

        if (cornerSolid) {
            return CORNER;
        }

        return FACE;
    }

    public int getDebugColor() {
        return this.debugColor;
    }

    public byte byteRepresentation() {
        return (byte) this.ordinal();
    }
}
