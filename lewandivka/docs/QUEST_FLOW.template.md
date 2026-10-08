# Quest flow

> The table in this file is **generated** from the real story table (`CampaignDirector`) by
> `./gradlew -p core dumpQuestFlow`. The prose around it lives in `docs/QUEST_FLOW.template.md`.

The campaign is one linear chain of **steps** (`QuestStep`) grouped into **stages** (`CampaignStage`). The state is stored
in the world (`CampaignState`, data version 1) and shared by all players; every player additionally keeps abilities,
notes and reputation. Systems never change the step themselves: they report an *event* and the `CampaignDirector`
decides what it means. An event that arrives too early (a player wandered ahead) is remembered and counts as soon as the
story reaches its step, so doing things in an unexpected order can never block the campaign.

## Overview of the acts

| Act | Stages | What happens | Typical time |
|---|---|---|---|
| **I. The district** (`lewandivka:district`) | Prologue, First night, Reputation, The Debtor, Garage №13, The Last Tram, Chroma | Find 4 of 6 places, collect district tokens, build the kiosk, meet Mr. Shlahbaum, track the Debtor (clues, chase, three ways to settle), the magic kettle, Garage №13 (three power points, the package escort through the tunnels), the Last Tram (3 waves, the Fare Dodger leader), swallow Chroma together | 1.5-2 h |
| **II. The other side** (`lewandivka:chromandivka`) | Crossing, Base, Rainbow Garage, Shelter of Lost Names, Dry Lake Aquapark, Depot Above the Sky | Wake up in the base, the compass, four artifact pedestals open the coloured portal. Four dungeons with a boss each (Garage King -> **Dash**, Collar Collector, Lady Vortex -> **Spring Insoles**, Conductor -> **Glider Ticket**). Each boss restores a fragment of the rainbow ring | 2.5-3 h |
| **III. The tower** | Tower, Final fight | The ring is whole and the Colorless Head calls the players. Traversal exam (dash doors, cats' passage, spring shaft, glider chasm), five floors that reuse earlier mechanics, the Chromatic Charge fight in three phases | 45-60 min |
| **Epilogue and postgame** | Epilogue, After everything | The ring carries the party home, morning in the district, credits, free play (twelve cats, the vanishing seeds, the wrong tram, Garage №0, what was in the package) | 15 min + optional |

## Steps

{{TABLE}}

"Needs" is the number of counted events the step wants (clues 5, panels 3 ...). "Completed by" is the event id of
`Events.java`.

## Dungeons in detail (all logic is plain Java in `core/.../flow/dungeon`, tested without Minecraft)

### Garage №13 (`Garage13Flow`)
1. Three power points: **A** four breakers in garage 9 in the order given by four notes (a wrong order resets the breakers),
   **B** two switch boxes pressed together (window 80 ticks for three players, longer for two, long enough for one player
   to walk between them), **C** three levers up / down / up confirmed on a console.
2. All three lamps on: the wall between 12 and 14 opens and the plate "13" appears; the package lies in the room.
3. Taking the package closes the main gate, opens the floor hatch and calls the guards; the package growls every 15-25 s
   (quieter, 2.5x rarer, when it lies on the ground; the carrier is slowed by 15 %).
4. The escape runs through the maintenance tunnels. The operator's lever opens the tunnel gate for a limited time
   (600 ticks for three players, scaled up for fewer). Nobody is forced into a role.

### The Last Tram (`LastTramFlow`)
Night is forced. Three waves of Fare Dodgers around the stop; the leader's shield only drops while the three validators
on the platform are punched in the same window (`SyncPresses`). A checkpoint after every wave; the reward is the
Ticket Composter.

### Base and portal (`BaseFlow`)
Wake-up dialogue, compass, then the four pedestals accept the kettle, the package, the composter and a district token.
The fourth opens the coloured portal in both worlds.

### Rainbow Garage (`RainbowGarageFlow`, boss `GarageKingRules`)
Paint workshop (mix orange / green / purple with three taps), lift room (three lifts with call levers), industrial presses
(timed, with a lamp and a sound warning before they close). Each wing gives a battery; three batteries in the hub open
the arena. The Garage King: arena levers must be pulled together (three players: one lever each; two players: one handles
two; solo: the windows are long enough to run between all of them).

### Shelter of Lost Names (`ShelterFlow`, boss `CollarCollectorRules`)
Levers in a window, then Chinazik leads the party to four fish bowls (the cat walks, sits, eats; the players follow the
purring), the hidden box with Metadonna, and the Collar Collector: eight collar stands, one real collar per cat, the cats
point at the real clone. Dash is unlocked by the previous boss.

### Dry Lake Aquapark (`AquaparkFlow`, boss `LadyVortexRules`)
The cats open the hidden entrance when the party lingers. Three pumps fill a quarter of the park each with orange liquid.
Lady Vortex floods one quarter at a time (colour **and** symbol are announced); in phase two the party routes water
cores into drains (three drains for groups, two for a solo player).

### Sky: ascent, ride, depot (`SkyAscentFlow`, `SkyDepotFlow`, boss `ConductorRules`)
Spring pads, floating islands, a bounce tunnel and an updraft lead to the sky stop. The tram really drives for about two
and a half minutes over the panorama; one short attack on the way. In the depot four switches route a tram car to the
ticket office (a wrong arrangement rolls the car back, nobody is hurt; the notes of the dispatcher room give the answer);
tickets with three symbols; the Conductor's arena has three composter stands and the "TICKET!" validator mechanic.

### Tower approach and the tower (`TowerApproachFlow`, `TowerFlow`, boss `ColorlessHeadRules`)
Approach: three dash doors, the "normal wall" the cats open, a spring shaft with an updraft, the glider chasm
(falling puts you back at the last checkpoint, it never kills). Tower: lobby with the ticket machine (`№847`, serving
`№003`) and the urgent side entrance, archive levers, Office 404, the colour department, the service shaft, the collapsed
floor, then the arena.

### The Colorless Head
Phase 1: only the holder of the **Chromatic Charge** can hurt him; the charge overloads after about 12 s and must be
passed (drop key **Q** while you hold it, or the aim key). Solo: two relay devices return the charge after a short delay.
Phase 2 (70 %): platforms lose colour (coloured, grey, cracked, collapsed; never all safe platforms at once).
Phase 3 (35 %): short sequences of earlier mechanics, one at a time. At 5 % he steals the charge, the world goes grey, the
cats walk in and one of them redirects the charge to the party for the final window.

## Scaling

| Mechanic | 3 players | 2 players | 1 player |
|---|---|---|---|
| Boss health | 100 % | reduced | reduced further |
| Adds / guards | full | fewer | about half |
| Synchronised presses | short window | longer | long enough to walk between the stations |
| Deadlines (tunnel gate, vulnerable windows) | base | longer | longest |
| Debtor chase | players cut off routes | he stumbles sometimes | he stumbles more often |
| Chromatic Charge | pass between players | pass between players | two relay devices |

The party size is measured when an encounter begins (`PartyService.scaleFor`), never "players != 3 -> fail".
`/lewandivka party <1|2|3|auto>` forces a size for testing.

## Safety nets

* **Quest items** are protected by `QuestItemLedger`: every two seconds the server compares what the party must hold at the
  current step with what it holds (inventory and items lying within 6 blocks) and hands out what is missing.
* **Checkpoints**: every dungeon saves progress; `/lewandivka checkpoint` teleports to the last one;
  `/lewandivka reset encounter <name>` restarts a dungeon without losing solved puzzles; `/lewandivka setstage <stage>`
  and `/lewandivka step <step>` repair a stuck campaign.
* **Wipes**: when everybody dies in a boss fight the boss resets completely (health, adds, floods, platforms).
* **Falls**: pits and gorges teleport to the last checkpoint with a little damage.
* **Disconnects**: a player who leaves is dropped from the Chroma synchronisation and from the party scaling; on return
  the abilities of all defeated bosses are granted again (`Story.catchUp`).
