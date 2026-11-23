# Offroad on Minecraft 26.3: port notes

State on 2026-11-22: compiles and loads with Create Fly, Sable, Simulated and Aeronautics (server and client,
strict Mixin audit clean). Driven in game (test client, code-only jars):
- wheel mounts: a survival player fits tyres by right-clicking each mount's face (4 of 4); a 5x5 deck with four
  motor-driven mounts, assembled into a sub-level, drives on the floor (about 16 blocks in 18 s, turning: the test
  rig's motor directions, not the port). Brakes (mounts powered from above) not proved: 3-7 s after powering, the car
  still moved at 4 blocks/s (from about 7);
- borehead bearing drill, laid out as Offroad's Ponder rig (bearing driven from its side shafts, barrel in front,
  a rock cutting wheel either side): assembles when driven and cut 21 stone blocks in 40 s at 128 rpm.

Changes beyond mechanical renames:
- The rock cutting wheel's contraption render state follows Create Fly 26.3: it extracts a `SuperByteBuffer`
  render state and submits it, as Create Fly's harvester does.
- Multi-mining break progress moved with vanilla from `LevelRenderer` to `ClientLevel`; the mixin now targets
  `ClientLevel` and uses the level's game time. The old `ClientLevel` accessor for the level renderer was removed.
- No stubs.

## Fixed in the port (2026-11-22, `rhys/26.3-loose-ends`)

- **A stalled drill now recovers.** A borehead drill with a full storage stalls, as designed. The remedy Offroad's
  Ponder shows (a funnel on the back of the bearing pulling items out) did nothing on Fabric:
  - the drill storage was exposed to Fabric's transfer API as a container over Simulated's
    `FabricInventoryLoaderWrapper`, whose `getItem` hands out copies and whose `setItem` does nothing, so nothing
    could be taken out;
  - and nothing told the drill when items left.

  Now Simulated exposes such wrappers as their real underlying storage, and committed extractions reach the wrapper's
  modification callback. The bearing sets that callback to start unstalling, as Offroad's NeoForge storage mixin does.

  Proved on the rig (screenshots kept with the unpublished test rig): full storage, 1 block cut in 20 s (stalled); a funnel behind
  the bearing hands items out and the drill cuts again (8 blocks in the next 30 s, at the andesite funnel's pace).

  The fault was inherited from the 1.21.11 Fabric port; upstream's NeoForge build uses item-handler capabilities.
  It applies to every Simulated or Offroad block that registers a wrapper this way.

## Behaviour players may ask about (as upstream designed it, unchanged)

- **Brakes are friction, not a lock.** A mount powered from above loses its drive and gets about five times the
  rolling friction; the friction is proportional to speed.
  - On flat ground a car stops within a few seconds, motors running or not (4.85 blocks/s to 0 in about 4 s).
  - On a slope it settles at a slow creep instead of holding: on an 11 degree ramp a braked car crept 0.28
    blocks/s, an unbraked one rolled about 11 blocks.
  - For a car this light, raising the suspension strength does not increase it: the brake friction is already capped
    by the car's own load at the default.
- **Steering is relative to each mount.** A mount turns its wheel toward its own left or right, seen from the mount
  looking out at its wheel. So a car's two front mounts (one facing west, one east) need their redstone on mirrored
  sides, for example the west mount's north side and the east mount's south side. A signal from the same world side
  on both turns the wheels against each other: the car slows and goes straight. Wired mirrored, a test car turned
  102 degrees in 5 s.
  - This matches the official 1.21.1 classes instruction for instruction.

Test-only diagnostics (off unless set on the server): `offroad.debug.wheelLog=true` (each wheel's steering, brake,
friction and velocity once a second) and `offroad.debug.drillLog=true` (stalls, extractions, the storage as funnels
see it).
