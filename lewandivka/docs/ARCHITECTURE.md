# Architecture

## 1. Layers

```
core/        pure Java 17 (no Minecraft), own Gradle build, JUnit 5      <- compiled into the mod AND tested alone
  campaign   CampaignModel, WorldProgress, PlayerProgress, QuestStep, abilities, reputation, data store (save format)
  story      CampaignDirector (event -> step table), Events
  flow       Flow / FlowEnv + dungeon flows, mechanisms (levers, sync presses, cat hints, checkpoints)
  boss       BossRules + five boss rule sets (phases, timelines, party scaling)
  puzzle     TramNetwork, PaintPuzzle, FleePlanner, ZonePlanner, PlatformDecay, CatRoute
  scale      PartyScale (1/2/3 players), SyncGroup, Population
  quest      QuestItems, QuestItemLedger, StashLoot
  registry   BlockSpec / ItemSpec / EntitySpec / SoundSpec catalogs (data drive the registration and the tests)
  structure  Blueprint, BlueprintBuilder (turtle-style DSL), markers, structure checks
  world      Pal (palette), Noise, WorldPlan, DistrictPlan, ChromaPlan, generators of every structure
  text       QuestTexts, DialogueBook, LoreNotes (uk + en)
src/main     Minecraft glue (Yarn names), common: registries, blocks, items, entities, services, networking, commands
src/client   client only (split source set): renderers, HUD, screens, sky, key bindings, autotest
tools/       Python generators (textures, models, animations, sounds, lang, data) + validators + CI scripts + modpack builder
```

The core is added to the mod's main source set (`srcDir 'core/src/main/java'`), so it ends up in `lewandivka.jar`, and it is
also a stand-alone Gradle project (`./gradlew -p core test`). Rule of thumb: **anything that can be decided without a
world is decided in `core`** and covered by tests; the glue only turns decisions into blocks, entities and packets.

## 2. Data-driven catalogs

`ModBlocks`, `ModItems`, `ModEntities`, `ModSounds` describe every object once. Three consumers read them:

1. the glue registers real objects from them (`GameBlocks`, `GameItems`, `GameEntities`, `GameSounds`),
2. `tools/generate_resources.py` reads the JSON export (`gradle -p core exportSpec` → `tools/data/spec`) and writes
   blockstates, models, textures, animations, sounds, loot tables, recipes, advancements and language files,
3. the tests and `tools/validate_resources.py` check that nothing is missing or misspelled (every lang key and every
   catalog id used in code, every texture, every sound file, all vanilla ids against the 1.20.1 registry).

Block properties of the enum kind are stored as integer indices in the blockstates, so the Java side and the generator
cannot drift apart.

## 3. The campaign

* `CampaignModel` = one `WorldProgress` (step, counter, flags, defeated bosses, encounter records, ring fragments,
  portal) + a `PlayerProgress` per player (abilities, reputation points, notes, tutorials). It serialises through a small
  `DataStore` abstraction; `NbtStore` (glue) writes it into the overworld's persistent state (data version in the file).
* Systems never change the step themselves. They report an **event id** (`Events`) through `Story.event(server, id)`;
  `CampaignDirector` holds the table *(event, from-step) → (to-step, effects)*, counts multi-part objectives, remembers
  early events and replays them when the story arrives. The table is also the source of `docs/QUEST_FLOW.md`.
* Effects (items, abilities, dialogue, NPC spawns, portal, cinematics) go through the `Effects` interface; the glue
  implements it (`Story.Effects`).
* `Lifecycle` handles joining (first arrival in the district or at the base), respawn points, step announcements,
  advancements. `QuestWatch` runs the quest-item ledger and the package slowness.

## 4. Dungeons: flows

A dungeon is a `Flow` (core): `use(marker, player)`, `useWith(marker, player, item)`, `tick()`, `rebuild()`, `reset()`,
`bossDefeated(id)`, `complete()`. A flow only knows **marker names** (`breaker_1`, `gate_main`) and talks to the world through
`FlowEnv` (set a station property, replace a block, open a gate, move a lift, flood a region, spawn entities, drive a
tram, play a sound, say a message, report an event, save a checkpoint ...). Permanent progress lives in the encounter record
(string flags), so a flow can be rebuilt after a restart (`rebuild`) and reset to the last valid checkpoint (`reset`).

* tests use `FakeEnv`, a recording, **strict** double: unknown sound/entity/item/block ids and bad block states throw;
* the game uses `GameEnv`, which resolves marker names to block positions through `Structures` (the marker index built from
  the blueprints of both dimensions) and calls `BlockOps`, `Gates`, `Lifts`, `Scheduler`, `Net` ...;
* `FlowHost` creates a flow on first use, ticks it while players are inside (with a per-structure margin), resets it when
  everybody was gone for 20 s and routes station clicks (`Stations` → marker at the clicked block → flow).

Reusable mechanisms: `OrderedSequence` (breakers), `SyncPresses` (switch boxes, levers, validators: window scaled by the
party), `LeverPattern`, `DeadlineSwitch`, `CatHints` (the cats open hidden routes instead of a waypoint beam),
`CheckpointSensors`.

## 5. Bosses

Rules are pure (`BossRules` subclasses: phases at 70/35/5 %, timelines, party scaling, event output `BossEvent`);
`BossEntity` (glue) is the shell: it feeds time and hits into the rules, shows the boss bar, applies events (telegraphs,
floods, shields, adds, camera shake, monochrome), resets the whole fight when the party is gone and survives bad input
(a boss summoned outside its arena resets instead of crashing). Stations that belong to a boss (arena levers, collar stands,
drains, composters, relays) are forwarded by the flows with `bossStation(entity, action, index, player)`.

The final fight's **Chromatic Charge** is server state (`ChargeRelay`, 12 s overload, `ColorlessHeadRules`); the client only
draws it and sends "pass" (the drop key while you hold it).

## 6. World generation

`PlanChunkGenerator` / `PlanBiomeSource` (codecs `lewandivka:plan`) delegate every chunk to `ChunkPainter`, which paints the
terrain column of the `WorldPlan`, scatters decoration and stamps the fixed `StructurePlacement`s that intersect the chunk.
Everything is a pure function of the plan, so two servers always generate the same world and structures never depend on the
seed. `Structures` indexes all markers (point markers for stations, regions for gates/zones), `StructureValidator` checks
the painted world against the blueprints (`/lewandivka validate`, run in CI over rcon), `StructureChecks` proves
reachability of the quest points inside the blueprints at build time (core tests).

### Calibrated physics

The dungeons are built for exact launch numbers: `core/.../world/Launch.java` holds the speed of the spring pads (apex about
+20 blocks, drift 1.4 along the arrow), of the hatches (apex about +31) and of the glider (0.42 blocks/tick, sink 0.09).
`LaunchTest` simulates vanilla movement and proves that every pad of the sky ascent lands on the next island, that the hatch
reaches the platform of the sky tram and that the glider crosses the chasm of the tower approach with energy to spare. The
server (`Stations.spring`, `Abilities`) and the client (`Keys.glide`) use the same constants, and the CI client test repeats
the jumps with a real player in survival mode (`AutoTest.physics`).

## 7. Client

`LewRenderer` (one GeckoLib renderer for all 20 entities, model/texture/animation from the catalog), `Hud` (objective,
abilities with cooldowns, charge bar, monochrome overlay, letterbox cinematics), `NotebookScreen`, `CreditsScreen`,
`ConfigScreen` (Mod Menu), `ChromaSky` (pink dome, three suns, the four-fragment rainbow ring), `Keys` (V / N / H, glider,
pass-charge). The server is authoritative: the client sends requests, the server validates grants, cooldowns and state.
`dev/AutoTest` drives a real client through the whole world for screenshots (CI only, needs `LEWANDIVKA_AUTOTEST`).

## 8. Resource pipeline (tools/)

Python + Pillow, deterministic except where noted: `blocks_paint` (16×16 pixel art with dithering and a shared palette),
`items_paint`, `entities_*` (UV-mapped skins and GeckoLib geometry for humanoids, cats, bosses, trams), `geo` (geometry and
animation writers), `sounds_*` (synthesised effects and short music loops, `.ogg`), `lang*` (uk + en), `data_emit` (loot
tables, recipes, advancements, tags, biomes, dimensions). `validate_resources.py` is the gate.

## 9. Packaging

`tools/modpack/build_modpack.py` resolves the pack from Modrinth for 1.20.1 / Fabric, reads the `fabric.mod.json` of every
candidate (including jar-in-jar modules) and steps back through older releases until all dependency ranges, the Minecraft
version and the loader version fit. Output: `.mrpack` (references only), server pack (server-side mods, Fabric launcher,
scripts), TLauncher installer (downloads and hash-checks the same files), `THIRD_PARTY.md`, `resolved.json`. Fabric API
and GeckoLib are never embedded in `lewandivka.jar`.

## 10. CI (`.github/workflows/lewandivka.yml`)

1. core unit tests → 2. `./gradlew build` → 3. Fabric GameTests (headless server; every registry entry, every entity is
created, ticked and reloaded from NBT, the campaign NBT round trip) → 4. dedicated-server smoke test over rcon
(`/lewandivka validate`, then a restart of the same world: the campaign step must survive) → 5. resource validation
(including blockstate properties against the block catalog) → 6. client test (Xvfb, software GL: story-step soak of every
dungeon, the night population, the physical jump checks, screenshots; any logged error of the mod fails it) → 7. modpack
build → 8. boot the finished server pack. Every run publishes its logs, screenshots and artifacts to the pre-release
`ci-latest`.
