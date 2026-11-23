package dev.simulated_team.simulated;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.AllBlockEntityTypes;
import com.zurrtum.create.client.AllBlockEntityBehaviours;
import com.zurrtum.create.client.content.redstone.link.LinkBehaviour;
import com.zurrtum.create.client.content.kinetics.transmission.SplitShaftVisual;
import com.zurrtum.create.client.foundation.blockEntity.behaviour.audio.KineticAudioBehaviour;
import com.zurrtum.create.client.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import com.zurrtum.create.client.foundation.item.ItemDescription;
import dev.simulated_team.simulated.client.BlockPropertiesTooltip;
import dev.simulated_team.simulated.compat.create.LegacyCustomKineticTooltipBehaviour;
import dev.simulated_team.simulated.compat.create.LegacyFilteringClientBehaviour;
import dev.simulated_team.simulated.compat.create.LegacyItemTooltips;
import dev.simulated_team.simulated.compat.create.LegacyKineticTooltipBehaviour;
import dev.simulated_team.simulated.compat.create.LegacyScrollValueClientBehaviour;
import dev.simulated_team.simulated.compat.create.LegacyTooltipBehaviour;
import dev.simulated_team.simulated.compat.create.SableCreateKineticRenderer;
import dev.simulated_team.simulated.compat.create.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionVisual;
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftRenderer;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorRenderer;
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorVisual;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableVisual;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerRenderer;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineRotationDirectionClientBehaviour;
import dev.simulated_team.simulated.content.blocks.spring.SpringRenderer;
import dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver.ModulatingLinkVisual;
import dev.simulated_team.simulated.content.blocks.redstone.redstone_inductor.RedstoneInductorRenderer;
import dev.simulated_team.simulated.content.blocks.redstone.redstone_inductor.RedstoneInductorVisual;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelRenderer;
import dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelVisual;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelLockingClientBehaviour;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingVisual;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverVisual;
import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringVisual;
import dev.simulated_team.simulated.content.items.merging_glue.MergingGlueItemHandler;
import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItemRenderer;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import dev.simulated_team.simulated.client.model.SimulatedItemModels;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimBlockEntityTypes;
import dev.simulated_team.simulated.index.SimResourceManagers;
import dev.simulated_team.simulated.index.ponder.SimPonderPlugin;
import com.zurrtum.create.client.catnip.render.SuperByteBufferCache;
import com.zurrtum.create.client.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import dev.simulated_team.simulated.compat.minecraft.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SimulatedClient {

    public static final PhysicsStaffClientHandler PHYSICS_STAFF_CLIENT_HANDLER = new PhysicsStaffClientHandler();
    public static PlungerLauncherItemRenderer.RenderHandler PLUNGER_LAUNCHER_RENDER_HANDLER = new PlungerLauncherItemRenderer.RenderHandler();
	public static final MergingGlueItemHandler MERGING_GLUE_ITEM_HANDLER = new MergingGlueItemHandler();

    public static void init() {
        dev.simulated_team.simulated.content.entities.diagram.screen.preview.DiagramPreviewRenderer.init(); // PORT-26.3: registers its pipelines before shaders load
        SimulatedItemModels.register();
        LegacyKineticTooltipBehaviour.installBridge();
        registerBlockEntityBehaviours();
        registerVisuals();

        ItemDescription.useKey(SimBlocks.IRON_HANDLE.get(), "block.simulated.handle");
        ItemDescription.useKey(SimBlocks.COPPER_HANDLE.get(), "block.simulated.handle");
        SimBlocks.DYED_HANDLES.forEach(block -> ItemDescription.useKey(block, "block.simulated.handle"));
        SimBlocks.NAMEPLATES.forEach(block -> ItemDescription.useKey(block, "block.simulated.nameplate"));
        SimBlocks.PORTABLE_ENGINES.forEach(block ->
                ItemDescription.useKey(block, "block.simulated.red_portable_engine"));

        LegacyItemTooltips.register(Simulated.getRegistrate());

        SimPartialModels.init();
        BlockPropertiesTooltip.init();
        SimResourceManagers.init();

        PonderIndex.addPlugin(new SimPonderPlugin());

        SuperByteBufferCache.getInstance().registerCompartment(SteeringWheelRenderer.STEERING_WHEEL);
    }

    private static void registerBlockEntityBehaviours() {
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.MODULATING_LINKED_RECEIVER.get(), LinkBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.SIMPLE_BE.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.TORSION_SPRING.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new, LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.ROPE_WINCH.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.PORTABLE_ENGINE.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new, PortableEngineRotationDirectionClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.SWIVEL_BEARING.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new, SwivelLockingClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.STEERING_WHEEL.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new, LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.SWIVEL_BEARING_LINK_BLOCK.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.AUGER_SHAFT.get(), LegacyKineticTooltipBehaviour::new, KineticAudioBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.DIRECTIONAL_GEARSHIFT.get(), LegacyCustomKineticTooltipBehaviour::new, KineticAudioBehaviour::new);

        AllBlockEntityBehaviours.add(SimBlockEntityTypes.PHYSICS_ASSEMBLER.get(), LegacyTooltipBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.ALTITUDE_SENSOR.get(), LegacyTooltipBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.GIMBAL_SENSOR.get(), LegacyTooltipBehaviour::new, LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.VELOCITY_SENSOR.get(), LegacyTooltipBehaviour::new, LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.THROTTLE_LEVER.get(), LegacyTooltipBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.REDSTONE_ACCUMULATOR.get(), LegacyTooltipBehaviour::new, LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.REDSTONE_INDUCTOR.get(), LegacyTooltipBehaviour::new, LegacyScrollValueClientBehaviour::new);

        AllBlockEntityBehaviours.add(SimBlockEntityTypes.OPTICAL_SENSOR.get(), LegacyScrollValueClientBehaviour::new, LegacyFilteringClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.LASER_POINTER.get(), LegacyScrollValueClientBehaviour::new);
        AllBlockEntityBehaviours.add(SimBlockEntityTypes.LASER_SENSOR.get(), LegacyFilteringClientBehaviour::new);
    }

    private static void registerVisuals() {
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.SIMPLE_BE.get()).factory(AnalogTransmissionVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.TORSION_SPRING.get()).factory(TorsionSpringVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.GIMBAL_SENSOR.get()).factory(GimbalSensorVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.SWIVEL_BEARING.get()).factory(SwivelBearingVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.STEERING_WHEEL.get()).factory(SteeringWheelVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.THROTTLE_LEVER.get()).factory(ThrottleLeverVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.NAVIGATION_TABLE.get()).factory(NavTableVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.MODULATING_LINKED_RECEIVER.get()).factory(ModulatingLinkVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.REDSTONE_INDUCTOR.get()).factory(RedstoneInductorVisual::new).apply();
        SimpleBlockEntityVisualizer.builder(SimBlockEntityTypes.DIRECTIONAL_GEARSHIFT.get()).factory(SplitShaftVisual::new).apply();
    }
}
