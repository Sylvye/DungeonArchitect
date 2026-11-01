# DungeonArchitect

DungeonArchitect is a Paper 1.21.11 / Java 21 plugin foundation for developer-authored procedural dungeons.

## Build

```powershell
./gradlew build
```

The checked-in Gradle wrapper uses Gradle 8.14.3.

## Developer Workflow

- `/da wand` gives the authoring wand.
- `/da selector` gives the spyglass component selector.
- `/da` opens the main GUI; `/da help` prints command help.
- Tab completion covers command actions and arguments across `/da`, `/da minigame`, `/dminigame` (also `/dmg`), and `/dplay`. It suggests marker purposes, active component names, template/preset/run IDs, teams, kits, and list entries. New command routes must include completion and matching coverage tests.
- `/da room create <id>` starts a room session.
- Left/right click blocks with the wand to set the current selection.
- Hold the selector to outline doors, markers, and feature slots; right click to select the first component in view.
- `/da room bounds` saves the current selection as the room bounds.
- `/da room door` saves the current selection as a door region using defaults.
- `/da room door [id] [socketType] [facing]` adds a socket with explicit overrides.
- Markers describe rectangular block areas with one purpose: **Generic**, **Loot**, or **Spawn**. Open a template's **Edit In World**, select two corners with the normal wand, then open **Markers → Add Marker → Choose Purpose**. Names are automatic and can be changed. A single block remains a valid selection. The Selector outlines the entire area.
- Click a marker for its purpose, position, dimensions, loot settings, rename/delete, or **Use Current Wand Selection**. During world editing, all marker changes wait for **Save Edit**; **Cancel Edit** discards them. Without an active edit, completed menu settings save metadata immediately.
- **Loot** markers fill each distinct inventory inside their selection independently. A double chest counts once, even if both halves are selected. Overlapping loot markers targeting the same inventory block dungeon preparation and identify both owners. A Loot marker without a table is inactive.
- **Spawn** markers belong to rooms and select floor blocks. Players stand above safe selected floors and are spread across all of the room's areas, with distinct positions chosen deterministically. Classic start rooms and minigame spawn rooms require authored areas with sufficient safe capacity; no hardcoded entry fallback is used.
- `/da room marker area <name> [generic|loot|spawn]` creates an area from the current selection; `/da room marker purpose <name> <purpose>` changes its purpose. Door and feature commands support Generic and Loot areas. Spawn creation uses the full wand selection with either `marker add` or `marker area`. Older spawn names remain readable as compatibility aliases.
- `/da room component rotate [N|E|S|W|up|down]` rotates the selected room door or feature slot; full direction names are also accepted.
- `/da room component face [N|E|S|W|up|down]` sets the selected room component's facing without moving it, when that facing is valid.
- `/da door component` manages the active door template's gateway, markers, and feature slots; unsupported type/action combinations are rejected.
- `/da door component rotate|face [N|E|S|W|up|down]` supports the selected gateway or feature slot.
- `/da feature feature [name]` creates a weighted nested feature slot from the current selection.
- `/da feature component` selects, resizes, renames, rotates, faces, or removes nested feature slots and markers.
- `/da room feature <name>` adds a random feature marker at the targeted block.
- `/da room save [id]` exports `room.nbt` and `room.yml`.
- `/da room rename <oldId> <newId>` and `/da room duplicate <oldId> <newId>` manage room template ids.
- `/da room delete <id>` permanently deletes a room template.
- `/da feature create <id>`, `/da feature bounds`, `/da feature save [id]`, `/da feature rename <oldId> <newId>`, `/da feature duplicate <oldId> <newId>`, and `/da feature delete <id>` manage reusable feature templates.
- `/da door create <id>`, `/da door bounds`, `/da door gateway`, `/da door save [id]`, `/da door rename <oldId> <newId>`, `/da door duplicate <oldId> <newId>`, and `/da door delete <id>` manage reusable door templates.
- `/da gui`, `/da rooms`, `/da features`, `/da doors`, `/da loot`, `/da config`, and `/da dungeons` open inventory GUIs.
- In the loot editor, left-click or shift-left-click an inventory item to add a one-item template without changing your inventory. **Add Table** inserts a weighted reference that resolves one item from another loot pool. Left-click an entry to remove it and right-click it to configure amounts, weight, or caps. Loot pools do not control container fullness; configure a minimum and maximum roll count on each room, door, or feature marker binding. Use the same value for both when you want an exact number. Completed changes save immediately; **Done** only closes the editor.
- `/da reload` reloads templates and feature pools.
- `/da generate <roomCount> [seed]` creates an isolated dungeon world and teleports the player to the start room.
- `/da debug room`, `/da debug instance`, `/da list`, `/da teleport [instance] <roomIndex>`, `/da destroy [instance]`, and `/da exit` support debugging and dungeon management.

## Notes

- Room templates are saved under `plugins/DungeonArchitect/rooms/<id>/`.
- The first generator is deterministic, socket-based, and rejects bounding-box collisions before any blocks are placed.
- Random features are metadata-driven weighted slots configured in `feature-pools.yml`.
- Feature templates may contain weighted feature slots. The dependency graph must be acyclic and is limited by `features.max-nesting-depth` (default 4) and `features.max-expanded-placements` (default 256).
- If an old template was saved before size-based capture, re-save it from the original build area. `/da room inspect <id>` shows the metadata size and actual `room.nbt` size.

Existing metadata without marker dimensions loads as one-block areas. Legacy spawn feet and Dungeon Entry positions become one-block floor areas; duplicate equivalent entries are avoided. Migration is persisted on the next normal template save and never changes structure files. Enlarge migrated areas when entering more players than their safe capacity.

## Minigame launches

Open `/da` → **Minigames**, or use `/da minigame` (`/dminigame`, alias `/dmg`).

1. Author a `TEAM_SPAWN` room using the room category editor. Open **Spawn Areas: N**, or **Edit Spawn Areas** from minigame setup, to use the same marker screen as every room. Choose **Edit In World**, use `/da wand` to select floor blocks, then **Markers → Add Marker → Spawn**. Use `/da` to return to the marker screen during minigame authoring. **Save Edit** saves the template; **Cancel Edit** discards workspace changes. Each team needs enough distinct safe standing positions above the selected floors. Capacity is measured in the workspace and checked again after generation. Complete another active template edit before opening a different workspace. `/da minigame spawns <roomId>` opens this shared editor.
2. Create a minigame preset. Select one `START` root, an ordinary room pool, and a team-spawn template pool. One weighted spawn template is selected per run and copied for every team. Ordinary rooms cannot be `START` or `TEAM_SPAWN` templates.
3. Set the total room count and spread settings. Defaults are 32 total rooms, at least 4 door transitions between spawns, and a longest/shortest route ratio no greater than 1.25. Counts include the root and all spawn rooms. Larger team counts may need more rooms and a root with more compatible doors; impossible layouts are refused after 20 bounded attempts. Distances estimate connected-room travel, not obstacles, difficulty, or loot quality.
4. Select allowed DungeonItems kits, or leave the list empty for no starting gear. Click **Save Preset** to keep reusable settings; creating, duplicating, and editing presets does not save automatically. **Set Up Match** uses the displayed settings even without saving a preset. Select players by their heads, choose **Teams**, and use **Use All Online & Balance** to replace manual assignments with balanced teams of eligible online players across every page. **Balance Selected** uses only selected players. Supports 2–8 teams with 1–4 players each; free-for-all uses teams of one. Match kit choices can override the preset's list.
5. **Prepare** validates and builds without moving players. **Start** sends players into their team rooms; it also prepares automatically if necessary. Settings and roster lock when preparation begins. Players stay frozen, choose among multiple kits for up to 10 seconds, and release together after a 5-second countdown. One kit is automatic; unselected choices become random. Missing kits prevent preparation.

Unprepared GUI setup is temporary and never appears in the run list. Closing it, returning to Runs, disconnecting, or starting another setup discards it without confirmation. Internal navigation, chat prompts, and shared marker authoring retain edits. Failed validation keeps the editor open without creating a run. Explicit command/API run creation remains available for integrations.

Players use `/dplay kit [id|random]` and `/dplay exit` without admin permission. During play, enemy-team PvP and looting are enabled; friendly fire and block editing are blocked. Death leads to spectating. Disconnects do not delay launch, and participation is held for 120 seconds. Reconnects preserve progress; players who missed initial selection get a random allowed kit. Combat logging remains the existing combat-log system's responsibility.

Original player state is saved before entry and restored on exit, cancellation, run end, or reconnect after expiry. Recovery files live under `plugins/DungeonArchitect/minigames/recovery/`; restart cancels runs and restores returning players. Presets live under `minigames/presets/`. Editing presets or kits affects future runs; each prepared run uses snapshots. This foundation has no automatic winner, scoring, or objectives: admins end runs, or they end when no participants or reconnect reservations remain.

Admin command equivalents (run UUID prefixes are accepted):

```text
/da minigame preset list
/da minigame preset create <id>
/da minigame preset duplicate <source> <newId>
/da minigame preset set <id> <root|rooms|spawns|count|distance|ratio|kits> <value>
/da minigame spawns <roomId>
/da minigame create <preset> [seed]
/da minigame assign <run> <player> <team|0>
/da minigame balance <run> <teamCount> [player1,player2,...]
/da minigame kits <run> <kit1,kit2|none>
/da minigame prepare <run>
/da minigame start <run>
/da minigame inspect <run>
/da minigame cancel <run>
/da minigame end <run>
/da minigame list
```

Pool values use comma-separated IDs; `none` clears a pool or disables kits. `assign ... 0` removes a player. `balance` without player names uses the run's selected roster. Admin actions require `dungeonarchitect.admin`; player commands use `dungeonarchitect.play` (default true).

For extensions, load `DungeonArchitectAPI` from Bukkit services and call `minigames()` on the server thread. `prepareDungeonAsync()` builds a dungeon without entry. The minigame service exposes run creation, team assignments, preparation, start/end, inspection, and elimination. `MinigamePreparedEvent`, `MinigameStartedEvent`, `MinigameEliminatedEvent`, and `MinigameEndedEvent` provide lifecycle hooks. DungeonItems exposes `kits()` and `kit(id)` for detached kit contents; update both plugin JARs together when using kits.

Room editing enters a safe authored `spawn` area, preserving your looking direction. If none is usable, it tries safe interior positions from the center outward, then explains a workspace fallback.

Generation treats **Must Connect** as a requirement for each individual doorway, separately from a room’s minimum connection count. It prefers branching growth and can join compatible unused doorways whose gateways already align, including at the exact requested room count. It does not route extra corridor bridges. Loops count toward shortest-path minigame fairness; unmet requirements fail generation before block placement. Failure messages name required sockets and distinguish search-budget exhaustion from geometry/compatibility rejects.

A preset root must reference a `START` room; team spawns must reference `TEAM_SPAWN` rooms. The preset editor shows missing rooms and category mismatches beside each selection without changing saved presets automatically.

Optional generation frontiers are selected using the seed, without penalizing rooms that already have connections. Non-start rooms can branch through all eligible sockets. Room door lists mark unavailable expansion slots and explain missing assignments or incompatible rules; clicking opens the existing slot settings. Collision feasibility remains a generation-time check.
