package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * PORT-26.3 addition (server only): a <b>readable pose</b>, opt-in per body. For a body whose user data holds
 * {@code pose_readable:1b}, {@code data get sub_level <selector> pose...} reads a {@value #KEY} compound computed at the
 * moment it is read from the body's current pose and velocities (the state {@code /sable info} shows: after the last
 * completed physics tick). It is never stored: the data accessor adds it to a copy on read and strips it on write.
 *
 * <p>Contents, all in the world frame: {@code pos} [x, y, z]; {@code quat} [x, y, z, w]; {@code up} [x, y, z], the
 * body's up axis; {@code tilt}, degrees between the body's up and the world's up (0 to 180); {@code lean_x} and
 * {@code lean_z}, signed degrees the body's up leans toward +x and toward +z (positive {@code lean_x}: the +x side is
 * lower); {@code vel} [x, y, z] in blocks per second; {@code avel} [x, y, z] in degrees per second about the world axes;
 * {@code avel_local} the same about the body's own axes; {@code riders}, the riders counted this tick (rider weight).
 */
public final class BodyPose {
    public static final String KEY = "pose";
    public static final String FLAG = "pose_readable";

    private BodyPose() {
    }

    /** Whether this user data asks for the readable pose. */
    public static boolean readable(final CompoundTag userData) {
        return userData.getBooleanOr(FLAG, false);
    }

    /** The pose compound, computed now. */
    public static CompoundTag compute(final ServerSubLevel subLevel) {
        final Pose3dc pose = subLevel.logicalPose();
        final Quaterniondc q = pose.orientation();
        final Vector3d up = q.transform(new Vector3d(0.0, 1.0, 0.0));
        final CompoundTag tag = new CompoundTag();
        tag.put("pos", list(pose.position().x(), pose.position().y(), pose.position().z()));
        tag.put("quat", list(q.x(), q.y(), q.z(), q.w()));
        tag.put("up", list(up.x, up.y, up.z));
        tag.putDouble("tilt", Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y)))));
        tag.putDouble("lean_x", Math.toDegrees(Math.atan2(up.x, up.y)));
        tag.putDouble("lean_z", Math.toDegrees(Math.atan2(up.z, up.y)));
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle != null && handle.isValid()) {
            final Vector3d v = handle.getLinearVelocity(new Vector3d());
            final Vector3d w = handle.getAngularVelocity(new Vector3d()).mul(180.0 / Math.PI);
            tag.put("vel", list(v.x, v.y, v.z));
            tag.put("avel", list(w.x, w.y, w.z));
            final Vector3d wl = q.transformInverse(new Vector3d(w));
            tag.put("avel_local", list(wl.x, wl.y, wl.z));
        }
        tag.putInt("riders", subLevel.riderCount());
        return tag;
    }

    private static ListTag list(final double... values) {
        final ListTag list = new ListTag();
        for (final double v : values) {
            list.add(DoubleTag.valueOf(v));
        }
        return list;
    }
}
