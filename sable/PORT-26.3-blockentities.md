# Block entities on sub-levels, Minecraft 26.3 (this server's private port)

Branch `rhys/26.3-blockentities` (from `rhys/26.3`). Covers gap 1 of `PORT-26.3.md` and `simulated/PORT-26.3.md`:
block entities and Create machinery drawn on moving sub-levels (ships), under the default renderer and Sodium 0.9.2.

## Design

Two paths. Every block entity on a ship goes through exactly one of them.

**1. Renderer-drawn block entities** (vanilla ones, and every Create or Simulated part with Flywheel off):
`sublevel/render/blockentity/SubLevelBlockEntityRendering`, with three mixins in `mixin/sublevel_render/block_entity_render/`.

- **Extract** (`LevelExtractorBlockEntityMixin`):
  - It wraps the `extractVisibleBlockEntities` call inside `LevelExtractor.extract`, not the method itself. Sodium
    cancels that method at its head and extracts world block entities its own way, so the wrap runs after whichever
    of the two ran.
  - It first drops plot-space states from the main list. The client level's "globally rendered" set holds every block
    entity whose renderer draws off screen, plot ones included, and both vanilla and Sodium would otherwise extract
    them at plot coordinates, about 1.3 million blocks out.
  - Then, for each chunked sub-level, it extracts the block entities of its compiled sections, with the block-entity
    dispatcher's camera moved into the ship's plot space. Distance checks and camera-facing renderers (sign text, for
    example) then see the camera where it really is relative to the ship.
  - It uses the compiled section lists rather than the plot chunks' block-entity maps. Create Fly's `SectionCompiler`
    hook already leaves out block entities Flywheel draws, so the two paths never draw the same block entity twice.
    Sable compiles ship sections through vanilla's `SectionCompiler` with or without Sodium, so the hook applies in
    both cases.
  - Light is the plot's light (torches and burners aboard) with its sky part scaled by the ship's sky light, exactly
    as Sable lights the ship's terrain.
  - The batches ride on the `LevelRenderState` (`LevelRenderStateBlockEntityMixin`).
  - EntityCulling's block-entity culling is suspended during extraction, as in the 1.21 path.
- **Submit** (`LevelRendererBlockEntityMixin`):
  - Straight after the world's block entities in `submitFeatures`, each ship's states are submitted with the pose
    `T(pivotWorld - camera) * R * S * T(blockPos - pivot)`.
  - It is built from the same `renderPose()` the ship's terrain uses that frame, copied once on the render thread.
  - Every term stays small. Plot positions are about 1.3 million blocks out, where a float has 0.125 of precision, so
    the 1.21 approach (translate to the plot position, then undo it inside the matrix) would jitter.
  - While a ship is submitted, the camera render state's position and orientation are moved into its plot space,
    for renderers that face the camera.
  - Break progress on a ship block entity gets a crumbling overlay with the same pose.
- The renderers keep running on the normal 26.3 submit path. Simulated's legacy buffer renderers go through its
  `RenderBridge`, which records vertices with the pose it is given at submit time, so the ship transform reaches them
  unchanged. Nothing has to register with Sable for a block entity to be drawn: the 1.21
  `SubLevelBlockEntityRenderRegistry` and Simulated's immediate-renderer registrations stay excluded or unused.

**2. Flywheel visuals** (Create and Simulated parts with Flywheel on): `compatibility/create/flywheel/SubLevelFlywheelEmbeddings`
and `mixin/compatibility/create/flywheel/*`.

- These are applied only when Create Fly is loaded; the mixin plugin gates the `compatibility.create` package on mod
  id `create`. Sable compiles against Create Fly `compileOnly`.
- This is the design of upstream Sable's NeoForge Flywheel compatibility, ported to Create Fly's repackaged
  Flywheel. Upstream's custom Flywheel light storage, scene shader and matrix buffer are not ported.
- Flywheel still tracks ship block entities itself, through its own section-compile hook. `BlockEntityStorage.createRaw`
  is wrapped so a ship block entity's visual is created inside one `VisualEmbedding` per ship.
  - The embedding's render origin is the plot's centre block, so instance positions stay small.
  - Its model matrix `T(pivotWorld - rootOrigin) * R * S * T(embeddingOrigin - pivot)` is set once per frame on the
    render thread, in `VisualizationManagerImpl.beginFrame` after the wait for the previous tick plan and before the
    frame plan runs.
  - Ship poses are snapshotted there. Visuals can be created on Flywheel's worker threads, where Sable's cached render
    pose must not be touched.
- Embeddings are deleted with their ship (their visuals are removed), and cleared when Flywheel recreates every visual
  (render-origin move, backend change, reload) or invalidates.
- Ship visuals are never frustum-culled or distance-limited. Flywheel would test plot positions against the root
  frustum and camera. A ship holds few visuals, and culling them would freeze their animation.
- Light of embedded visuals (`relight`, `computePackedLight`) is plot light with the ship's sky scale, as above.
  Flywheel's own shader light (`smooth_when_embedded`) is not extended to ships, so they get flat light per block
  entity, as on the fallback path.

**A fix outside Sable, in Simulated** (`simulated/mixin/create_render/EntityBlockLayerMixin`, next to the port's `EntityBlockMultipleLayerMixin`):
- Create Fly 26.3-6.0.9-2's `EntityBlockLayer.renderInto(Pose pose, ...)` multiplies the caller's pose by its own
  parameter (it shadows the layer's `pose` field) instead of by the layer's transform. `EntityBlockLightLayer` does
  it right.
- So every buffer drawn through `SuperByteBuffer.renderInto` lands at about twice its camera-relative offset, off
  screen. Simulated's legacy renderers draw that way whenever Flywheel is off: no working backend, or Iris with a
  shader pack. The steering wheel, the throttle lever handle and the other legacy parts were therefore invisible
  everywhere, ships or not. Shown on the rig before the fix: `vq_w0_controls_in_world.png`, world, default renderer.
- The mixin passes the layer's own pose. Create Fly itself calls `renderInto` only twice, which is likely why it went
  unnoticed there.

## What is drawn (proved on the test rig)

All on a cloud VM, with **software rendering only** (Mesa llvmpipe under Xvfb, 854x480): the
VMs have no GPU. Server and client run Create Fly 26.3-6.0.9-2, Sable and Simulated from this branch, and Fabric API
0.166.2. "Sodium" means Sodium 0.9.2, the pack's version.

The test ship is a 7x7 deck assembled with `sable assemble area`. On it:
- vanilla: a chest, an oak sign with text, a banner, a lit furnace, a bell and a torch;
- Create: two creative motors, a shaft, a cogwheel, a 3-long belt, a depot holding a diamond and a lit blaze burner;
- Simulated: a steering wheel and a throttle lever.

The ship is pushed up and sideways with a spin about two axes, teleported tilted about two axes, and photographed
while it moves, from two sides each time. The test script is part of the unpublished test rig. Screenshots and run logs
are kept with the unpublished test rig.

| What | Default renderer (Flywheel off, see gaps) | Sodium, Flywheel `instancing`/`indirect` | Sodium, Flywheel off |
|---|---|---|---|
| Vanilla block entities on the ship, placed and oriented as it moves and turns | `van2_m1..m4`, `van2_t1_*` | `sod2_m1..m4`, `sod2_t1_*` | `sodoff_m*`, `sodoff_t1_*` |
| Chest lid opens when a player uses the chest on the ship | `van2_a2_chest_open` | `sod2_a2_chest_open` | `sodoff_a2_*` |
| Sign text legible on the ship (plot-space camera) | `*_a2_chest_open` | same | same |
| Light: night shot, torch, furnace and burner aboard; block entities lit like the hull | `van2_n1_*` | `sod2_n1_*` | `sodoff_n1_*` |
| Create kinetics animate on the ship (frame diff 0.7 s apart: shaft and cogwheel, belt surface, burner, banner) | `van2_r3_rest_south_a/b` | `sod2_r3_*` | `sodoff_r3_*` |
| Depot item; belt running on the ship and carrying its item (two frames) | `lv_e1_belt_survived_a/b` | `ls_e1_belt_survived_b` | depot: all shots |
| Sky light: a stone roof over the resting ship; hull and block entities darken together | `lv_l0_open_sky`, `lv_l1_under_roof` | `ls_l0_*`, `ls_l1_*` | |
| Sable alone (no Create Fly, no Simulated): joins with no mixin error, vanilla block entities drawn on the ship | `so_r1..r3_*` | | |
| Steering wheel and throttle lever drawn and animating (wheel turns, lever handle swings to state 14) | `van2_controls_grid.png` | `sod2_controls_grid.png` | `sodoff_controls_grid.png` |
| Same controls in the plain world, before assembly (the `renderInto` fix) | `van2_w0_*`; broken before: `vq_w0_controls_in_world` | `sod2_w0_*` | `sodoff_w0_*` |
| A client joining with the ship already present | | `sod4_j1_*`, `sod4_j2_rejoined_controls` (`indirect`) | `sod3_j1_*`, `sod3_j2_*` |
| Block outline on a ship block (high-contrast outline; world-block control in the same run) | `van5_o1_*`, `van5_o0_*` | `sod5_o1_*`, `sod5_o0_*` | |
| Break decal on a rotated ship block while mining | `van5_o2/o3_*` | `sod4_o3_*`, `sod5_o2/o3_*` | |
| F3+T (resource reload): ship sections marked dirty and recompiled, textures correct | rebuild log line (vanilla's dispatcher then recompiles asynchronously) | rebuild log line, then Sable's "compiled 1 sub-level sections on the render thread" (`logs/Drew-ls.out`), `ls_x1_*` | |

After rebasing onto `rhys/26.3` at `f832b00`, the branch was rebuilt and re-checked on both renderers:
`rebased_check_grid.png`, with `rbv_*` for the default renderer and `rbs_*` for Sodium with Flywheel `indirect`.

Summaries are in `outline_decal_grid.png` and `sod2_motion_grid.png`. Which Flywheel backend ran is read from the
client log in every run (`The current Flywheel backend is ...`).

## What is not drawn, or not proved

- **The Flywheel path is proved under Sodium only.** Under the default renderer on this rig, Flywheel's `indirect`
  and `instancing` backends both fail on the first frame and fall back to `flywheel:off`. The error is "Uploading
  texture ... Close the existing render pass before performing additional commands", from Create Fly's `afterSolid`
  hook, which runs inside vanilla's open solid pass. It does not come from this branch's code; it reproduces in the
  plain world. So, without Sodium, everything Create or Simulated on a ship is drawn by the fallback path, which is
  proved. Whether Flywheel itself runs on the default renderer on a real GPU is a question for Create Fly.
- **A hand-built belt sometimes disappears in the world, before any assembly.** It is placed with `setblock` and
  belt NBT. In 2 of 8 passes its block entities were gone within seconds of placing it. This is server-side Create
  behaviour and does not come from this branch. When the belt exists, it survives `sable assemble` with its
  controller and source remapped into the plot, keeps running (speed -16), and carries its item along on the ship
  under both renderers (`lv_e1_belt_survived_a/b`, `ls_e1_belt_survived_b`).
- ~~Single-block sub-levels draw nothing~~: they do. They use the chunked path, see `PORT-26.3-render2.md`.
- Ship Flywheel visuals get **flat light**, as on the fallback path; Flywheel's smooth shader light is not extended to
  ships. They are never frustum-culled or distance-limited.
- Flywheel's own crumbling overlay for **visualised** block entities on ships is not handled (cause and the work it needs: `PORT-26.3-render2.md`, item 4). With Flywheel on, a shaft being
  mined on a ship is expected to show no cracks on its moving parts. This was not tested.
- Not tested: Iris with a shader pack (Mesa's software driver segfaults in Iris's composite pass, as recorded in
  `STATUS.md`), a real GPU, and two clients.
- Left alone as asked: the fog gap and rain splashes.

## Frame cost

The same ship and the same camera are measured with sub-level block entities on and off (the measuring switch
below), alternated three times, ten 1-second fps samples each, medians. Software rendering, 854x480, 8 llvmpipe
threads. The ship carries 16 block entities. These numbers show the relative cost only; a player's GPU will differ.

| Configuration | With | Without | Cost per frame |
|---|---|---|---|
| Default renderer (Flywheel off: every part drawn by its renderer) | 149 fps | 182 fps | about 1.2 ms |
| Sodium, Flywheel off | 159 fps | 178 fps | about 0.7 ms |
| Sodium, Flywheel `instancing` (only the vanilla block entities are switched; the Create and Simulated parts are Flywheel visuals in both) | 177 fps | 176 fps | none measurable |

The extraction itself is one pass over the ship's compiled sections per frame, plus a removal pass over the main
block-entity list, which tests a plot-bounds check per entry.

## Test rig (not published)

The rig (a cloud VM harness, a test client driver and the test script `bt.py`) is not published.

## Measuring aid

`-Dsable.debug.skipSubLevelBlockEntities=true` (read every frame) skips sub-level block-entity extraction. It is
used by the test driver to measure the same scene with and without ship block entities. It has no other effect.
