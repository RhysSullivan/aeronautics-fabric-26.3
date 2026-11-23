package dev.ryanhcode.offroad.content.blocks.rock_cutting_wheel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zurrtum.create.catnip.animation.LerpedFloat;
import com.zurrtum.create.client.api.behaviour.movement.MovementRenderState;
import com.zurrtum.create.client.api.behaviour.movement.MovementRenderBehaviour;
import com.zurrtum.create.client.catnip.animation.AnimationTickHolder;
import com.zurrtum.create.client.catnip.render.CachedBuffers;
import com.zurrtum.create.client.catnip.render.SuperByteBuffer;
import com.zurrtum.create.client.content.contraptions.render.ActorVisual;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationContext;
import com.zurrtum.create.client.flywheel.api.visualization.VisualizationManager;
import com.zurrtum.create.client.foundation.virtualWorld.VirtualRenderWorld;
import com.zurrtum.create.content.contraptions.behaviour.MovementContext;
import dev.ryanhcode.offroad.index.OffroadPartialModels;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class RockCuttingWheelActorRender implements MovementRenderBehaviour {
    @Nullable
    @Override
    public ActorVisual createVisual(final VisualizationContext visualizationContext,
                                    final VirtualRenderWorld simulationWorld,
                                    final MovementContext context) {
        if (context.temporaryData == null) {
            context.temporaryData = LerpedFloat.angular();
        }
        return new RockCuttingWheelActorVisual(visualizationContext, simulationWorld, context);
    }

    // PORT-26.3: Create Fly extracts a SuperByteBuffer render state and submits it (as its harvester does).
    @Override
    public MovementRenderState getRenderState(final Vec3 camera, final Font textRenderer, final MovementContext context,
                                              final VirtualRenderWorld renderWorld, final PoseStack.Pose transform,
                                              final Matrix4f worldMatrix4f) {
        if (VisualizationManager.supportsVisualization(context.world)) {
            return null;
        }
        if (context.temporaryData == null) {
            context.temporaryData = LerpedFloat.angular();
        }
        final BlockState blockState = context.state;
        final float angle = ((LerpedFloat) context.temporaryData).getValue(AnimationTickHolder.getPartialTicks(context.world));
        final int light = net.minecraft.util.LightCoordsUtil.getLightCoords(renderWorld, context.localPos);
        final SuperByteBuffer wheel = CachedBuffers.partial(OffroadPartialModels.ROCK_CUTTING_WHEEL_WHEEL, blockState)
                .transform(transform).translate(context.localPos);
        final Direction facing = blockState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
        final boolean axisFirst = blockState.getValue(RockCuttingWheelBlock.AXIS_ALONG_FIRST_COORDINATE);
        if ((facing.getAxis() == Direction.Axis.Z || facing.getAxis() == Direction.Axis.Y) ^ axisFirst) {
            wheel.rotateCentered(facing.getRotation()).rotateZCenteredDegrees(90).translate(0.625, 0.5, 0);
        } else {
            wheel.rotateCentered(facing.getRotation()).rotateXCenteredDegrees(90).translate(0, 0.5, -0.625);
        }
        final com.zurrtum.create.client.catnip.render.SuperByteBufferRenderState state = wheel.rotateYCenteredDegrees(angle)
                .light(light).useLevelLight(context.world, worldMatrix4f).extractRenderState();
        return state::submit;
    }
}
