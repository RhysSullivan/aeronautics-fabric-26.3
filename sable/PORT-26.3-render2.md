# Rendering and visual gaps, second pass (Minecraft 26.3, this server's private port)

Branch `rhys/26.3-render2`, on `rhys/26.3` after `rhys/26.3-blockentities` was merged (`44101a3`). This follows
`PORT-26.3-blockentities.md`. Proofs are on the test rig with **software rendering only** (Mesa llvmpipe, 854x480),
under the default renderer and Sodium 0.9.2. Screenshots and run and client logs are kept
with the unpublished test rig. The test script is part of the unpublished test rig. Prefixes `v6`..`v9`/`va` are the default renderer,
`s6`..`s9`/`sa` Sodium.

## 1. Single-block sub-levels: already drawn

- `VanillaSubLevelRenderDispatcher.isSingleBlock` always returns false in this port. So one-block sub-levels use the
  ordinary chunked path, which draws them. The single-block fast path it would select was the part removed.
- The "single-block sub-levels draw nothing" line in `PORT-26.3.md` (which I repeated) was stale, not a bug.
- Shown: a one-block plank and a one-block chest, falling and at rest (`v6_s1_single_falling`, `v6_s2_single_resting`).
  The chest is a block entity, so the block-entity path covers them too.
- `VanillaSingleSubLevelRenderData` is now dead code.

## 2. Flywheel-drawn ship parts: lighting and culling

**Lighting.** The hull itself was the odd one out:
- upstream Sable dimmed a ship's terrain by its sky-light scale (0..15, from where the ship is) through a Veil shader
  uniform, which the port removed. Ship terrain therefore always had full plot sky light;
- block entities, entities and particles on ships did apply the scale.

Under cover, Flywheel parts went black on a bright hull (`v8_d1_dark_ship_enclosed`). The fix gives the hull the same
rule (`sublevel/render/vanilla/SubLevelLightmaps`, `ChunkSectionsToRenderLightmapMixin`):
- 26.3's terrain shader takes light only from the 16x16 lightmap (block light along x, sky light along y);
- each sky scale in use gets a 16x16 copy of the game's lightmap with sky row y taken from row floor(y*scale/15);
- the copy is rendered on the GPU before the main pass by a tiny `sable:core/sublevel_lightmap` pipeline (16 variants
  via a shader define, no uniforms; the game's lightmap cannot be copied directly, it lacks copy-source usage);
- it is bound in place of the game's lightmap while that ship's terrain draws, under both renderers.

Block light from a ship's own torches is unchanged. The fallback path needed one more piece:
- Create's renderers look light up themselves during extraction (`LightCoordsUtil.getLightCoords`), overwriting the
  scaled value;
- so every such lookup made while a ship's block entities are extracted now gets its sky scale (render thread only,
  `block_entity_render/LightCoordsUtilMixin`).

Result: hull and parts darken together under cover and come back in the open, with Flywheel on (`v9_d0..d2`,
`s9_d0..d2`, `vc_d*`) and off (`sc_d*`).
- Flywheel visuals are also relit when their ship's sky scale changes; before, they only relit on light updates
  inside the plot.
- Flywheel ship parts stay flat-lit per block entity, as the fallback path is. Smooth Flywheel light would need its
  light LUT in plot space (upstream's light-storage and shader work). Not done.
- As upstream designed, world block light never reaches ship blocks: a torch beside a ship does not light it
  (`v9_d1b`). Only light inside the ship's plot (its own torches, burners) does.

**Culling.**
- Flywheel ship visuals are frustum-tested and distance-limited at their world position under this frame's ship
  pose. Before, they were never culled.
- On the fallback path, a ship whose bounds (last and current tick) are off screen keeps only the block entities whose
  renderers draw outside their block (`shouldRenderOffScreen`: lasers, beams). Vanilla never frustum-culls those
  either. A laser on a ship still draws with the ship out of view (`vb_l1_laser_on_ship`, `vb_l2_laser_beam_only`).
  Create's and Simulated's machinery renderers all say they draw off screen, so with Flywheel off this cull only saves
  vanilla block entities.
- Correctness: the moving, tilted and night ships draw all their parts under Flywheel `indirect` (`va_*`, `sa_*`).
- The CPU saving was not measured: on llvmpipe it would be lost in noise.

## 3. Flywheel on the default renderer: a real Create Fly bug, now backported

- Without Sodium, Flywheel fails on its first frame and falls back to `flywheel:off`.
- Cause: its material setup and `TextureBinder` fetch textures with `TextureManager.getTexture` while drawing, inside
  vanilla's open main render pass. A texture not loaded yet is uploaded right there, and 26.3's command encoder refuses
  any upload inside a pass ("Close the existing render pass before performing additional commands").
- The check is unconditional, so it is **not** the software driver: any player on Create Fly 26.3-6.0.9-2 without
  Sodium gets the slow path.
- Create Fly fixed it upstream on 2026-11-13 (commit `a25c2f0`, "Fix Flywheel texture register and load"). The newest
  release, 6.0.9-2 of 2026-11-07, predates that fix.
- Backport in Simulated (`mixin/create_render/FlywheelTexturePreloadMixin` and its two helpers): an unloaded texture
  is queued, the block atlas is bound for that one frame, and the queue is loaded at the start of the next level
  render, outside any pass. With `require = 0`, it does nothing on a Create Fly release that no longer fetches
  textures there.
- Result: Flywheel stays on `indirect` under the default renderer.
- Under Sodium it had sometimes dropped to `instancing` before. Since the backport it has always stayed on `indirect`
  in these runs. That is an observation, not a proven cause.
- **Behaviour change:** players without Sodium now get Flywheel, so Create parts there take the Flywheel path instead
  of the renderer path.
- Switching the backend off, back to default and off again with a ship present draws the controls identically each
  time (`sb_x1_off`, `sb_x2_default`, `sb_x3_off_again`).

## 4. Flywheel's crack overlay on its parts aboard a ship: not done (wall)

- Flywheel's crumbling programs use the `CRUMBLING` shader context, which never defines `FLW_EMBEDDED`, so they ignore
  embedding matrices. A crack for a ship visual would be drawn at its ship-local offset from the root origin, near the
  camera, not on the ship.
- I briefly handed ship break entries to Flywheel, saw no cracks on the shaft (`v6_k_zoom`), found this cause, and
  took the hand-over out again.
- Doing it properly takes four changes to Flywheel's engine in Create Fly:
  - a crumbling program variant with `FLW_EMBEDDED`;
  - binding the matrix buffer in the indirect crumbling path;
  - a guaranteed identity matrix in slot 0;
  - per-draw model matrices in the instancing crumbling path.
- Each of those risks the cracks on world blocks.
- Control: the same shaft mined in the plain world with Flywheel on shows its crack marks (`vb_k0_zoom.png`), so this
  is specific to ships.
- Today: the ship block itself still breaks normally, and non-Flywheel ship blocks show cracks (first branch). Only the
  moving parts of a mined shaft or cogwheel show no cracks.

## 5. Fog and rain splashes

**Fog.**
- The terrain shader measures fog from `pos = plotVertex - anchor - frac(camera)`. The sub-level terrain draw now
  anchors at the camera's position in the ship's own frame instead of at the pivot.
- So `|pos|` is the true camera distance (rotation keeps lengths) to within a block. Spherical fog is exact;
  cylindrical fog is exact for yaw and approximate under pitch or roll.
- Shown: the ship and an identical deck built in the world, side by side at 75, 85 and 95 blocks, fade to the same
  tint (`v9_fog_zoom.png`, `s9_g1_fog_95`).

**Rain splashes.**
- 26.3 moved them to `ClientLevel.tickWeatherEffects`. A new `stop_rain/ClientLevelRainSplashMixin` raises the column
  height to a deck above the terrain, with the same rule as the port's rain stop (helper copied to
  `mixinhelpers/stop_rain/SubLevelRainHeight`).
- Splashes land on a deck resting on pillars, and the ground beneath it gets none (`v8_w1_zoom.png`, `v8_w2_zoom.png`).

## 6. Simulated visual stubs

**Laser.** Back on 26.3 pipelines:
- `simulated:core/laser` (the 1.21.11 port's shaders, moved to 26.3 includes and explicit locations);
- two passes, attenuation (`ZERO, ONE_MINUS_SRC_ALPHA`) then emission (`SRC_ALPHA, ONE`), no depth write, both
  submitted in that order by the beam's renderer. The 1.21 deferred "after clouds" queues are gone, since 26.3's submit
  model has no such hook;
- with a shader pack on, the beam is drawn as vanilla lightning, which Iris maps to its own program (the custom
  pipelines are unknown to it), instead of not at all;
- shown: `v9_i1_lasers`, `v9_i1b_zoom.png`, `s9_i1b_lasers_side`.

**Physics staff and plunger launcher.** Rebuilt on 26.3's item model API, in the shape of Create Fly's own animated
items (layers with a local transform, Create's item-glowing render types):
- staff: body, glowing core, ring and the two sigma petals opening with the beam. The two cubes are drawn by a small
  special renderer, because they need the final pose for the first-person orientation and the beam's focus capture;
- launcher: body (capturing the tether's focus point), two plungers with the reload slide, the joint and the spool;
- shown in hand, first and third person (`v9_i2..i5`, `s9_i2..i5`, `v9_i2_zoom.png`);
- the staff's cubes have scale 0 unless the beam is active, as before. The beam and drag animation, and the cubes
  appearing, were not exercised: they need a staff drag on a sub-level, which the harness does not do yet.

**Diagram screen's 3D ship preview: proposal, not built.**
- The original drew the ship (and anything welded to it) into a Veil framebuffer with its own projection, then ran a
  Veil post pipeline (`outline_diagram`) for the outline style, and composited the result into the screen. Veil has no
  26.x build.
- Without it, on 26.3:
  - draw the preview through 26.3's picture-in-picture GUI renderers, the same mechanism as the inventory's entity
    preview and the book model (`gui/render/pip/PictureInPictureRenderer`, registered through Fabric's GUI element
    registry). That gives an offscreen texture of the right size and the screen composition for free;
  - fill it either:
    - (a) with a mesh of the ship's block models, tessellated once per ship and rebuilt when it changes: simple and
      independent of Sable's terrain internals, but slower on very large ships; or
    - (b) by replaying Sable's compiled section buffers (the same draw sets as in the world) with the preview's model
      view: fast, but tied to the terrain path and to Sable's private dispatcher under Sodium;
  - port the outline look as one more pass over that texture, with a 26.3 pipeline of the `screenquad` +
    `InSampler` kind used here for the sub-level lightmaps.
- Estimate: about 500 to 800 lines, two to three days with testing. I would build (a) first.

## Sable on its own

Sable without Create Fly or Simulated still joins with no mixin errors and draws vanilla block entities on a ship
(`so2_r*`). The new lightmap pipelines and mixins load without Create.

## Frame cost

Same ship and camera, ship block entities switched on and off (`sable.debug.skipSubLevelBlockEntities`), medians of
30 one-second samples, software rendering, now with Flywheel on (`indirect`) under both renderers:

| Configuration | With | Without | Extra per frame |
|---|---|---|---|
| Default renderer, Flywheel `indirect` | 73 fps | 77 fps | about 0.7 ms |
| Sodium, Flywheel `indirect` | 97 fps | 107 fps | about 1.1 ms |

Absolute rates are lower than in the first branch's table because Flywheel's GPU culling now actually runs, and on
llvmpipe that work runs on the CPU. On a real GPU it is the fast path. The sub-level lightmap passes are a 16x16 draw
per sky level in use per frame.

## What most needs a real GPU look

1. **Hull lighting under cover** (the per-ship lightmaps; **behaviour change: every hull now dims under cover**), with and without Sodium, and with a shader
   pack (Iris replaces terrain shaders and may ignore the swapped lightmap: ships would then be lit as before).
2. **Laser** in both modes: the two-pass look without shaders, and lightning under a shader pack.
3. **Flywheel `indirect`/`instancing` on the default renderer** with the texture backport.
4. **The staff and launcher animations** in a real hand-held session (drag, cubes appearing, reload slide).
5. **Fog on far ships** under pitch or roll (cylindrical fog is approximate there).

## Measuring and test aids

- `-Dsable.debug.skipSubLevelBlockEntities` (unchanged from the first branch).
- The unpublished test rig has the scripts:
  - `bt.py` adds the test steps `single_block`, `mine_shaft`, `dark_ship`, `fog`, `rain` and `items`;
  - `sheet.py` makes contact sheets.
