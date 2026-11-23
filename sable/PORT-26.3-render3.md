# PORT-26.3 render3: shader-pack hulls, burner flame, diagram preview, packaging

Branch `rhys/26.3-render3`, rebased on `rhys/26.3` at `8e62513`.

## Ship hulls under an Iris shader pack

**Cause (from the Iris 1.11.7 and Sodium 0.9.2 sources, checked against the shipped jars' bytecode).**
- Sable draws each sub-level's sections with vanilla's terrain pipelines (`SOLID_TERRAIN`, `CUTOUT_TERRAIN`,
  `TRANSLUCENT_TERRAIN`): one `TerrainUniform` block (`ModelViewMat`, `TextureSize`, 80 bytes) and one `ChunkSection`
  block per section (its origin).
- With a pack loaded, Iris swaps every pipeline it knows for a pack program by pipeline identity
  (`IrisPipelines`; the phase does not matter). The terrain pipelines map to `TERRAIN_SOLID`, `TERRAIN_CUTOUT` and
  `TERRAIN_TRANSLUCENT`. Under Sodium nothing else uses those pipelines, so the ship draws are their only users.
- Iris's programs declare a single transform block with the `DynamicTransforms` layout (`ModelViewMat`, `TextureMat`,
  `ColorModulator`, `ModelOffset`), and Iris binds Sable's `TerrainUniform` slice to it (`MixinGlProgram`). It has no
  `ChunkSection`. So `TextureMat`, `ColorModulator` and `ModelOffset` come from past the end of an 80-byte slice
  (typically zero): alpha 0, collapsed texture coordinates, every section at one offset. The hull vanishes;
  block entities, entities and particles use other pipelines and still draw.
- The `Missing program sable:pipeline/sublevel_lightmap_N` lines are not the cause. For a pipeline it does not know,
  Iris logs once and returns the original program unchanged (`MixinShaderManager_Overrides`), so those offscreen
  passes that bake each ship's sky-scaled lightmap still run. render2 did not change the hull's pipelines, so hulls
  most likely never drew under a pack; the before-render2 build (`73ad458`) answers that.

**Fix (`IrisHullPath`).**
- While a pack is in use, the same section buffers are drawn with `SOLID_BLOCK`, `CUTOUT_BLOCK` and
  `TRANSLUCENT_BLOCK`, with one `DynamicTransforms` slice per section whose `ModelOffset` is the section origin
  relative to the anchor. The vertex layout is the same as terrain's, and `block.vsh` computes
  `Position + ModelOffset` where `terrain.vsh` computes `Position + ChunkPosition - CameraBlockPos + CameraOffset`, so
  positions and fog distances are identical. Iris maps solid and cutout block pipelines to the same terrain
  programs; translucent maps to its moving-block program.
- Iris's render phase is set around the draws (`TERRAIN_SOLID`, `TERRAIN_CUTOUT`, `TERRAIN_TRANSLUCENT`), and the
  previous phase is restored.
- Shadows: registered through Iris's public `IrisApi.registerShadowRenderCallback`. Iris runs it in its shadow pass
  after opaque terrain, with the shadow model view, the shadow projection already set and the main camera's position.
  Sable draws the frame's hull draws again there with the shadow model view.
- Sky-light scale: the per-ship lightmap is bound as `Sampler2`; Iris binds `Sampler2` to the lightmap texture unit
  the pack samples, so the ship's sky scale reaches the pack's lighting.
- Vertex format: under Sodium, ship sections compile synchronously on the render thread inside the level render, so
  with a pack on Iris builds them in its wider terrain format (normals, tangents, mid-texture filled per quad), and
  the upload stride and draw binding agree. A switch of the pack on or off rebuilds ship meshes.
- With no pack, nothing changes: the existing terrain draw runs exactly as before.
- Iris is optional; all access is reflective.

**Verified on software GL (llvmpipe, boat VM)**, shots in `create-port/shots/aero-be3/iris/`:
- No pack (Sodium, the vanilla renderer, and Iris loaded with shaders off): the old terrain draw, unchanged
  (`ha_*`, and the `hd` run).
- The block path forced without a pack (`sable.debug.blockHullPath=true`): the same hull, including a rotated ship
  (`hb_*`).
- Iris 1.11.7 with a minimal test pack of my own (`sabletest.zip`: pass-through terrain programs tinted pink, a real
  shadow-map lookup and a shadow program; it does not crash llvmpipe as Complementary does):
  - the hull draws through the pack's terrain program (pink), lit, with normals (`he_*`);
  - the hull casts shadows in Iris's shadow pass: the deck's shadow on the ground and the mast's shadow on the deck
    with `hullShadows` on (`hv_s*`), none with it off (`hn_s*`).
  - The log shows the solid pipeline format as 52 bytes under the pack (Iris's terrain format), as expected.

**Switches** (launch-time JVM properties):

| Property | Effect |
|---|---|
| `sable.iris.hullPath=terrain` | the old draw under a pack, for comparison |
| `sable.iris.hullShadows=false` | hulls left out of the shadow pass |
| `sable.iris.hullPhase=false` | Iris's render phase left as it is |
| `sable.iris.shipLightmaps=false` | the game's lightmap instead of the ship's sky-scaled one |
| `sable.debug.blockHullPath=true` | the block path without any pack (rig test of its geometry on vanilla shaders) |

Log lines, once per path change: `sub-level terrain path …`, `sub-level block path drew N section draws …` and
`sub-level hulls drawn into Iris's shadow pass …`.

Known limits:
- Block entities on ships are not in the shadow pass.
- The hull's `mc_Entity` block IDs are -1, because the vanilla compiler has no Iris block-ID hook, so pack effects
  keyed by block ID (waving leaves, emissive ores) do not apply on ships.
- Ship translucents use the moving-block program, not the water program.
- Shadow-pass draws are not culled to the shadow frustum.

## Packaging (assets)

Shader files of mine that were under `assets/` and ported upstream shaders now derive from the official release in
`AssetOverlay` (simulated `port/assets`), as `LaserShaders` does:

- `aeronautics/shaders/core/burner_flame.{vsh,fsh}`: `BurnerFlameShaders`, from the official Veil program
  `aeronautics/pinwheel/shaders/program/burner_flame.*` and `include/util.glsl`. Changes: the 26.3 frame, flame data
  from the vertex colour, 26.3 fog, and the circle array filled and indexed in range.
- `simulated/shaders/core/diagram_outline.fsh`: `DiagramShaders`, from the official
  `simulated/pinwheel/shaders/program/contraption_diagram/outline_diagram.fsh`. Changes: Veil uniforms to defines,
  the texel size computed in place, and depth read reversed.
- `Shaders` holds the checked text edits shared by both.

## Diagram screen's ship preview (Simulated)

The diagram screen now draws its 3D ship preview through 26.3's picture-in-picture GUI renderer
(`DiagramPreviewRenderer`):
- it draws a cached block mesh of the ship's plot (`DiagramShipMesh`, rebuilt every 3 s);
- it then runs the outline and palette pass, a derived official shader, as a screen-quad pass.

Shots are in `create-port/shots/aero-be3/diagram/`.

- Two bugs, found once the rig ran a fresh jar (earlier "blank" runs had a stale Simulated jar):
  - the view was recorded on `fbo` while the screen shows `finalFbo` (the 1.21 three-buffer chain is one pass now);
  - the GUI target's y points down.
- `simulated.debug.diagramFlipZ` swaps the depth sign. The default (`y0`) gave correct depth on the rig; which side of
  the ship faces the viewer should be checked against the 1.21 mod on a real game.
- Not done: the force and rotation gizmo overlays drawn over the preview.

## Gameplay bug found

Placing a contraption diagram on a block's top face crashes the client. 26.3's hanging-entity direction check rejects
vertical facings, in `DiagramEntity.updateFacingWithBoundingBox` → `setDirectionRaw`. The rig opens the screen
through its test driver instead.
