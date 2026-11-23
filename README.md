# Create Aeronautics stack for Fabric 26.3 (unofficial port)

An unofficial port of Sable, Simulated, Create Aeronautics and Offroad to Minecraft 26.3 on Fabric, made for one
private server. It is not affiliated with or endorsed by the original authors. Please do not report problems with
this port to them; use this repository's issues instead.

This is a source snapshot of the working branch, with the original mods' art removed. Each snapshot is one commit
here; built jars are on the [releases page](https://github.com/RhysSullivan/aeronautics-fabric-26.3/releases).

## Where it comes from

- [bobqianic/create-aeronautics-fabric](https://github.com/bobqianic/create-aeronautics-fabric) at `69001da`
  (a Fabric 1.21.11 port), which this tree started from.
- [Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project) by the Simulated Team and the
  Creators of Aeronautics: Simulated, Create Aeronautics and Offroad.
- [Sable](https://github.com/ryanhcode/sable) by RyanHCode: the physics engine, in `sable/`.
- [Porting Lib](https://github.com/Fabricators-of-Create/Porting-Lib), vendored in `portinglib/`.

What changed for 26.3 is described in the `PORT-26.3*.md` notes in each module.

## State

- Works in survival on a dedicated server: assembling, flying, landing, relogging and restarting an airship;
  Create machinery and block entities draw on moving ships; Offroad wheels, steering and drills.
- Shader packs: ships draw whole under Complementary and BSL (Iris with Sodium), and with no pack, including ships
  carrying glass, ice and other translucent blocks and ships with water or lava aboard. Under a pack, a fluid on a
  ship is drawn with the pack's program for moving blocks, not its water program.
- Block masses, friction and the other physics properties match the published mods' data block by block.
- Windows and macOS have not been tried on real machines.
- A tree with the art removed, as here, builds the four code-only jars. Dependency versions are in
  `gradle.properties` (Create Fly 6.0.9-2 for 26.3 among them).
- Recipes: the mods' recipes, advancements and loot tables all load; mixing, deploying and crafting recipes were
  made in game, the levitite blend among them.
- The `PORT-26.3*.md` notes describe tests run on a separate test rig that is not part of this repository.

## The art is not here

The original mods' art (`*/src/main/resources/assets/` in Simulated, Create Aeronautics and Offroad, and the files
generated beside it) is All Rights Reserved and is not in this repository. The port reads it at run time from the
official Create Aeronautics 1.3.0 release, which the game downloads from Modrinth on first start
(`simulated/src/main/java/dev/simulated_team/simulated/port/assets/`). The banner images of the original
repositories are left out too.

## Licences

Each part keeps its own licence. See [NOTICES.md](NOTICES.md).

| Path | Licence |
|---|---|
| `aeronautics/`, `simulated/`, `offroad/` (code) | MIT, see [LICENSE.md](LICENSE.md) |
| `sable/` | PolyForm Shield 1.0.0, see [sable/LICENSE.md](sable/LICENSE.md) (https://polyformproject.org/licenses/shield/1.0.0) |
| `sable/src/main/resources/natives/sable_rapier/` | Rapier, Apache-2.0, see `LICENSE-RAPIER` there |
| `portinglib/` | see `portinglib/LICENSE` and `portinglib/NOTICE.md` |
| `simulated/src/main/java/com/tterrag/registrate/` | MPL-2.0, see [licenses/MPL-2.0.txt](licenses/MPL-2.0.txt) |

The changes made for this port are offered under the same licence as the files they are in.
