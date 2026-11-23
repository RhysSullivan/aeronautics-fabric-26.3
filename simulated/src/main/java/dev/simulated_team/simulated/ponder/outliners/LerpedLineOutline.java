package dev.simulated_team.simulated.ponder.outliners;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.ponder.records.PonderLineRecord;
import com.zurrtum.create.client.catnip.outliner.LineOutline;
import com.zurrtum.create.client.catnip.render.PonderRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector4f;

public class LerpedLineOutline extends LineOutline {

    Vector3d prevStart;
    Vector3d prevEnd;

    public LerpedLineOutline(final PonderLineRecord initialLine) {
        this.prevStart = JOMLConversion.toJOML(initialLine.startPos());
        this.prevEnd = JOMLConversion.toJOML(initialLine.endPos());
    }

    public LerpedLineOutline(final Vec3 initialPoint) {
        this.prevStart = JOMLConversion.toJOML(initialPoint);
        this.prevEnd = JOMLConversion.toJOML(initialPoint);
    }

    public void update(final Vec3 prevStart, final Vec3 prevEnd, final Vec3 start, final Vec3 end) {
        this.prevStart = JOMLConversion.toJOML(prevStart);
        this.prevEnd = JOMLConversion.toJOML(prevEnd);

        this.set(start, end);
    }

    // PORT-26.3: Create Fly's LineOutline submits through SubmitNodeCollector; interpolate the end points and let
    // it draw.
    @Override
    protected void submitInner(final PoseStack ms, final SubmitNodeCollector queue, final Vec3 camera, final float pt,
                               final Vector3d start, final Vector3d end, final float width, final int color,
                               final int lightmap, final boolean disableNormals) {
        super.submitInner(ms, queue, camera, pt, interpolatePoint(this.prevStart, start, pt),
                interpolatePoint(this.prevEnd, end, pt), width, color, lightmap, disableNormals);
    }

    public static Vector3d interpolatePoint(final Vector3d current, final Vector3d target, final float pt) {
        return new Vector3d(
                Mth.lerp(pt, current.x, target.x),
                Mth.lerp(pt, current.y, target.y),
                Mth.lerp(pt, current.z, target.z)
        );
    }
}
