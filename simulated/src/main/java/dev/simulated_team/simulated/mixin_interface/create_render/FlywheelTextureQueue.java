package dev.simulated_team.simulated.mixin_interface.create_render;

import net.minecraft.resources.Identifier;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** PORT-26.3: see {@code mixin.create_render.FlywheelTexturePreloadMixin}. */
public interface FlywheelTextureQueue {
    Queue<Identifier> QUEUE = new ConcurrentLinkedQueue<>();

    boolean simulated$isLoaded(Identifier id);
}
