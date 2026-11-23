# Sable on Minecraft 26.3: staged gaps (this server's private port)

Everything here compiles and is meant to load; these parts are switched off until they are re-done for 26.3.

| Part | Why | State |
|---|---|---|
| Sub-level rendering, vanilla path | 26.3 renders terrain from pooled section geometry with one model view per draw set | re-done: `impl/vanilla/LevelRendererSubLevelTerrainMixin` (own draw set per sub-level in the same pass); the 1.21.x `LevelRendererMixin` stays excluded |
| Sub-level rendering, Sodium path | Sodium replaces the section dispatcher (ignores compiles, empty buffer pool) and the terrain object | re-done: `SubLevelSectionDispatcher` (private dispatcher and buffers, sync compile, upload before the main pass) plus the same per-sub-level draw sets after Sodium's terrain; the 1.21.x `SodiumWorldRendererMixin` stays excluded |
| Block-entity lighting for the Sodium path (`SubLevelLightVertexConsumerProvider`) | Fabric's old renderer API and `MultiBufferSource` are gone | excluded; block entities on sub-levels are drawn by the block-entity branch's path instead (merged) |
| Block entities on sub-levels (`block_entity_render/LevelRendererMixin`, `VanillaSubLevelBlockEntityRenderer`, `api/client/SubLevelBlockEntityRenderRegistry`) | submit-node rendering replaced buffer sources | excluded |
| Block outlines on sub-levels (`fabric/mixin/block_outline_render`) | uses the removed `LevelRenderer.level` and old outline rendering | excluded |
| Block break decals on sub-levels (`block_decal_render/LevelRendererMixin`) | as above | excluded |
| Section dirty tracking (`RenderSectionMixin`) | `RenderSection` has no dirty flag in 26.3 | replaced: tracked in `VanillaChunkedSubLevelRenderData`; hook moved to `LevelExtractor.setSectionDirty` |
| Landing on tilted sub-levels (`entity_rotations_and_riding.BlockMixin`, the fall-on wrap in `entity_sublevel_collision.EntityMixin`) | `Block.updateEntityMovementAfterFallOn` is gone; landing is in `Entity.restituteMovementAfterCollisions` | removed |
| Rain splash particles on sub-levels (`stop_rain` tickRainParticles wrap) | rain particles moved to `ClientLevel` | removed (rain itself is still stopped under decks via `extractRenderState`) |
| Rebuild on resource reload under Sodium | Sodium cancels `invalidateCompiledGeometry`, where the rebuild hook sits | gap: after F3+T with Sodium, sub-levels keep old meshes until they change |
| Fog on sub-level blocks | each sub-level is drawn with its own model view; the shader measures fog from `pos`, which is relative to the sub-level pivot | gap: far sub-levels get too little fog |
| Directional shading, debug render, UDP transport | already excluded in the 1.21.11 port | unchanged |

## Gaps a player would notice, most visible first

1. ~~Block entities on sub-levels are not drawn~~: done on the block-entity branch (merged); see `sable/PORT-26.3-blockentities.md`.
2. ~~Resource reload under Sodium leaves sub-levels stale~~: done on the block-entity branch (merged).
3. ~~No block outline or break decals on sub-level blocks~~: done on the block-entity branch (merged).
4. **Landing on a tilted sub-level** uses the world's up, not the ship's (the fall-on hooks are removed).
5. ~~Fog is measured from the sub-level's pivot~~: fog on sub-level blocks fixed (terrain anchored at the camera's
   position in the sub-level's frame).
6. ~~No rain splash particles on decks~~: rain splash particles on decks fixed (`stop_rain/ClientLevelRainSplashMixin`).
7. Single-block sub-levels draw (`isSingleBlock` is always false, so they use the chunked path; see
   `PORT-26.3-render2.md`).
8. New: sub-level terrain takes its sky-light scale through per-sub-level lightmaps (`SubLevelLightmaps`).
9. Remaining: Flywheel crack overlays on ship visuals, Flywheel smooth light on ships.

## Decision: synchronous compiles on Sable's own section dispatcher

**When it applies.** Only when the game's section dispatcher is not vanilla's, which is the case with Sodium.
Without Sodium, sub-level sections use vanilla's dispatcher and its normal asynchronous path.

**What was observed** on a boat client with Sodium 0.9.2, before the change:
- 3 sub-level sections were created and queued (`queue 3`).
- None compiled (`compiled 0`, `slices 0`), with `free buffers 0` in the dispatcher's pool, and no error in the
  log.
- A thread dump showed no vanilla background ("Worker-Main") threads, only Sodium's own executors.

**What was ruled out:**
- *The render hook not running*: it ran every frame, and draw sets were built and drawn after Sodium's terrain.
- *A null camera position stalling the queue*: the camera is set before compiling, with no change.
- *The game's buffer pool being allocated empty under Sodium*: Sable's dispatcher was given its own
  `RenderBuffers(2)`; still 0 free and still queued.
- *The compile itself failing*: the same sections compile at once and without error when compiled synchronously.

**What remains unexplained.** `SectionRenderDispatcher.runTask` takes a buffer pack, and on a
NullPointerException it puts the task back without releasing the pack or rescheduling itself. On the private
dispatcher that path apparently runs, leaks both packs, and swallows the error. Which NullPointerException it is
(the region? a Sodium change to block meshing off the render thread?) was not found. Finding it needs a debugger
or a patched `runTask` that logs.

**So for now.** On Sable's own dispatcher, sub-level sections are compiled on the render thread. The cost is
reported in the log whenever a batch takes over 4 ms (see the measurement below).

**Measured cost** (boat client, 16 vCPU EPYC, Sodium 0.9.2; `notes/sable-sync-compile-cost.log` in create-port):

| Batch | Sections | Time |
|---|---|---|
| 17x17 plank deck, first compile in the session (includes JIT warm-up) | 4 | 14.1 ms |
| 33x33 plank deck | 9 | 8.4 ms |

That is about 1 ms per thin section once warm. Dense sections cost more; I estimate 2 to 5 ms for a full 16x16x16
section of mixed blocks, but did not measure it.

**The limit.**
- With a 16 ms frame, a batch of more than about 10 to 15 sections is a visible hitch.
- A ship is compiled in full only when it first appears (assembly, or coming into view).
- After that, placing or breaking a block recompiles one section, about 1 ms.
- So the cost is a one-off hitch when a large ship is assembled or loaded, not ongoing frame cost.

**Ways out if it bites:**
- spread dirty sections over frames (a per-frame budget in `VanillaChunkedSubLevelRenderData.compileSections`);
- or find and fix the background-path failure above.

The frame-rate samples in that run were capped at 30 by Minecraft's idle limit and are not a measurement.
