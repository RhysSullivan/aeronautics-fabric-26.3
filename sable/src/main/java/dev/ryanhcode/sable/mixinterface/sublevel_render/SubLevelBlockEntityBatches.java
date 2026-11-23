package dev.ryanhcode.sable.mixinterface.sublevel_render;

import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;

import java.util.List;

/** PORT-26.3: the sub-level block-entity batches a {@code LevelRenderState} carries from extract to submit. */
public interface SubLevelBlockEntityBatches {
    List<SubLevelBlockEntityRendering.Batch> sable$subLevelBlockEntities();

    List<SubLevelBlockEntityRendering.Breaking> sable$subLevelBreaking();
}
