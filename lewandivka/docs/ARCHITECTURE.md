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
  advancements. `QuestWatch` runs the quest-item ledger and the package slowness. **The arrival is never done inside
  Fabric's `ServerPlayConnectionEvents.JOIN`**: that event fires right after the game-join packet, before vanilla has added
  the player to its world; a teleport in there moves the player to the district and vanilla then adds the same player to
  the overworld too, so two worlds tick and watch it. The overworld then sends its chunks (24 sections) to a client that
  decodes them as 16-section district chunks (stone, ore and an ocean 64 blocks too high around the first arrival), and
  the next teleport to another dimension throws in `ChunkTicketManager.handleChunkLeave` and leaves a ghost player. The
  arrival therefore waits three ticks (`Lifecycle.DEFAULT_ARRIVAL_DELAY`); `/lewandivka joinreplay` proves it on every CI run.
* A flow must not rely on seeing the players walk in while the right step is active: the transition carries the party
  into the base first and moves the story to the waking up 60 ticks later, so `BaseFlow` starts the wake-up from its
  tick (the client test found that the compass never came; `BaseFlowTest` pins it). Counted objectives done in advance
  (pedestals filled before the compass) are remembered one by one and counted when their step begins.

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

### The open country (survival world)

The district is a city of 304 x 304 blocks in the middle of an endless wilderness. `WildTerrain` (core, pure noise) describes
it: continentalness (sea or land), relief (lowlands, hills, mountains with ridges), rivers (zero lines of a noise), lakes
(basins), a temperature/humidity climate that picks one of 32 vanilla biomes with fitting surface blocks, and caves (wide
caverns, winding tunnels, a few openings to the daylight, lava below y 10). Next to the city the land is calm (no water, no
cliff, no cave, a temperate climate); `DistrictPlan.wildColumn` grows the flat ground of the city into the land over 64 blocks.
The plan declares the vanilla biomes, so the game itself decorates them (trees, flowers, ores, springs, snow, ice), adds the
animals of freshly generated chunks (`PlanChunkGenerator.populateEntities` calls `SpawnHelper`), spawns the ordinary monsters at
night and generates its structures (villages, mineshafts, ruined portals ...) far from the city only (`farFromTheCity`).
The city biome keeps no spawns and no features: the quest structures and the roaming gopniks are the story's. The story only
holds the evening and the night, and only sends the gopniks, around people who are in town (`Town`); out in the country the
days and nights are the ordinary ones. Beds work: the world wakes everybody up, but it cannot move the clock of a campaign
dimension (the clock of the overworld), so `TimeControl.sleep` moves it to the next morning once everybody in the district has
slept long enough.

The district is a world of its own for the game, which knows only the overworld, the Nether and the End by their keys, so three
things of ordinary survival needed a hand. The game lights a Nether portal only in the overworld and in the Nether
(`AbstractFireBlock.isOverworldOrNether`): `NetherGate` takes the click of flint and steel or a fire charge on a frame of
obsidian in the district and lights the portal exactly as the game does elsewhere (`NetherPortal.getNewPortal`). The way back
from the Nether leads to the overworld of the ordinary game, which is another world than the district: whoever comes out of it
is brought home to the district at the same coordinates (`Lifecycle`, `Travel.toSurface`). A bed replaces the spawn point the
player was given on arrival, and when the bed is gone the game falls back on the spawn of the ordinary overworld: the respawn
there is turned into a respawn at the spawn of the district (`Lifecycle.respawned`). The End needs nothing: its portal works
everywhere and the way out of it is the respawn point. The dimensions of a mod get none of the spawners of the overworld, so
there are no phantoms, patrols or wandering traders.

Tools: `gradle -p core worldMap -Pcx= -Pcz= -Pspan= [-Pcave=30]` draws the dimension (biomes, relief, water, caves of one
height) and prints statistics; `/lewandivka wild <x> <z> [view]` stands or hovers there, `/lewandivka survey <x> <z> <r>` counts
the logs, water, ores, caves and animals of the loaded chunks. The smoke test generates three 9 x 9 chunk areas and demands a
forest, water, caves, coal and iron, grass and animals; `/lewandivka sweep start <count> <side> <range>` · `structures <radius>` · `status` · `end`
then generates 24 squares of chunks scattered over 6000 x 6000 blocks (biomes and their features) and, around the villages,
mineshafts, strongholds and the other structures that the game's own `locate` finds, whole structures; it reports the biomes and
structures (and how high each structure stands over the ground) they came out with; any logged error of the generation fails the
smoke test. (A chunk needs its neighbours up to eight chunks away, so squares are far cheaper than single chunks.)

### The city: ground, underground and relief

`CityGround` (core) is the one function of the height of the district's ground. Every building stands on a pad at the level of
the streets (`GROUND` = 63); pads of the same level melt into one another, the margin of a pad is trimmed only against a
neighbour of another level, and the streets win outside the pads. Between the buildings the ground undulates gently
(`INNER_AMPLITUDE` 5 blocks) and outside the built-up area it grows into real hills over 120..215 blocks from the centre
(`AMPLITUDE` 15), so nothing is a cliff: `CityGroundTest` and `WildTerrainTest` walk the city and its edge. The dimension is
as deep as the ordinary world (`min_y` −64, 384 blocks): `ChunkPainter` puts stone above and deepslate below y 8, the bedrock
gradient at the bottom, lava below y −54 in the caves; the biome under the city is `minecraft:plains` more than eight blocks
below the surface, so the game's own features make ores, dungeons and caves there, and the places that reach deep (the garages
of the quests, the tower) keep the ordinary underground away from them (`deepStructures`).

### The buildings and their rooms

`DistrictPlan` places the buildings (layout rules checked by `layoutProblems()`: nothing on a street, on another building or on a
place of the quests) and the props of the streets; the buildings are blueprints made by `PanelBlock` (a row of sections with a
lobby, a stairwell and four flats a storey), `Buildings` (houses, kindergarten, shop), `School`, `Industry` (garages, boiler
house, substations) and `Yard` (stalls, sheds, the pitch). Rooms are text grids read by `RoomKit` (`B^` a bed, `T` a table,
`c<` a chair, `S>` a sofa ...), turned into blocks through a `Plot` (the axes of a room, so one template serves a flat and its
mirror image) by `Furnish`, the one place that speaks of furniture in terms of what it is. `HouseKit` makes the floors and the
stairs of the houses. `Walk` proves that a visitor can reach every cell of a room and that the stairs of a stairwell can be
climbed (stairs and slabs count as solid, a step up is one block, a top slab over the first stair is headroom).

**Windows** are blocks of light blue stained glass (`Pal.WINDOW`: clear glass looks like a hole from a street) in a closed frame of
white (piers, a sill, a lintel); the facades of the panel blocks have only calm weathering. `WindowsTest` checks that every window
block is closed by its frame, that no building has clear panes or panes at all. Panes were tried first and looked like thin
sticks: a key of a blueprint names a pane, a fence, a wall or a bar *without* its joins to the neighbours, which the game works out
when the block is placed by hand but not when a chunk is written, so a pane stays a thin post in the middle of the opening until
it is joined. Blocks of glass need no joins. The fences, the bars and the walls of the district still do, so
`ChunkPainter` marks every one of them for the post-processing of the chunk (`markBlockForPostProcessing`, the way the game does
it for the fences and the bars of its own structures), and the neighbours are joined when the chunk comes alive and before it is
sent to a player. The client bot counts the panes and bars at the facades that stand alone.

**Furniture and the furniture mod.** `Decor` names every piece twice, `handcrafted:oak_chair[...]|minecraft:oak_stairs[...]`
(`Keys.either`): the block of **Handcrafted** and, after the bar, the block of the game that stands in for it. The core looks only
at the stand-in (`Materials`, `Walk`), `BlueprintBuilder.rotateKey` turns both halves (and the direction words in the shape of a
Handcrafted table), and `StateResolver` (glue) picks the block of the mod when the game has it, all its properties fit and, for a
bed, it is a bed of the game's own kind (so it can be slept in); otherwise the stand-in. Tables and sofas join their neighbours
(`Decor.tableShape`, `couchShape`: read off the models of the mod, checked after every rotation of every building by
`DecorTest`). The blocks and properties of Handcrafted 3.0.6 are a test fixture (`tools/data/handcrafted-3.0.6-blocks.txt`, from
`/lewandivka dumpblocks handcrafted` in the server-pack run), and every key made by `Decor` is checked against it
(`RegistryTest`). The streets have the mod too: the park benches (`Props.bench`, three seats of `<wood>_bench` that join like a
sofa, stairs of the game as the stand-in) and the sets of a table with four chairs in the courtyards and behind the stalls of the
market (`Props.cafeSet`). `/lewandivka decor` says which blocks the furniture came out as; the smoke test of the plain server expects the
stand-ins, the pack boot expects the blocks of the mod (and none of the stand-ins).

### The people of the district

`ModEntities` lists eight kinds of **citizens** (`CitizenEntity`: walks within six blocks of his place, looks at players, says
one of five short things of `DialogueBook` when spoken to, never twice the same in a row, never while a dialogue of the story is
running) and seven **traders** (`VendorEntity`, a `MerchantEntity` of the game with GeckoLib animation: stands behind his stall,
greets, opens the ordinary trade screen). `Populace` (core) lists the places: citizen markers in the plan (`district:citizen_N`)
and a `vendor` marker in every stall of the market; `Townsfolk` (glue) makes somebody stand at every place whenever a player is
within 48 blocks (two empty looks in a row before it makes one, so a restart does not double the market). The goods of a trader
are the table `Wares` (core, tested: the items exist, the counts fit a stack, the prices are one to five emeralds, every trader
buys at least three plain goods of a first day and sells at least three things); `VendorEntity` builds the game's `TradeOffer`s
from it and makes them again every day and on every load. The currency is the emerald; the district token is a quest item and
stays out of the trade, so nothing a quest needs can be spent. `/lewandivka populace` makes everybody now.

### Calibrated physics

The dungeons are built for exact launch numbers: `core/.../world/Launch.java` holds the speed of the spring pads (vertical
2.4, apex about +27 blocks; horizontal 1.8 along the arrow), of the hatches (apex about +31) and of the glider
(0.42 blocks/tick, sink 0.09). `FlightSim` reproduces the vanilla tick of a player without input (gravity, drag, the
first tick slowed by the ground, collision of the 0.6 x 1.8 box with unit cubes) and `LaunchTest` flies every pad of the
sky ascent through the finished blueprint: the rider has to pass the edge of the next island while still rising (an
early design with weaker throws carried him into the underside of the island, which the client test showed), and has to
come down on it. The islands are placed from the same numbers (`SkyAscent.islandCentre`), so changing a constant moves
the level with it. The server (`Stations.spring`, `Abilities`) and the client (`Keys.glide`) use the same constants, and
the CI client test repeats the jumps with a real player in survival mode (`AutoTest.physics`).

The **wind** of the sky tube and of the two shafts (`wind_*` regions: the horizontal speed is blended towards the wind, a rider
who is slow upwards is lifted a little so the top of the throw stretches out) is applied by the client to its own player
(`client/Winds`, every second tick, in the air): the client owns the movement and knows its real velocity. The first design
did it on the server from the positions the client had sent, estimating the velocity; every push of that estimate was a little
out of date, took a few hundredths from a rising rider and cost the service-shaft hatch of the tower the last blocks of its
throw (CI client test), and a player with a slow connection would have fared worse. The push is `Launch.wind`, the same function
that `LaunchTest` flies through the sky tube, the spring shaft and the service shaft with the wind on either tick parity.

The same care goes for the chase of the Debtor: a mob's pace grows with the square of attribute x multiplier (the
product is the movement speed *and* the forward input), so `ChasePace` derives the navigation multiplier from the pace
the chase should have (4.4 blocks/s alone, 5.0 for a party, a sprinting player does 5.6) and tires him after a long
pursuit; a GameTest measures the real navigation.

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
and GeckoLib are never embedded in `lewandivka.jar`. The server pack carries only the jars whose license lets anybody pass
them on (`redistributable()`: MIT, Apache, the GPL family, MPL ...); the others (Handcrafted is under the Terrarium license, all
rights reserved but for its code) are listed with URL and hash in `mods-download.txt` and fetched from Modrinth by
`fetch-mods.sh` / `fetch-mods.bat`, which the start scripts call (and the pack boot test of the CI, too). `THIRD_PARTY.md` says
for every project whether its jar is in the archive.

## 10. CI (`.github/workflows/lewandivka.yml` and `lewandivka-client.yml`)

Two workflows. `lewandivka.yml` has two jobs that run side by side. **server**: core unit tests → `./gradlew build` →
(reading material, not a check) the vanilla classes that matter are decompiled with CFR and published as `vanilla-src.tgz`
→ Fabric GameTests (headless server; every registry entry, every entity is created, ticked and reloaded from NBT, the
campaign NBT round trip) → dedicated-server smoke test over rcon (`/lewandivka validate`, `/lewandivka selftest`, the join
replay below, then a restart of the same world: the campaign step must survive, the generated world is read back and
compared with the plan again, every encounter is rebuilt, reset and wiped) → resource validation (including blockstate
properties against the block catalog). **package**: modpack build → boot the finished server pack.
`lewandivka-client.yml` (its own workflow, because a run takes half an hour and a new push waits for it instead of
cancelling it): the real client test (Xvfb, software GL: story-step soak of every dungeon, the night population, the
physical jump checks, the sky tram ride, the end of the first act with real clicks, screenshots; any logged error of the
mod fails it). Every job publishes its logs, screenshots and artifacts to the pre-release `ci-latest` when it ends
(`info-server.txt`, `info-client.txt`, `info-package.txt` say which commit they belong to; the client job also publishes
what it knows every four minutes, marked `state=partial`).

**The join replay** (`/lewandivka joinreplay`, `JoinReplay`): a player is created and joined through the real
`PlayerManager.onPlayerConnect` with a connection that has no socket behind it, so the packets the client would receive
stay in a queue. They are read back in order, every chunk is decoded the way the client decodes it and compared with the
chunks of the three worlds (a client once showed stone and diorite in the district for a while after the first arrival),
and `torture` moves such a player the way the client test does (a forced gallery far away, teleports inside the district,
crossings to Chromandivka and back) while the chunk watchers are updated as the movement packets of a client would do it,
reporting the exceptions of the teleports and which ticket manager lists the player where. The chunks of each phase are
judged by the world the client was in when it received them, and while the worlds still hold them (the player is scanned
before every move).
