package dev.ryanhcode.sable.mixinterface.sublevel_render;

import net.minecraft.world.level.Level;

/** PORT-26.3: Flywheel's block-entity storage with sub-level embeddings (only applied when Create Fly is loaded). */
public interface SubLevelFlywheelStorage {
    /** Render thread, before Flywheel's frame plan. */
    void sable$preFrame(Level level);
}
