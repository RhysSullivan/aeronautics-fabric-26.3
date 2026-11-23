# PORT-26.3 data: NeoForge-only data features on Fabric

The published data is upstream's last commit on the port's history (`69001da`). In the four mods' `data/` (main and
generated), NeoForge-only data features that Fabric silently ignores:

| Feature | Where | What Fabric did with it before this fix |
|---|---|---|
| `"remove"` in a tag file | `sable/tags/block/super_heavy.json` | Ignored. `#c:storage_blocks` stayed whole, so the slime block, copper block and bone block were super heavy (mass 4 instead of 1). Create's cardboard block was in the tag too, but its own priority-1001 rule sets it to 0.5 afterwards on both loaders. |
| `"remove"` in a tag file | `sable/tags/block/light.json` | Ignored. Wooden slabs and stairs, and the stone-type slabs of `#sable:normal_slabs`, stayed in `#sable:light`. No final mass changed: `#sable:normal` and the priority-1001 `#sable:super_light` set those blocks afterwards. Tag membership was wrong, though, and Simulated's block tooltip reads the tag. |
| `"remove"` in a tag file | `aeronautics/tags/item/converts_to_cloud_skipper.json` | Ignored. The Cloud Skipper disc was in its own conversion tag: dropped at cloud height it took the converter and levitation components. The published mod leaves it alone. |
| `neoforge:` / `forge:` load conditions, data maps | none in the four mods | — |
| Optional entries `{"id": …, "required": false}` | many tags | Vanilla feature, same on both loaders. |
| `c:` convention tags | 54 references | All present on Fabric 26.3 with Create (block, item and fluid tags; `#c:honey` and `#c:water` are fluid tags, `#c:ladders` an optional entry). `#c:storage_blocks` is built from sub-tags on Fabric; the four removed blocks are each alone in their own sub-tag. |

**Fix.** Each `remove` is replaced by explicit entries (each file carries a `_comment` saying what the published file
means):
- `super_heavy` names the `#c:storage_blocks/*` sub-tags other than slime, copper, bone meal and cardboard, plus
  `create:experience_block`, which Create puts in the parent tag directly.
- `light` lists the slabs and stairs that remain once wooden slabs, wooden stairs and `#sable:normal_slabs` are taken
  out (generated from 26.3 with Create). The Quark wooden-vertical-slab removal can't be expressed; Quark isn't on
  Fabric 26.3.
- The conversion tag lists the discs of `#c:music_discs` other than Cloud Skipper.

Mods' blocks are optional entries. A mod added later that puts a storage block directly in `#c:storage_blocks`, or a
new slab or stair, isn't picked up by these lists; the check below finds that.

**Proof, block by block.**
- A test-only dump (`-Dsable.debug.dumpBlockProperties=FILE`, `BlockPropertiesDump`) writes, on a 26.3 server with the
  port and Create:
  - every block tag and item tag with its members;
  - the physics definitions in the order applied;
  - every block state's stored mass, friction, restitution, fragile, volume, floating material, floating scale and
    inertia.
- `create-port/be/props_check.py` works out the published values independently:
  - it reads the published tag files from git (`69001da`) and applies NeoForge's rule: all files of a tag combined,
    then every `remove` taken out of the finished tag;
  - other tags come from the game;
  - it then replays Sable's definitions in the game's order (priority, then load order), base properties then
    matching state overrides, from the property defaults, onto every state.
  - It compares every property of every state (75,062 states of 2,070 blocks) and every block and item tag of the four
    mods. It also fails if a `remove`, a `neoforge:`/`forge:` condition or a data map appears in the four mods' data.
- Results:
  - Before the fix: exits 1 (5 mismatched properties on 3 blocks; 3 tags differ).
  - After: exits 0 (no mismatches, no tag differences).

Selected blocks, values as `before / after / published` (NeoForge's reading of the published data):

| Block (state) | mass: before / after / published | friction: before / after / published | restitution: before / after / published | fragile: before / after / published | volume: before / after / published | floating_material: before / after / published |
|---|---|---|---|---|---|---|
| `minecraft:slime_block` (-) | 4.0 / 1.0 / 1.0 **fixed** | 1.0 / 1.0 / 1.0 | 0.5 / 0.5 / 0.5 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:copper_block` (-) | 4.0 / 1.0 / 1.0 **fixed** | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:bone_block` (axis=y) | 4.0 / 1.0 / 1.0 **fixed** | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `create:cardboard_block` (axis=x) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `create:experience_block` (-) | 4.0 / 4.0 / 4.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:iron_block` (-) | 4.0 / 4.0 / 4.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:honey_block` (-) | 1.0 / 1.0 / 1.0 | 1.65 / 1.65 / 1.65 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:oak_slab` (type=bottom,waterlogged=false) | 0.25 / 0.25 / 0.25 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.5 / 0.5 / 0.5 | None / None / None |
| `minecraft:oak_slab` (type=double,waterlogged=false) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:oak_stairs` (facing=north,half=top,shape=straight,waterlogged=true) | 0.25 / 0.25 / 0.25 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.5 / 0.5 / 0.5 | None / None / None |
| `minecraft:stone_slab` (type=bottom,waterlogged=false) | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.5 / 0.5 / 0.5 | None / None / None |
| `minecraft:stone_brick_slab` (type=bottom,waterlogged=false) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.5 / 0.5 / 0.5 | None / None / None |
| `minecraft:stone_brick_slab` (type=double,waterlogged=false) | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:stone_brick_stairs` (facing=north,half=top,shape=straight,waterlogged=true) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.5 / 0.5 / 0.5 | None / None / None |
| `minecraft:stone` (-) | 2.0 / 2.0 / 2.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:oak_planks` (-) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:oak_log` (axis=y) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:glass` (-) | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:ice` (-) | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | 0.0 / 0.0 / 0.0 | True / True / True | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:blue_ice` (-) | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:end_stone` (-) | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | sable:end_stone / sable:end_stone / sable:end_stone |
| `minecraft:anvil` (facing=north) | 4.0 / 4.0 / 4.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:bedrock` (-) | 1000.0 / 1000.0 / 1000.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:mud` (-) | 1.0 / 1.0 / 1.0 | 0.25 / 0.25 / 0.25 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `aeronautics:levitite` (-) | 1.0 / 1.0 / 1.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | aeronautics:levitite / aeronautics:levitite / aeronautics:levitite |
| `aeronautics:white_envelope` (-) | 0.25 / 0.25 / 0.25 | 1.0 / 1.0 / 1.0 | 0.5 / 0.5 / 0.5 | False / False / False | 1.0 / 1.0 / 1.0 | aeronautics:simple_drag / aeronautics:simple_drag / aeronautics:simple_drag |
| `create:flywheel` (axis=x) | 4.0 / 4.0 / 4.0 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:sticky_piston` (extended=true,facing=north) | 0.5 / 0.5 / 0.5 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 1.0 / 1.0 / 1.0 | None / None / None |
| `minecraft:glass_pane` (east=true,north=true,south=true,waterlogged=true,west=true) | 0.25 / 0.25 / 0.25 | 1.0 / 1.0 / 1.0 | 0.0 / 0.0 / 0.0 | False / False / False | 0.25 / 0.25 / 0.25 | None / None / None |

**Test.** `create-port/be/props-test.sh` starts the server with the dump on and runs `props_check.py`. It fails on any
property or tag that differs from the published meaning, and on any NeoForge-only feature added to the four mods' data.

## What the masses moved (weight-dependent proofs, software GL on boat, at y 66 like the GPU rig)

Only the slime, copper and bone blocks changed mass (4 to 1). No other block's final properties changed, so ships
without those three blocks weigh and fly exactly as before.

| Flight | Before (`9e73693`) | After |
|---|---|---|
| Ponder airship, plain (mass 45.75), burner at 15 | climbs 32.2 blocks and hovers | the same (no changed blocks aboard) |
| With 3 glass blocks on deck (mass 48) | climbs 21.2 | 21.2 |
| With ice, honey and slime on deck | mass 51, climbs 7.2 | mass 48, climbs 21.2, exactly as the glass ship |

The GPU baseline's "barely rises" ship was the slime block's fourfold mass. It is not honey friction: the honey block
sits on the deck and never touches the ground, and friction only acts on contact (published: honey 1.65 friction, slime
0.5 restitution, both mass 1). A ship resting with honey against the floor would still lift off in the published mod.
Nothing in the data or the sources glues it there; honey and slime only pull neighbouring blocks into the ship at
assembly time.

## Floating (software GL on boat, open water, sea level 62)

The published data and Sable's physics library (`buoyancy.rs`, the same on both loaders):
- each block in a water cell gets an upward force of 10.5 × the immersed volume × its `volume` property;
- gravity is 11.
- So a one-block deck settles where 11 × mass = 10.5 × volume × depth.

| Case | Published data gives | Port measures |
|---|---|---|
| Oak deck 5x1x5 (mass 0.5, volume 1) | 0.524 blocks deep | 0.521 |
| Wool deck (mass 0.25) | 0.262 | 0.264 |
| Oak deck with a waterlogged oak slab on its edge | as oak | 0.527, slab stays waterlogged and aboard |
| Glass deck (mass 1) | sinks slowly (needs 1.05 blocks of a 1-block deck) | sinks to the floor in about 25 s |
| Stone deck (mass 2) | sinks | sinks |
| Ponder airship, burner off | Its hull layers displace only 35.75 of the 47.9 volume units it needs, so the envelope goes partly under and the deck ends about 3 blocks deep (my estimate from the published data: water line about 0.9 blocks higher on the envelope than measured; it leaves out the envelope's drag material and some non-solid blocks) | Floats, steady, deck block at y 59.2 (top face 60.2) under a water surface at 63 |
| The same, burner lit at 15 while afloat | Balloon lift in the published sources is gravity × the balloon's gas lift × air pressure, and pressure depends only on height (`ServerBalloon.applyForces`, `DimensionPhysicsData.getAirPressure`): nothing checks for water, so a submerged burner and envelope still lift | Lifts out: y 60.8 to 100.8 in 60 s |

These match your published 1.21.1 black-box numbers: oak about half a block deep, wool about a quarter, and glass not
repeatable, since it is almost exactly neutral.

At the water line:
- The ship's bounds stay unchanged and no world water enters the ship's own block space.
- World water beside the hull still flows; a removed block next to it refills.
- The ship's own fluids staying aboard is covered by `hull_fluids`.

Tests: `bt.py floating` asserts the deck depths, both sinking cases, the airship floating, its bounds and water staying
put, and the lift-off with the burner on. `bt.py lift_compare` (`LIFT_VARIANTS`, `LIFT_Y`) gives the climb table.
`-Daeronautics.debugLift=true` logs the balloon's lift, filled volume, pressure and gravity every 40 ticks.
