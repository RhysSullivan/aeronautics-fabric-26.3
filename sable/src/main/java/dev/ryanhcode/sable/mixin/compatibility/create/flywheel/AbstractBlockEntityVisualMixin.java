package dev.ryanhcode.sable.mixin.compatibility.create.flywheel;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.client.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.ryanhcode.sable.compatibility.create.flywheel.SubLevelFlywheelEmbeddings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.client.flywheel.api.visual.DynamicVisual;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationManager;
import com.zurrtum.create.client.flywheel.lib.math.MoreMath;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Vec3i;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;

/**
 * PORT-26.3: sub-level block-entity visuals would test plot positions (about a million blocks out) against the root
 * frustum and camera. They are culled and distance-limited at their world position under this frame's ship pose
 * instead. Their light gets the sub-level's sky scale, as the terrain does.
 */
@Mixin(value = AbstractBlockEntityVisual.class, remap = false)
public abstract class AbstractBlockEntityVisualMixin {
    @Shadow
    @Final
    protected BlockPos pos;

    /** On a ship: the block's world position (this frame's ship pose) against the frustum, which is relative to the root render origin. */
    @ModifyReturnValue(method = "isVisible", at = @At("RETURN"))
    private boolean sable$visibleOnSubLevel(final boolean visible, @Local(argsOnly = true) final FrustumIntersection frustum) {
        final ClientSubLevel subLevel = SubLevelFlywheelEmbeddings.containing(((AbstractVisualAccessor) this).sable$level(), this.pos);
        if (subLevel == null) {
            return visible;
        }
        final Vector3d centre = SubLevelFlywheelEmbeddings.worldCentre(subLevel, this.pos, sable$rootOrigin());
        return centre == null || frustum.testSphere((float) centre.x, (float) centre.y, (float) centre.z,
                (float) (MoreMath.SQRT_3_OVER_2 * SubLevelFlywheelEmbeddings.scale(subLevel)));
    }

    /** On a ship: the update rate follows the block's world distance to the camera, as for world blocks. */
    @ModifyReturnValue(method = "doDistanceLimitThisFrame", at = @At("RETURN"))
    private boolean sable$distanceLimitOnSubLevel(final boolean limit, @Local(argsOnly = true) final DynamicVisual.Context context) {
        final ClientSubLevel subLevel = SubLevelFlywheelEmbeddings.containing(((AbstractVisualAccessor) this).sable$level(), this.pos);
        if (subLevel == null) {
            return limit;
        }
        final Vector3d centre = SubLevelFlywheelEmbeddings.worldCentre(subLevel, this.pos, Vec3i.ZERO);
        if (centre == null) {
            return false;
        }
        return !context.limiter().shouldUpdate(context.camera().pos.distanceToSqr(centre.x, centre.y, centre.z));
    }

    @Unique
    private Vec3i sable$rootOrigin() {
        final VisualizationManager manager = VisualizationManager.get(((AbstractVisualAccessor) this).sable$level());
        return manager == null ? Vec3i.ZERO : manager.renderOrigin();
    }

    @WrapOperation(method = {"computePackedLight", "relight(Lnet/minecraft/core/BlockPos;[Lcom/zurrtum/create/client/flywheel/lib/instance/FlatLit;)V",
            "relight(Lnet/minecraft/core/BlockPos;Ljava/util/Iterator;)V", "relight(Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/LightCoordsUtil;getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int sable$subLevelLight(final BlockAndLightGetter getter, final BlockPos lightPos, final Operation<Integer> original) {
        return SubLevelFlywheelEmbeddings.scaleLight(((AbstractVisualAccessor) this).sable$level(), lightPos, original.call(getter, lightPos));
    }
}
