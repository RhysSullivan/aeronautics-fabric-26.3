package com.tterrag.registrate.fabric;

import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;

public record FluidData(String translationKey, int light) {

    public interface RenderHandlerFactory {
        Object create(Identifier stillTexture, Identifier flowingTexture);
    }

    public static RenderHandlerFactory createDefaultHandler() {
        return (stillTexture, flowingTexture) -> {
            // 26.3: fluids are drawn from a FluidModel; the old handler used the flowing texture as the overlay, untinted.
            return new FluidModel.Unbaked(new Material(stillTexture), new Material(flowingTexture), new Material(flowingTexture), null);
        };
    }

    public static  <T extends SimpleFlowableFluid> void registerRenderHandler(NonNullSupplier<RenderHandlerFactory> renderHandler, T entry, Identifier stillTexture, Identifier flowingTexture) {
        EnvExecutor.runWhenOn(EnvType.CLIENT, () -> () -> {
            final FluidModel.Unbaked model = (FluidModel.Unbaked) renderHandler.get().create(stillTexture, flowingTexture);
            FluidRenderingRegistry.register(entry.getSource(), entry, model);
        });
    }
}
