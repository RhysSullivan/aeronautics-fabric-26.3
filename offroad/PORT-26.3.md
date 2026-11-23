# Offroad on Minecraft 26.3: port notes

State on 2026-11-22: compiles and loads with Create Fly, Sable, Simulated and Aeronautics (server and client,
strict Mixin audit clean). Driven in game (boat client, code-only jars, `create-port/shots/offroad/`):
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
