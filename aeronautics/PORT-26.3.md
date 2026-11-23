# Aeronautics on Minecraft 26.3: port notes

Private port for one server (personal use). Stubs carry `PORT-26.3 STUB` in the source; other non-mechanical
changes carry `PORT-26.3:`. The 1.21.11 originals are in this tree's history on `fabric-1.21.11`.

State on 2026-11-22: compiles; loads with Create Fly 26.3-6.0.9-2, Sable, Simulated and Offroad on a dedicated
server and a client (with and without Sodium 0.9.2), strict Mixin audit clean. Flown in game: a survival player
assembles a hot-air ship (Aeronautics' own Ponder rig plus a coal-fired propeller and a swivel-bearing rudder),
lifts it with the burner's lever, cruises, steers with the steering wheel, and lands; the ship survives a relog
and a server stop/start and lifts again; two clients see it (screenshots kept with the unpublished test rig).

## Gaps, most visible first

1. **The burner flame is not drawn** (`HotAirBurnerRenderer.renderFlameOrEnqueue` returns early): the flame's
   palette-textured core shader is written for the 1.21 uniform layout and packs its parameters into vertex colours,
   so a vanilla stand-in drew a large flat green wedge. Smoke particles and sound still show the burner is lit. The deferred Iris
   flame pass is gone as well (`IrisBurnerFlameRenderQueue` is a stub; `fabric/mixin/burner_flame/GameRendererMixin`
   removed: it used frame-graph render-target overrides 26.3 removed).
2. **Levitite blocks follow their textures for the render layer** (26.3 picks layers from texture transparency;
   the per-block translucent registration is gone). The levitite blend fluid is forced translucent through its
   materials.
3. The Veil balloon overlay renderer (`ClientBalloonEffectRenderer`) was removed; it was already a no-op that
   nothing called.

## Behaviour changes

- Envelope bounce is `bounceRestitution(0.65)` (encased shaft `0.5`) on the block: 26.3 bounces from block and
  entity properties instead of a per-block fall hook. Items used to bounce at 0.8 of that.
- Levitite blend's upward current is applied through a second `EntityFluidInteraction` for the levitite tag, where
  vanilla applies water and lava currents (`mixin/levitite/EntityMixin`).
- Fog colour inside levitite blend is written into 26.3's destination vector (`fabric/mixin/levitite/FogRendererMixin`).
- Create value boxes on ships hook Create Fly's `ValueBox.submit` (was `render`).
- Flammability goes through Fabric's `FlammableBlockRegistry` (`FireBlock.setFlammable` is private).
- Saved data ids under `aeronautics:` (`aeronautics:aeronautics_levitite_data`, `aeronautics:aeronautics_unloaded_balloons`).

## Worth knowing when testing

- **Hot-air lift falls with air pressure, so altitude matters.** The same balloon that lifts a 43-mass ship from
  y 69 (it settles near y 115 to 137) cannot lift it from a platform at y 181 (pressure 0.61). That is the design
  (Ponder: "Lift weakens as Altitude increases"), not a port fault.
- `-Daeronautics.debugLift=true` on the server logs each burner's balloon state and the balloon force every two
  seconds (off by default).

## Data

The committed advancements and loot tables were converted to 26.3 formats with `scripts/convert_data_263.py`
(see `simulated/PORT-26.3.md`). The server loads them without parse errors.
