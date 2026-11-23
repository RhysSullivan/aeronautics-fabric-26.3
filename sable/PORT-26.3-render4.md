# PORT-26.3 render4: diagram overlays, the flame under a shader pack, shadow limits, laser copies

Branch `rhys/26.3-render4`. It starts from `rhys/26.3-render3` (`53e3446`, the GPU build) and will be rebased onto
`rhys/26.3` once render3 is merged. Shots and client logs are in `create-port/shots/aero-be3/render4/`.

## Diagram force and rotation overlays (Simulated)

- The force arrows, the centre-of-mass marker and their tooltips were already ported (GUI fills and blits), and they
  are drawn after the preview. They now line up with the drawn ship (`kb_z0`: gravity from the centre of mass,
  levitation from the levitite, the tooltip "Levitation force of 118.24 pN").
- They had not shown earlier for two reasons: the test ship had no forces in the groups shown by default (levitation,
  balloon lift, propulsion, lift), and the centre-of-mass marker is off by default.
- The rotation gizmo and the force-group toggles are ordinary widgets and draw.
- Test switches (also usable by hand): `simulated.debug.diagramAllForces` (every force group, gravity included),
  `simulated.debug.diagramShowCom`, and `simulated.debug.diagramLog` (now also logs the received force data).
- Still a stub: `aabbInFramebuffer` (decorative greebles avoiding the ship). Small; it needs the ship's projected
  bounds.

## Burner flame under a shader pack (Aeronautics stub)

- `IrisBurnerFlameRenderQueue` is real again. With a pack on, the world frame's flames are queued (camera rotation
  baked in) and drawn with their own shader at the return of `LevelRenderer.render`, after Iris's final pass.
- On 26.3 there is no immediate draw, so the queue submits custom geometry into its own `SubmitNodeStorage`, then
  renders it with its own feature dispatcher in one pass on the main target's colour and depth.
- `fabric/mixin/burner_flame/LevelRendererBurnerFlameMixin` replaces the Fabric `START_MAIN`/`END_MAIN` events.
- Flames outside a world frame (Ponder, GUIs) still use the fire-sprite fallback.
- `aeronautics.debug.noDeferredFlame=true` keeps the fallback in the world too.
- Verified with the test pack on software GL: the procedural flames in both palettes (`ka_h1`), and depth-tested
  against the scene after the pack (`ka_o1`: a burner behind a stone column is hidden).
- Not verified: how a real pack's composite (bloom, tone mapping) sits with an unshaded flame drawn after it. This
  matches the 1.21 behaviour.

## Shadow-pass limits (Sable, under Iris)

- **Ship block entities now cast shadows.** `mixin/compatibility/iris/IrisShadowBlockEntitiesMixin` (`@Pseudo`,
  applied only with Iris) runs at the return of Iris's `ShadowRenderer.renderBlockEntities`. It extracts the ships'
  block entities for the shadow pass with the camera Iris passes, then submits them with the shadow model view.
  - The main pass's batches are not current at that point in the frame, so they are extracted again: a second
    extraction, only with a pack and shadows on.
  - `kg_c1` and `kh_c1` show it: three chest shadows with the hook, none with it off
    (`sable.iris.shipBlockEntityShadows=false`).
- **Shadow draws are trimmed to the shadow frustum.** Each section's 16-block box goes through the ship's transform
  into a world box and is tested against Iris's public `ShadowRenderer.FRUSTUM`; hidden sections are not drawn.
  - The log line reports `N of M sections in the shadow frustum`.
  - `sable.iris.cullShadowSections=false` turns it off.

## Laser shader copies

Deleted `simulated/src/main/resources/assets/simulated/shaders/core/laser.{vsh,fsh}`. `AssetOverlay`'s
`LaserShaders` builds the shipped form from the official release.

## If the GPU run shows Complementary or BSL treating the hull badly

What each symptom would most likely mean, and the quick change:

- **Hull too dark or too bright compared with nearby terrain.** The ship's lightmap may not match what the pack
  expects in `Sampler2`.
  - First check: compare the run with `sable.iris.shipLightmaps=false`.
  - If that fixes it, bind the game's lightmap under a pack and carry the sky scale in the vertices' sky-light
    coordinate at compile time instead.
- **Hull lit as if facing up everywhere, or with no directional shading.** Normals did not reach the pack.
  - Check the log's `solid pipeline format 52 bytes`; if it says 28, the meshes were built before the pack loaded.
  - Rebuild on the path change; that is in place, so check the order.
- **Waving leaves or emissive blocks wrong on ships** (`mc_Entity` = -1). Expected for now.
  - Fix: set Iris's block ID during the ship's section compile through Iris's `BlockSensitiveBufferBuilder`
    (`beginBlock`/`endBlock` around each block), reflectively.
- **Glass or other translucent ship blocks look wrong** (through the moving-block program).
  - Draw the translucent layer with `TRANSLUCENT_TERRAIN` and a `DynamicTransforms`-shaped slice bound under the name
    `TerrainUniform` (Iris binds both names to the same block). The pack's water program (`gbuffers_water`) is then
    used.
- **Hull shadow offset or detached from the hull.**
  - Check the log's camera offset (it should be 0.000).
  - Compare with `sable.iris.cullShadowSections=false`.
  - If the shadow is still offset, the shadow camera differs from the main one, so carry the hull's world position
    instead of the camera-relative matrix.
- **Hull flickers or vanishes at certain angles.**
  - Compare with `sable.iris.hullPhase=false`. A pack may key behaviour on the render phase.
- **Shadow missing on big ships.**
  - Compare with `sable.iris.cullShadowSections=false`. The world box test may be wrong under some transform.

## Hull sections missing under a shader pack

**Symptom.** With Iris and a shader pack, ship hull sections (or whole hulls) vanished, in the main pass and the
shadow pass alike, while every draw was submitted with correct state. Which section, which pack and which start was
effectively random; the first ship on a fresh client usually drew.

**Cause.** Under Sodium, Sable keeps every sub-level's section meshes in its own section dispatcher's shared arenas,
one vertex arena per layer (vanilla `SectionRenderDispatcher`, `UberGpuBuffer`).
- Each arena aligns allocations to `layer.pipeline(false).getVertexFormatBinding(0).getVertexSize()`, read once, when
  the dispatcher is built. Iris swaps that format to its 52-byte terrain vertex on the render thread during a level
  render with a pack on. So the arena is 52-aligned if the dispatcher happens to be built then, and 28-aligned (vanilla)
  otherwise. Both happen in practice (GPU pass 5 logged both).
- With a pack on, the meshes are 52-byte; each section's draw took `byte offset / 52` as its base vertex. In a
  28-aligned arena most offsets are not multiples of 52, so those sections were read from the wrong bytes.

Reproduced on software GL under BSL (three ships on one server, a fresh client each: ship 1 whole, ships 2 and 3 lost
sections) and confirmed on the RTX 2080 (GPU pass 5: sections at offsets such as 180236 and 262164, not multiples of
52, now draw).

**Fix.**
- Under a pack, each section is drawn with the vertex buffer bound at its own byte offset and base vertex 0
  (`IrisHullPath`), whatever the arena's alignment.
- The reverse case: an arena built 52-aligned with a pack on, then the pack turned off, so the meshes are vanilla's
  28 bytes. A sub-level with any section not aligned to the current vertex size is drawn the same way without a pack,
  through vanilla's block pipelines and shaders. Aligned sub-levels keep vanilla's own draw. Reproduced and proved on
  software GL: the pack was turned off by Iris's own toggle, the log said the sections were not aligned, and the ship
  drew whole.
- Not covered: such a sub-level's translucent blocks with vanilla's improved transparency (OIT) on and no pack.

**Ruled out on the way** (each by a GPU run that still lost sections with it in place):
- stale render-pass or GL state after Sodium's raw GL calls (a full cache reset before the hull draws changed nothing);
- the draw order;
- a depth-convention mismatch.

Two other fixes made then stay, because they are real:
- `75af429` binds the default uniforms and the terrain fog in every pass the hull draws into: Iris's translucent pass
  has none, which crashed with translucent ship blocks.
- Ship block entities are added to the shadow pass.

**Audit of the same assumption** (a byte offset in a shared buffer divided by a stride):
- `LevelRendererSubLevelTerrainMixin`: the base vertex for vanilla's draw (now only used when aligned) and the first
  index for custom (sorted translucent) index buffers. The index arena is built with a fixed alignment of 8, so
  `offset / index size` is always exact.
- `IrisHullPath`: no division left; the shadow pass uses the same per-section draw.
- No other place in Sable, Simulated, Aeronautics or Offroad divides a buffer offset by a stride. Block entities,
  Flywheel visuals and the diagram's ship mesh use their own buffers, never these arenas.

**Regression test** (rig, software GL, no GPU): `hull_seq_ship` in `create-port/be/bt.py`, run three times on one
server with a fresh client each, under a pack with `-Dsable.iris.hullLog=true`.
- It asserts that every section draw read a block vertex at the section's own byte offset (GL's actual vertex binding
  plus the base vertex used) and passed samples, and reports how many sections sat at offsets that are not multiples
  of 52.
- Passes on the fix (ships 2 and 3: 6 of 12 section draws misaligned, all good). Fails with the old base-vertex draw
  put back: ship 2 read 4 bytes early, passed 0 samples.
- `hull_pack_off` covers the reverse case.

**Diagnostic** (`-Dsable.iris.hullLog=true`, every 10 s with a pack in use): each section draw's byte offset, whether
it is a multiple of the stride, primitives and samples, and the first-vertex check; plus each layer's Iris program.

## Crash: translucent ship blocks, Sodium, no shader pack (old; fixed here)

**Symptom.** `NullPointerException: ... "bufferView" is null` in `GlCommandEncoder.setupDraw`, from vanilla's
`DrawSeparate` under `sable$renderSubLevelGroup` in `executeClassicTransparency`. It needs a sub-level with translucent
blocks, Sodium, and no pack. It reproduced on every build back to before render2.

**Cause.** Iris, installed with shaders off, still replaces the translucent pass with a fresh one in which nothing is
bound (`beginTranslucents`). Vanilla's terrain draw for the sub-level then had no `Projection`, `Globals`, `Lighting`
or `Fog`. Iris needs Sodium, so the default renderer never crashed. Under a pack the hull path already bound them
(`75af429`).

**Fix.**
- The no-pack sub-level group binds the default uniforms and the terrain fog before drawing.
- Fail safe: a sub-level whose group draw, or a hull section whose draw, still throws is skipped for that frame with
  one logged line, never the client.

**Test.** `hull_translucent` in `create-port/be/bt.py`, run as ship one and ship two of a session, with Iris installed
and shaders off, then under BSL.
- The ship carries stained, tinted and plain glass, ice, slime, honey and a water cauldron.
- For 3 minutes the camera orbits it (translucent re-sorts) and a block inside it is placed and removed every 30 s
  (section rebuilds).
- It asserts no crash and no skipped draw.
- Reproduced the crash at once before the fix; all four runs pass after it.
- The impulses barely lift this ship, so it turns more than it flies.


## Black ship with water on it, no shader pack (fixed)

- **Symptom:** the whole ship, hull and block entities alike, drawn near black from every view for the rest of the session
  once a water source block was on it (the GPU pass broke ice on the deck). Before the no-pack translucent fix, the same
  case crashed instead.
- **Cause:**
  - Water on the deck that reached a cell above open air fell out of the bottom of the ship.
    - Upstream's `FlowingFluidMixin` guards only sideways spreading (`canPassThrough`).
    - On 26.3, downward flow goes through `canMaybePassThrough`, which nothing guarded.
  - Every block of the falling column grew the plot's bounds, down to the bottom of the world: in plot space, y 127..134
    became -53..134.
  - The ship's world bounds then reached far below it. Its sky-light sample, taken at the bounds' centre (upstream logic),
    was underground, so the sky scale was 0. Terrain (the per-ship lightmap) and block entities (`scaleLightColor`) both
    lost their sky light.
  - Once the water is removed the bounds shrink back and the ship is bright again.
- **Fix:** `FlowingFluidMixin` also refuses any flow, downwards included, into a cell outside the plot's bounds. This
  matches upstream's stated intent ("fluids refuse to flow off the edge of sub-levels"): fluids stay on board and spread
  on the deck, and are drawn like the same blocks in the world.
- **Not triggers:** lava (slow, and it fell the same way given time before the fix), waterlogged blocks, seagrass,
  flowing water with no source, a water cauldron, glass, ice, honey and slime.
- **Test:** `bt.py hull_fluids` (create-port/be).
  - Runs water, lava, a waterlogged slab, seagrass, flowing water and a water cauldron (the control), each on the deck of
    the Ponder airship and on the same structure in the world.
  - Asserts that the plot's bounds are unchanged and the fluid is still there.
  - Compares close views of hull-only blocks, and the fluid's own close-up, against the world copy.
  - `hull_control` with `-Dsable.debug.hullSkyScale=0` is the positive control: the comparison must fail on a black hull
    (it reads 0.10 to 0.15).
- **Test-only switches:** `-Dsable.debug.skyLog=true` logs the ship's sky scale, its world bounds, and its plot light
  (bounds, sky over the air inside, the draw path). `-Dsable.debug.hullSkyScale=N` forces the hull's lightmap.

**Deck under the envelope: the test, not the port (resolved).**
- The 1.2 to 1.3 reading came from the test's framing. The stamped airship floats one block above the floor (its Ponder
  base plate is removed) and settles onto it when assembled, before the reference pose is taken. Every ship view was
  therefore framed one block higher above the deck than the world copy's.
- With the true settled offset (found by locating the ship's assembler in the world), the deck views read 0.97 to
  1.02, and the deck under cover 1.00.
- **Light data:** a test-only client dump (`-Dsable.debug.lightDump=FILE`, `LightProbeDump`) compares every cell of the
  ship's own block space with the world copy's.
  - All 596 air cells hold the same sky and block light: under the envelope, sky 12 to 14 on both.
  - So the envelope casts its sky-light shadow in the ship's space exactly as in the world.
  - Face shading is the vanilla table on both (up 1.0, north 0.8). Light comes per block from the ship's own block
    space, as in Sable's published design.
- **Under BSL:**
  - The hull's own shadow is in place. With the hull left out of the shadow pass the covered deck reads 81 to 92
    (sunlit); with it in, 56, the world copy's value.
  - BSL adds a time-varying full-screen haze to some frames, about +11 on that dark view, on the ship and the world copy
    alike. It persists with TAA or light shafts off and lasts under 10 s, so the test takes three shots 6 s apart and
    uses the darkest.
- **Test:** `hull_fluids` now asserts the deck-under-cover view (`deck_under_cover_spectator`) for every case, with and
  without a pack.
- **What isn't asserted, and why:**
  - With water, waterlogged blocks, seagrass or lava out, the open deck view shows the world copy's fluid running off
    onto the floor, which a ship's fluids never do (upstream's design).
  - After lava, the world copy's wooden deck may be burning.
