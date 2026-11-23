package dev.ryanhcode.sable.mixin.stop_rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
public class LevelRenderMixin {

    @Unique
    private BlockPos.MutableBlockPos sable$tempPos;

    @Unique
    private static int sable$getSubLevelHeight(final Level level, final int pX, final int yOffset, final int pZ) {
        final LevelAccelerator accelerator = new LevelAccelerator(level);

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        final Vector3d checkingPos = new Vector3d();
        final Vector3d localUp = new Vector3d(0, 1, 0);

        final BoundingBox3dc minMaxBB = new BoundingBox3d(pX, level.getMinY(), pZ, pX + 1, (level.getMaxY() + 1), pZ + 1);
        int maxHeight = Integer.MIN_VALUE;
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, minMaxBB)) {
            subLevel.logicalPose().transformPositionInverse(checkingPos.set(pX + 0.5f, subLevel.boundingBox().maxY(), pZ + 0.5f));
            subLevel.logicalPose().transformNormalInverse(localUp.set(0, 1, 0));

            final double checkingDistance = subLevel.boundingBox().maxY() - subLevel.boundingBox().minY();
            for (int i = 0; i < checkingDistance; i++) {
                checkingPos.sub(localUp);

                final BlockState gatheredState = accelerator.getBlockState(mutableBlockPos.set(checkingPos.x, checkingPos.y, checkingPos.z));
                if (gatheredState.isSolid() /* PORT-26.3: blocksMotion() removed */ || !gatheredState.getFluidState().isEmpty()) {
                    subLevel.logicalPose().transformPosition(checkingPos);
                    maxHeight = (int) Math.max(maxHeight, checkingPos.y + yOffset);
                    break;
                }
            }
        }

        return maxHeight;
    }

    // PORT-26.3: the call's owner is ClientLevel now
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    public int sable$preventRainThoughSubLevel(final net.minecraft.client.multiplayer.ClientLevel instance, final Heightmap.Types types, final int i, final int j, final Operation<Integer> original) {
        return Math.max(original.call(instance, types, i, j), sable$getSubLevelHeight(instance, i, 1, j));
    }

    // PORT-26.3 staged: rain splash particles are ticked in ClientLevel now; the sub-level heightmap wrap for them is not re-done yet



}
