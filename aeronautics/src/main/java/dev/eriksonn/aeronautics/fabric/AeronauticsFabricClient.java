package dev.eriksonn.aeronautics.fabric;

import com.zurrtum.create.client.catnip.placement.PlacementClient;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.AeronauticsClient;
import dev.eriksonn.aeronautics.events.AeronauticsClientEvents;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import foundry.veil.api.network.VeilPacketManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

public final class AeronauticsFabricClient implements ClientModInitializer {
    private static final int PRODUCTION_SMOKE_REQUIRED_TICKS = 20;
    private static final Identifier LEVITITE_BLEND_STILL = Aeronautics.path("fluid/levitite_blend_still");
    private static final Identifier LEVITITE_BLEND_FLOW = Aeronautics.path("fluid/levitite_blend_flow");
    private static final AtomicInteger PRODUCTION_SMOKE_TICKS = new AtomicInteger();

    @Override
    public void onInitializeClient() {
        VeilPacketManager.registerClientReceivers();
        FabricAeroParticleTypes.registerFactories();

        // PORT-26.3: fluids are drawn from a FluidModel; block and fluid layers come from texture transparency, so
        // the levitite blend is forced translucent through its materials. Levitite blocks follow their textures.
        FluidRenderingRegistry.register(
                FabricAeroFluids.LEVITITE_BLEND.getSource(),
                FabricAeroFluids.LEVITITE_BLEND.get(),
                new FluidModel.Unbaked(new Material(LEVITITE_BLEND_STILL, true), new Material(LEVITITE_BLEND_FLOW, true),
                        null, null));

        AeronauticsClient.init();

        // the burner flame queue's world frame: fabric/mixin/burner_flame/LevelRendererBurnerFlameMixin
        ClientTickEvents.START_CLIENT_TICK.register(client -> AeronauticsClientEvents.clientLevelTick(false));
        ClientTickEvents.END_CLIENT_TICK.register(client -> AeronauticsClientEvents.clientLevelTick(true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Boolean.getBoolean("aeronautics.productionSmokeTest")
                    && PRODUCTION_SMOKE_TICKS.incrementAndGet() == PRODUCTION_SMOKE_REQUIRED_TICKS) {
                // Create normally loads this only after joining a world. Exercise its mixins in production too.
                PlacementClient.tick(client);
                Aeronautics.LOGGER.info("AERONAUTICS_PRODUCTION_CLIENT_SMOKE_OK");
                client.stop();
            }
        });
    }
}
