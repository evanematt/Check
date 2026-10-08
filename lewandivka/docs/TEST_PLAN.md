# Test plan

## 1. What is tested automatically (every push, `.github/workflows/lewandivka.yml`)

| Level | What | Where | Result is... |
|---|---|---|---|
| **Unit** (222 tests, pure Java, no Minecraft) | campaign model + save round trip, director table (every step reachable, early events replayed, counters), party scaling for 1/2/3, sync groups, chromatic charge relay, five boss rule sets (phases 70/35/5 %, timelines, wipes), every dungeon flow with the strict recording `FakeEnv` (garage №13, last tram, base, rainbow garage, shelter, aquapark, sky ascent/depot, tower approach, tower, postgame, garage №0), blueprint DSL, structure reachability, world plans, registries, exported spec freshness | `core/src/test` | `gradle -p core test` |
| **Build** | compiles against Minecraft 1.20.1 + Yarn, remaps, client + common source sets | CI step "Compile and remap" | `BUILD SUCCESSFUL` |
| **GameTests** (Fabric, headless server) | every catalog entry is registered (blocks, items, entities, sounds), every blueprint key resolves to a real block state, compass targets exist, `ChunkPainter` builds every structure where its blueprint says (solid spawn), the campaign survives an NBT round trip | `LewandivkaGameTests` | `All 5 required tests passed` |
| **Server smoke test** | a fresh dedicated server loads both custom dimensions through the normal data-pack path, `/lewandivka status`, force-loads the world, **`/lewandivka validate`** compares generated structures with their blueprints | `tools/ci/smoke.sh` | `SMOKE-RESULT OK` |
| **Static resource validation** | JSON syntax, models/blockstates/loot tables for every block, textures (size, alpha), GeckoLib geometry vs texture vs animations, uk/en language parity, every language key / sound / item / block / entity id **used in code** exists, data pack references, vanilla ids against the 1.20.1 registry | `tools/validate_resources.py` | `resources OK` |
| **Client test** | a real client (Xvfb + Mesa software OpenGL) joins a dedicated server with `--quickPlayMultiplayer`; `AutoTest` tours every structure of both dimensions, summons every entity, shows the block gallery, opens the notebook, config and credits screens, takes screenshots, fails on crash | `tools/ci/clienttest.sh` | `CLIENTTEST-RESULT OK` + screenshots in the release `ci-latest` |
| **Packaging** | Modrinth resolution of the whole pack (dependency ranges checked), `.mrpack`, server pack, TLauncher installer, `THIRD_PARTY.md` | `tools/modpack/build_modpack.py` | `wrote N mods` |
| **Server pack boot** | the finished `Lewandivka-Server-1.0.0.zip` is unzipped and started (with the real third-party server mods), rcon `/lewandivka validate` | `tools/ci/serverpack-smoke.sh` | `SERVERPACK-RESULT OK` |

Reproduce locally: see "Building from source" in `README.md`.

## 2. What the automated tests cannot see (manual checklist)

Nobody is in the world during CI, so interactions that need a human body are covered by the flow tests (logic) but
should be walked through once with real players. Tick per party size.

| # | Stage / mechanic | Solo | Duo | Trio | Expected |
|---|---|---|---|---|---|
| 1 | First join: arrives at the district spawn, gets the notebook, title shown | ☐ | ☐ | ☐ | notebook shows "Десь я не туди вийшов" |
| 2 | Explore 4 of 6 places | ☐ | ☐ | ☐ | counter 1/4 … 4/4, step advances |
| 3 | First night: gopniks appear; neutral groups ask "Сємки є?"; seeds make them friendly; 4 tokens (drops, stashes, gifts) | ☐ | ☐ | ☐ | hostile cap 3 / 5 / 7; never a swarm |
| 4 | Kiosk: stashes give 6 planks, 2 iron ingots, a sign; craft; place on the painted foundation | ☐ | ☐ | ☐ | wrong place gives the block back; Mr. Shlahbaum appears |
| 5 | Debtor: note, 5 clues, chase, caught, three ways to settle (3 emeralds / tokens or reputation / 8 seeds) | ☐ | ☐ | ☐ | solo: he stumbles more; kettle given |
| 6 | Kettle with water placed: water turns pink + sound | ☐ | ☐ | ☐ | garage note given |
| 7 | Garage №13: breakers in note order, two switch boxes, three levers, plate 13, package, guards, growls, tunnels, exit | ☐ | ☐ | ☐ | window 80 / ~144 / ~288 ticks; package slows the carrier; delivered to Mr. Shlahbaum |
| 8 | Last tram: night, three waves, validators, composter reward | ☐ | ☐ | ☐ | checkpoint after each wave |
| 9 | Chroma: first swallow starts the 5 min bar «ХРОМА»; all online players needed; expiry returns the tablets | ☐ | ☐ | ☐ | transition cinematic, wake up in the base |
| 10 | Base: compass, four pedestals (kettle, package, composter, token), portal opens | ☐ | ☐ | ☐ | compass points at Rainbow Garage |
| 11 | Rainbow Garage: paint taps, lifts, presses, batteries, Garage King, **Dash** | ☐ | ☐ | ☐ | boss health 100 % / reduced; V works after the fight |
| 12 | Shelter: levers, Chinazik's food route, Metadonna's box, Collar Collector | ☐ | ☐ | ☐ | cats never die or get lost |
| 13 | Aquapark: cats open the entrance, three pumps, Lady Vortex, water cores, **Spring Insoles** | ☐ | ☐ | ☐ | 2 / 3 drains needed |
| 14 | Sky: spring ascent, 2.5 min tram ride, one attack, depot switches, tickets, Conductor, **Glider** | ☐ | ☐ | ☐ | wrong switches roll the car back |
| 15 | Ring restored cinematic, tower approach (dash doors, cats' wall, shaft, glider chasm), falls respawn at checkpoints | ☐ | ☐ | ☐ | nobody dies from the gorge |
| 16 | Tower: ticket №847, urgent entrance, 5 floors | ☐ | ☐ | ☐ | no multi-hour waiting |
| 17 | Colorless Head: charge, pass with Q, relays solo, platforms decay, phase 3, steal at 5 %, cats | ☐ | ☐ | ☐ | no unavoidable damage; world goes grey |
| 18 | Epilogue: ring carries the party to the base, portal, morning, credits, free play | ☐ | ☐ | ☐ | postgame unlocked |
| 19 | Postgame: 12 cats → square ticket → wrong tram → Garage №0 → package (sock) | ☐ | ☐ | ☐ | advancements "Twelve Cats", "Wrong Tram", "Garage No. 0", "Better Not to Have Asked" |
| 20 | Disconnect / rejoin mid-dungeon and mid-Chroma | ☐ | ☐ | ☐ | no soft lock; abilities restored |
| 21 | Death with a quest item, `/clear`, full inventory | ☐ | ☐ | ☐ | item handed out again within ~2 s |
| 22 | Dedicated server restart in every stage | ☐ | ☐ | ☐ | state restored (`rebuild`) |
| 23 | Accessibility: shake 0 %, reduced flashes, no shaders | ☐ | ☐ | ☐ | every telegraph still readable (text + sound) |
| 24 | TLauncher: installer on a clean `.minecraft`, join the server pack | ☐ | ☐ | ☐ | version `fabric-loader-0.16.10-1.20.1` starts |

## 3. Known limits (honest list)

* The unit tests prove the **logic** of every encounter; the physical feel (jump distances, how long a lever walk takes,
  boss telegraph readability) needs the manual pass above. Timings were chosen from the spec and scaled by `PartyScale`.
* The CI client test renders with a **software** OpenGL driver at low resolution and without Sodium/Iris: it proves the
  client starts, renders every structure and entity without an exception, not that the frame rate is good.
* Third-party mods are resolved and downloaded at build time from Modrinth (hash-checked). Their behaviour together with
  the mod (Sodium, Iris, shaders, dynamic lights...) is covered by their own `fabric.mod.json` dependency ranges and by
  the server-pack boot; a client with the full stack was not rendered in CI.
* Audio is synthesised (original, small): functional and distinct per event, not studio quality.
