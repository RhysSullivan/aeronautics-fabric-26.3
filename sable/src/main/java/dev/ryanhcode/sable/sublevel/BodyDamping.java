package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;

/**
 * PORT-26.3 addition (server only): <b>damping</b>, opt-in per body. A body whose user data holds
 * {@code linear_damping} or {@code angular_damping} (a number per second, clamped to 0..{@value #MAX}) has that share
 * of its linear or angular velocity taken away each second, exponentially: each physics substep the velocity is
 * multiplied by {@code exp(-k * dt)}. That factor is between 0 and 1 for any value, so damping only ever removes
 * motion: it cannot add energy, overshoot or become unstable. A sleeping body is left asleep. Without the keys nothing
 * here touches the body.
 *
 * <p>Data pack use: {@code data merge sub_level <selector> {angular_damping:2.0f}}; remove the key to switch it off.
 */
public final class BodyDamping {
    public static final String LINEAR_KEY = "linear_damping";
    public static final String ANGULAR_KEY = "angular_damping";
    public static final float MAX = 50.0f;

    private BodyDamping() {
    }

    /** Once per server tick, before the substeps: each body's damping rates (0 without the keys). */
    public static void gather(final ServerSubLevelContainer container) {
        for (final ServerSubLevel subLevel : container.getAllSubLevels()) {
            final CompoundTag tag = subLevel.getUserDataTag();
            if (tag == null) {
                subLevel.setDamping(0.0f, 0.0f);
                continue;
            }
            subLevel.setDamping(rate(tag, LINEAR_KEY), rate(tag, ANGULAR_KEY));
        }
    }

    private static float rate(final CompoundTag tag, final String key) {
        final float k = tag.getFloatOr(key, 0.0f);
        if (!(k > 0.0f)) { // NaN, missing, zero or negative: off
            return 0.0f;
        }
        return Math.min(MAX, k);
    }

    /** Each physics substep, for a body with damping. */
    public static void apply(final RigidBodyHandle handle, final float linear, final float angular, final double timeStep) {
        final Vector3d v = handle.getLinearVelocity(new Vector3d());
        final Vector3d w = handle.getAngularVelocity(new Vector3d());
        if (!v.isFinite() || !w.isFinite()) {
            return; // never feed a non-finite velocity back to the engine (Infinity * -0.0 is NaN)
        }
        // add -(1 - exp(-k dt)) of the velocity: the result is exp(-k dt) times the velocity
        v.mul(-(1.0 - Math.exp(-linear * timeStep)));
        w.mul(-(1.0 - Math.exp(-angular * timeStep)));
        handle.addLinearAndAngularVelocity(v, w, false);
    }
}
