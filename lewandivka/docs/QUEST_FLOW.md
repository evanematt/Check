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

| # | Step | Stage | Objective (uk) | Objective (en) | Needs | Completed by | Then |
|---|------|-------|----------------|----------------|-------|--------------|------|
| 1 | `explore_district` | prologue | Огляньте район: двори, гаражі, магазин (%s/%s) | Look around the district: courtyards, garages, the shop (%s/%s) | 4 | `district.poi` x4 | -> `collect_tokens` |
| 2 | `collect_tokens` | first_night | Здобудьте жетони району (%s/%s) | Collect district tokens (%s/%s) | 4 | `district.token` x4 | -> `craft_kiosk`; message `message.lewandivka.kiosk.unlocked` |
| 3 | `craft_kiosk` | district_reputation | Скрафтьте «Закинутий кіоск»: 6 дощок, 2 залізні зливки, табличка | Craft the Abandoned Kiosk: 6 planks, 2 iron ingots, 1 sign |  | `kiosk.crafted` | -> `place_kiosk` |
| 4 | `place_kiosk` | district_reputation | Поставте кіоск на фундамент біля магазину | Place the kiosk on the foundation near the shop |  | `kiosk.placed` | -> `talk_shlahbaum`; spawns `pan_shlahbaum` |
| 5 | `talk_shlahbaum` | district_reputation | Поговоріть із Паном Шлагбаумом | Talk to Mr. Shlahbaum |  | `shlahbaum.met` | -> `debtor_note`; gives 1x `debtor_note` |
| 6 | `debtor_note` | debtor | Прочитайте записку «Боржник» | Read the note "The Debtor" |  | `debtor.note` | -> `debtor_clues` |
| 7 | `debtor_clues` | debtor | Знайдіть сліди Боржника (%s/%s) | Find the Debtor's traces (%s/%s) | 5 | `debtor.clue` x5 | -> `debtor_chase`; spawns `debtor` |
| 8 | `debtor_chase` | debtor | Наздожени Боржника | Catch the Debtor |  | `debtor.caught` | -> `debtor_resolve` |
| 9 | `debtor_resolve` | debtor | Вирішіть справу з Боржником | Settle things with the Debtor |  | `debtor.resolved` | -> `kettle_test`; gives 1x `magic_kettle` |
| 10 | `kettle_test` | debtor | Випробуйте чайник із водою | Try the kettle with water in it |  | `kettle.placed` | -> `garage_find`; dialogue `shlahbaum_kettle`; gives 1x `garage13_note` |
| 11 | `garage_find` | garage_13 | Знайдіть гаражний кооператив | Find the garage cooperative |  | `g13.entered` | -> `garage_panels` |
| 12 | `garage_panels` | garage_13 | Увімкніть три електричні точки (%s/%s) | Restore the three power points (%s/%s) | 3 | `g13.power` x3 | -> `garage_package`; message `message.lewandivka.garage.open` |
| 13 | `garage_package` | garage_13 | Заберіть посилку з гаража №13 | Take the package from Garage No. 13 |  | `g13.package_taken` | -> `garage_escape` |
| 14 | `garage_escape` | garage_13 | Винесіть посилку: головні ворота зачинилися | Carry the package out: the main gate has closed |  | `g13.escaped` | -> `garage_deliver` |
| 15 | `garage_deliver` | garage_13 | Віднесіть посилку Панові Шлагбауму | Bring the package to Mr. Shlahbaum |  | `package.delivered` | -> `tram_wait`; dialogue `shlahbaum_package`; gives 1x `last_tram_note` |
| 16 | `tram_wait` | last_tram | Зачекайте на трамвайній зупинці | Wait at the tram stop |  | `tram.started` | -> `tram_fight`; cinematic `last_tram` |
| 17 | `tram_fight` | last_tram | Відбийте безквиткових (хвиля %s/%s) | Fend off the fare dodgers (wave %s/%s) | 3 | `tram.wave` x3 | -> `tram_report`; gives 1x `ticket_composter`; message `message.lewandivka.tram.reward` |
| 18 | `tram_report` | last_tram | Поверніться до Пана Шлагбаума | Return to Mr. Shlahbaum |  | `tram.reported` | -> `chroma_take`; dialogue `shlahbaum_chroma`; gives 1x `chroma_tablet` |
| 19 | `chroma_take` | chroma_ready | Проковтніть «Хрому» разом: п'ять хвилин на всіх | Swallow Chroma together: five minutes for everyone |  | `chroma.synced` | -> `transition`; cinematic `transition` |
| 20 | `transition` | chroma_transition | Тримайтеся | Hold on |  | `transition.done` | -> `base_wake`; spawns `base_empty` |
| 21 | `base_wake` | chromandivka_base | Огляньте будинок і прокиньтесь | Look around the house and wake up |  | `base.compass` | -> `base_pedestals`; gives 1x `chromatic_compass` |
| 22 | `base_pedestals` | chromandivka_base | Покладіть на постаменти чайник, посилку, компостер і жетон (%s/%s) | Put the kettle, package, composter and token on the pedestals (%s/%s) | 4 | `base.pedestal` x4 | -> `rg_travel`; opens the coloured portal; message `message.lewandivka.portal.open` |
| 23 | `rg_travel` | rainbow_garage | Дійдіть до «Веселкового гаража» за компасом | Reach the Rainbow Garage with the compass |  | `rg.entered` | -> `rg_wings` |
| 24 | `rg_wings` | rainbow_garage | Пройдіть три крила гаража й візьміть батареї (%s/%s) | Clear the three wings and take the batteries (%s/%s) | 3 | `rg.wing` x3 | -> `rg_boss` |
| 25 | `rg_boss` | rainbow_garage | Переможіть Гаражного Короля | Defeat the Garage King |  | `boss.garage_king` | -> `sh_dash`; unlocks ability **dash**; restores a ring fragment |
| 26 | `sh_dash` | shelter | Потрапте до Укриття втрачених імен: потрібен ривок | Reach the Shelter of Lost Names: you need a dash |  | `sh.hall` | -> `sh_levers` |
| 27 | `sh_levers` | shelter | Потягніть три важелі разом (%s/%s) | Pull the three levers together (%s/%s) | 3 | `sh.levers` | -> `sh_chinazik`; spawns `chinazik`; dialogue `chinazik_first` |
| 28 | `sh_chinazik` | shelter | Виведіть чорного кота на килимок: кладіть їжу (%s/%s) | Lead the black cat onto the rug: place food (%s/%s) | 4 | `sh.chinazik` | -> `sh_metadonna`; dialogue `chinazik_named` |
| 29 | `sh_metadonna` | shelter | Знайдіть сіру кішку в коробках складу | Find the grey cat among the warehouse boxes |  | `sh.metadonna` | -> `sh_boss`; spawns `metadonna`; dialogue `metadonna_named` |
| 30 | `sh_boss` | shelter | Переможіть Колекціонера Нашийників | Defeat the Collar Collector |  | `boss.collar_collector` | -> `aq_find`; restores a ring fragment; spawns `base_cats` |
| 31 | `aq_find` | aquapark | Знайдіть вхід в аквапарк: коти покажуть | Find the aquapark entrance: the cats will show you |  | `aq.entered` | -> `aq_pumps` |
| 32 | `aq_pumps` | aquapark | Запустіть три насоси (%s/%s) | Start the three pumps (%s/%s) | 3 | `aq.pump` x3 | -> `aq_boss` |
| 33 | `aq_boss` | aquapark | Переможіть Пані Вирву | Defeat Lady Vortex |  | `boss.lady_vortex` | -> `sky_ascent`; unlocks ability **spring_insoles**; restores a ring fragment |
| 34 | `sky_ascent` | sky_depot | Піднімайтеся пружинами до небесної зупинки | Bounce up to the sky tram stop |  | `sky.stop` | -> `sky_ride` |
| 35 | `sky_ride` | sky_depot | Сядьте в небесний трамвай | Board the sky tram |  | `sky.arrived` | -> `sky_switches` |
| 36 | `sky_switches` | sky_depot | Налаштуйте стрілки, щоб вагон дійшов до каси | Set the switches so the car reaches the ticket office |  | `depot.switches` | -> `sky_tickets` |
| 37 | `sky_tickets` | sky_depot | Отримайте квитки з різними знаками (%s/%s) | Obtain tickets with different symbols (%s/%s) | 3 | `depot.ticket` x3 | -> `sky_boss`; message `message.lewandivka.ticket.got` |
| 38 | `sky_boss` | sky_depot | Переможіть Кондуктора | Defeat the Conductor |  | `boss.conductor` | -> `tower_ring`; unlocks ability **glider**; restores a ring fragment; cinematic `ring_restored` |
| 39 | `tower_ring` | tower | Подивіться вгору: кільце ціле | Look up: the ring is whole |  | `ring.restored` | -> `tower_approach`; dialogue `head_message` |
| 40 | `tower_approach` | tower | Пройдіть підхід до Вежі: ривок, прохід, пружини, планер | Cross the approach to the Tower: dash, passage, springs, glider |  | `approach.done` | -> `tower_climb` |
| 41 | `tower_climb` | tower | Підніміться Вежею (поверх %s/%s) | Climb the Tower (floor %s/%s) | 5 | `tower.floor` x5 | -> `final_fight` |
| 42 | `final_fight` | final_boss | Переможіть Безбарвного Голову | Defeat the Colorless Head |  | `boss.colorless_head` | -> `epi_return`; cinematic `rescue_ring` |
| 43 | `epi_return` | epilogue | Огляньте оновлену Хромандівку | Look around the restored Chromandivka |  | `epilogue.base` | -> `epi_portal`; spawns `base_epilogue` |
| 44 | `epi_portal` | epilogue | Поверніться порталом до Левандівки | Return through the portal to Lewandivka |  | `epilogue.portal` | -> `epi_morning`; dialogue `epilogue_morning` |
| 45 | `epi_morning` | epilogue | Зустріньте ранок біля кіоска | Meet the morning at the kiosk |  | `epilogue.done` | -> `post_free`; cinematic `credits`; message `message.lewandivka.postgame.unlocked` |
| 46 | `post_free` | postgame | Вільна гра: дванадцять котів, сємки, трамвай, гараж №0 | Free play: twelve cats, the seeds, the tram, Garage No. 0 |  | (end of the campaign) |  |


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
