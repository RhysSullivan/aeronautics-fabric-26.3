package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import dev.ryanhcode.sable.mixinterface.sublevel_render.SubLevelBlockEntityBatches;
import dev.ryanhcode.sable.sublevel.render.blockentity.SubLevelBlockEntityRendering;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

/** PORT-26.3: carries the sub-level block-entity batches from extraction to submission. */
@Mixin(LevelRenderState.class)
public class LevelRenderStateBlockEntityMixin implements SubLevelBlockEntityBatches {
    @Unique
    private final List<SubLevelBlockEntityRendering.Batch> sable$subLevelBlockEntities = new ArrayList<>();

    @Unique
    private final List<SubLevelBlockEntityRendering.Breaking> sable$subLevelBreaking = new ArrayList<>();

    @Override
    public List<SubLevelBlockEntityRendering.Breaking> sable$subLevelBreaking() {
        return this.sable$subLevelBreaking;
    }

    @Override
    public List<SubLevelBlockEntityRendering.Batch> sable$subLevelBlockEntities() {
        return this.sable$subLevelBlockEntities;
    }
}
