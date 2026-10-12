# Boss models made in Blockbench

Four of the five bosses wear models that were made in Blockbench ("Generic Model", a cube model with a patch of the texture on every
face). The files are here the way they were supplied by the project owner (they were made by hand from reference pictures; they are not
files of an outside author):

| Blockbench file | Character | Boss of the mod | Size in the game |
|---|---|---|---|
| `crimson_beast` | Crimson Beast, a beast in a crimson coat | Garage King | scale 1.45, hitbox 2.2 x 3.4 |
| `jester_cat` | Jester Cat | Collar Collector | scale 1.3, hitbox 1.2 x 2.8 |
| `sun_keeper` | Sun Keeper, a black uniform and a sun for a face | The Conductor | scale 1.15, hitbox 1.1 x 3.0 |
| `hooded_watcher` | Hooded Watcher, a pale cloak and one eye | The Colorless Head | scale 1.75, hitbox 1.7 x 3.7 |

Lady Vortex is still built by `tools/resgen/entities_bosses.py`. Which character wears which boss is one line of
`tools/resgen/entities_blockbench.py` (`SOURCES`) and one line of `ModEntities.java` for the size.

`python3 tools/generate_resources.py entities` turns the files into `geo/entity/<boss>.geo.json` and `textures/entity/<boss>.png`
(`tools/resgen/blockbench.py`; the rules of the conversion are in its header) and writes the animations of the boss
(`tools/resgen/entities_blockbench.py`: the nine animations every boss has, and the tail of the cat, the rays of the sun and the cloak
of the watcher). The bone names of the files (`body`, `head`, `arm_left`, `arm_right`, `leg_left`, `leg_right`) are the ones the
animations use; `tools/validate_resources.py` checks that the animations name only bones that exist.
