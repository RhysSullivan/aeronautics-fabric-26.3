package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.ryanhcode.sable.Sable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * PORT-26.3 addition (server only): <b>rider weight</b>, opt-in per body. A body whose user data holds
 * {@code rider_weight} (a number: the weight each standing player adds, in masses of one default block, clamped to
 * {@value #MIN_WEIGHT}..{@value #MAX_WEIGHT}) is pushed down at the feet of every player standing on it, each physics
 * substep, with that weight under the dimension's gravity. Without the key nothing here touches the body.
 *
 * <p>Counted: living players whose client says they are on the ground, not spectators and not riding anything (creative
 * players count), whose Sable-tracked body is this one. Sable tracks the body a player stands on: the client picks it on contact from below
 * and sends it with each move packet; landing on world ground or on another body changes it. Each player counts on one
 * body at most. The riders are gathered once per server tick
 * on the server thread; the substeps of that tick reuse them.
 *
 * <p>Data pack use: {@code data merge sub_level <selector> {rider_weight:1.0f}}; remove the key to switch it off. With
 * {@code rider_weight_hold:1b} as well, the riders tip the body without making it sink: their total weight is pushed
 * back up through its centre of mass.
 */
public final class RiderWeight {
    public static final String KEY = "rider_weight";
    /** With {@code rider_weight_hold:1b} the riders' total weight is also pushed up at the body's centre of mass. */
    public static final String HOLD_KEY = "rider_weight_hold";
    public static final float MIN_WEIGHT = 0.0f;
    public static final float MAX_WEIGHT = 64.0f;

    private RiderWeight() {
    }

    /** The rider weight set on a body, or 0 when the key is absent (off). */
    public static float weightOf(final ServerSubLevel subLevel) {
        final CompoundTag tag = subLevel.getUserDataTag();
        if (tag == null || !tag.contains(KEY)) {
            return 0.0f;
        }
        final float w = tag.getFloatOr(KEY, 0.0f);
        if (Float.isNaN(w)) {
            return 0.0f;
        }
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, w));
    }

    /**
     * Once per server tick, before the physics substeps: for each body with the key, the feet points (in the body's
     * plot coordinates) of the players standing on it. Bodies without the key are left with no riders.
     */
    public static void gather(final ServerLevel level, final ServerSubLevelContainer container) {
        final long start = TIMING ? System.nanoTime() : 0L;
        gatherRiders(level, container);
        if (TIMING) {
            timedNanos += System.nanoTime() - start;
            if (++timedTicks == 200) {
                org.slf4j.LoggerFactory.getLogger("sable/rider_weight").info("rider weight pass: {} bodies, {} ns per tick (mean of 200 ticks)",
                        container.getAllSubLevels().size(), timedNanos / 200);
                timedTicks = 0;
                timedNanos = 0L;
            }
        }
    }

    /** {@code -Dsable.debug.riderTiming=true}: the pass's cost, logged every 200 ticks. */
    private static final boolean TIMING = Boolean.getBoolean("sable.debug.riderTiming");
    /** {@code -Dsable.debug.riderLog=true}: each counted rider, every tick (for the proofs). */
    private static final boolean LOG = Boolean.getBoolean("sable.debug.riderLog");
    private static int timedTicks;
    private static long timedNanos;

    private static void gatherRiders(final ServerLevel level, final ServerSubLevelContainer container) {
        Set<UUID> counted = null;
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) {
                continue;
            }
            final float weight = weightOf(subLevel);
            if (weight <= 0.0f) {
                subLevel.setRiders(null, 0.0f, false);
                continue;
            }
            if (counted == null) {
                counted = new HashSet<>();
            }
            final List<Vector3dc> feet = new ArrayList<>();
            final Pose3dc pose = subLevel.logicalPose();
            for (final ServerPlayer player : level.players()) {
                final boolean rider = isRider(player, subLevel) && !counted.contains(player.getUUID());
                if (LOG) {
                    log(level, subLevel, pose, player, rider);
                }
                if (!rider) {
                    continue;
                }
                feet.add(pose.transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ())));
                counted.add(player.getUUID());
            }
            subLevel.setRiders(feet.isEmpty() ? null : feet, weight, subLevel.getUserDataTag().getBooleanOr(HOLD_KEY, false));
        }
    }

    /**
     * A rider of this body: a living, non-spectator player, not riding anything, whose client last reported it on the
     * ground, and whose Sable-tracked body is this one. Both come from the client: the tracked body is Sable's own
     * support signal (picked on contact from below, sent with each move packet), and the on-ground flag is the one in
     * the last move packet the server applied ({@link ClientGround}). The server's own {@code onGround()} is not used:
     * Sable's server collision sets it for every tracked player each tick.
     */
    static boolean isRider(final ServerPlayer player, final ServerSubLevel subLevel) {
        return !player.isSpectator() && player.isAlive() && !player.isPassenger()
                && ((ClientGround) player).sable$clientOnGround()
                && Sable.HELPER.getTrackingSubLevel(player) == subLevel;
    }

    private static void log(final ServerLevel level, final ServerSubLevel subLevel, final Pose3dc pose, final ServerPlayer player, final boolean rider) {
        final SubLevel tracked = Sable.HELPER.getTrackingSubLevel(player);
        final Vector3d local = pose.transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()));
        org.slf4j.LoggerFactory.getLogger("sable/rider_weight").info(
                "rw tick={} player={} body={} rider={} onGround={} serverOnGround={} tracked={} world={},{},{} local={},{},{}",
                level.getGameTime(), player.getPlainTextName(), subLevel.getName(), rider, ((ClientGround) player).sable$clientOnGround(), player.onGround(),
                tracked == null ? "none" : tracked == subLevel ? "this" : "other",
                fmt(player.getX()), fmt(player.getY()), fmt(player.getZ()), fmt(local.x), fmt(local.y), fmt(local.z));
    }

    private static String fmt(final double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /**
     * The on-ground flag a player's client sent in the last move packet the server applied; false for a new player
     * and after the server moves the player (teleport, dimension change, respawn). Written and read on the server
     * thread. Implemented on {@link ServerPlayer} by a mixin; nothing but rider weight reads it.
     */
    public interface ClientGround {
        boolean sable$clientOnGround();

        void sable$setClientOnGround(boolean onGround);
    }

    /** Each physics substep, for a body with riders: their weight at their feet. */
    public static void apply(final ServerSubLevel subLevel, final @Nullable List<Vector3dc> feet, final float weight, final boolean hold,
                             final RigidBodyHandle handle, final Vector3dc worldGravity, final double timeStep) {
        if (feet == null || weight <= 0.0f) {
            return;
        }
        // one default block weighs (mass 1.0) * gravity; the impulse is in the body's own frame
        final Vector3d impulse = subLevel.logicalPose().orientation()
                .transformInverse(new Vector3d(worldGravity).mul(weight * timeStep));
        for (final Vector3dc point : feet) {
            handle.applyImpulseAtPoint(point, impulse);
        }
        if (hold) {
            // the same total, reversed, through the centre of mass: no net force from the riders (the body does not sink
            // or rise), the torque about the centre of mass unchanged
            handle.applyLinearImpulse(impulse.mul(-feet.size()));
        }
    }
}
