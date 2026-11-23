# Simulated on Minecraft 26.3: port notes

Private port for one server (personal use). This file lists what was changed in kind, what is stubbed, and what is
not drawn yet. Every stub in the source carries the marker `PORT-26.3 STUB`; every other non-mechanical change
carries `PORT-26.3:` with a one-line reason. The 1.21.11 originals are in this tree's history on `fabric-1.21.11`.

State on 2026-11-22: compiles; loads on a dedicated server and on a client (Create Fly 26.3-6.0.9-2, Sable port,
Fabric API 0.166.2+26.3), with and without Sodium 0.9.2, software rendering (llvmpipe). Checked: a strict Mixin
audit on the client (every mixin target class force-loaded, no failure); the 15 key blocks place and render; items
reach the inventory and render in hand; a recipe can be given, an advancement granted, a loot table drops its
block; the creative inventory opens; the block outline shows; Sable sub-levels still fall, land and carry a player.
Interactive checks: see below. Not yet run: the diagram screen.

## Gaps, most visible first

1. Create and Simulated block entities on moving ships are drawn since the block-entity branch was merged
   (`sable/PORT-26.3-blockentities.md`); `compat/create/SableCreateBlockEntityRenderer` and the reflective
   registrations in `SimulatedClient` are now unused (left in place, harmless; remove at the next tidy-up).
2. ~~Lasers draw as additive lightning~~: lasers back on 26.3 pipelines as two passes, drawn as lightning with a
   shader pack, deferred queues removed.
3. ~~Physics staff and plunger launcher items show their base model only~~: staff and plunger launcher items rebuilt
   on 26.3's item model API.
4. **Diagram screen: no ship preview.** Proposal in `sable/PORT-26.3-render2.md`.
5. **Render types on vanilla stand-ins** (`index/SimRenderTypes`): lock icon is depth-tested text (was
   see-through); ghost and translucent block previews use vanilla translucent moving block.
6. **Ponder cosmetics:** base-plate shadow fade and offset (`mixin/ponder/PonderUIMixin`) and the yaw/roll order fix
   (`mixin/ponder/WorldSectionElementImplMixin`) were removed: Create Fly's 26.3 scene renderer is restructured.
7. **Shader-pack ghost block layer** (`mixin/shader_compat/GhostBlockRendererMixin`) removed: its target method is
   gone in Create Fly 26.3.
8. **End Sea level hook** (`mixin/end_sea/LevelRendererMixin`) removed: it called `EndSeaRenderer.render`, which
   was already a no-op on Fabric.

## Fixed after the first load

- Create Fly's multi-part buffer layers were recycled twice when drawn through the immediate `renderInto` path
  (the parent recycled parts that recycle themselves), so one part could serve two tasks: background
  `layer.future is null` errors every frame and, once, corrupted vertex arrays (a crash with a docking connector in
  view). `mixin/create_render/EntityBlockMultipleLayerMixin` stops the second recycle; 0 errors since.
- `renderInto` also multiplied the caller's pose by itself (fixed on the block-entity branch,
  `mixin/create_render/EntityBlockLayerMixin`): wheel and lever handle were invisible whenever Flywheel was off.

## Interactive checks (2026-11-22, boat client, Sodium on)

Passed through real input events (mouse buttons and keys sent through `MouseHandler.onButton` and
`KeyboardHandler.keyPress`, mouse motion through the X server): physics assembler (hold and drag, assembles the
glued ship; 121 to 134 blocks), steering wheel (grab and turn; the ship turned 24 degrees through a swivel-bearing
rudder), throttle lever (hold and drag to 15; its lamp lights), physics staff (grabs and drags a ship 5 blocks),
coal into the portable engine (drives a propeller), Tab through the keyboard hook during a staff drag (reached
the game; rotate-mode effect not measured), the Simulated creative tab (opens, banner drawn).
The creative tab "not opening" in an earlier note was the test driver clicking with GLFW's left button (0); 26.3
uses SDL's (1). No player-facing bug.

## Behaviour changes (not stubs)

- The Flywheel texture backport (`create_render/FlywheelTexturePreloadMixin`, Create Fly `a25c2f0`) turns Flywheel on
  for players without Sodium.

- The render bridge (`compat/create/RenderBridge`) runs each buffer-style renderer once per submission and replays
  the recorded vertices per render type. Running it once per render type crashed on Create Fly's recycled buffers.

- Kinetic block-entity buffers draw with the cutout moving-block type (26.3 picks layers per quad from texture
  transparency; there is no per-state lookup).
- Sail bounce is `bounceRestitution(0.26)` on the block; items used to bounce at 0.8 of that and now bounce the same
  as players. Thrown springs bounce fully through `Entity.getEntityBounciness`.
- End Sea worlds: the dragon fight is per-level saved data in 26.3, so a fresh End Sea world creates it already won
  (`mixin/world_presets/ServerLevelEndSeaDragonMixin`). Saved fights load unchanged.
- Fuel: burn time is the item's `COOKING_FUEL` component resolved on the running server; a client without a server
  treats any fuel as 1,600 ticks (is-fuel checks and Ponder only).
- Item tooltip palette is chosen on first use (item components bind with the world in 26.3).
- Saved data ids moved under `simulated:` (`simulated:simulated_physics_staff_lock_data`,
  `simulated:simulated_lodestone_tracker`). No existing 26.3 worlds have the old ids.
- Block codecs no longer exist in 26.3; the `CODEC` fields and `codec()` overrides were removed.
- Rope hover hides the block outline through Fabric's `BEFORE_BLOCK_OUTLINE` instead of a `LevelRenderer` mixin.
- Throttle-lever picking hooks `Minecraft.pick` (moved from `GameRenderer`).

## Data

Registrate's data generators compile but refuse to run (26.3 generates data through bootstrap contexts). The
committed generated data is the source of truth. Advancements and loot tables were converted once to the 26.3
formats with `scripts/convert_data_263.py` (loot condition `type` keys, `match_block`, single `condition` objects,
`recipes` in `recipe_unlocked`). Recipes needed no change. The server loads all of it without parse errors.

## Not ported, by design

- Veil: only the 10 API classes already vendored are used. Anything that needed Veil's renderer is listed above.
- `ItemBuilder.burnTime` and `compostable` throw: nothing calls them, and 26.3 makes these data components.

## Assets come from the official release jar

The Simulated, Aeronautics and Offroad jars carry no `assets/` (the build excludes them: upstream's art, models,
sounds, lang and Ponder scenes are All Rights Reserved). `port/assets` supplies them at runtime:
- `OfficialJar`: the official Create Aeronautics 1.3.0 bundle (`<game dir>/aeronautics-assets/`, fetched from Modrinth
  and SHA-512-checked if missing; `-Daeronautics.assets.noDownload`, `-Daeronautics.assets.dir`); its three nested mod
  jars are unpacked unchanged beside it and read in place. Its code never loads.
- `OfficialAssetsPack` (client, added by `mixin/port_assets/PackRepositoryMixin`): a required built-in resource pack
  at the bottom of the stack serving those files, with `AssetOverlay`'s 26.3 forms over 145 of them (item model
  definitions, particle textures, three blockstate/model fixes, goggles equipment, typewriter powered model, atlases,
  three lang keys, laser core shaders built from the official Veil shader bodies by `LaserShaders`). Ponder structures
  are data-fixed on read, plus three mod block-state renames.
- `ServerLanguage` (dedicated server): the mods' English strings from the same jar.
New asset files the port needs must be derived the same way (a rule in `AssetOverlay`), never added under
`src/*/resources/assets/`: the build drops them. Proofs: `create-port/scripts/handout/check_jars.py`,
`check_assets.py`, `check_ponder.py`.
