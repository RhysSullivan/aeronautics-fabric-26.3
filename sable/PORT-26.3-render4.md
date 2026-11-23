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
