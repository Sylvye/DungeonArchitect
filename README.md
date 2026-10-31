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
- `/da room create <id>` starts a room session.
- Left/right click blocks with the wand to set the current selection.
- Hold the selector to outline doors, markers, and feature slots; right click to select the first component in view.
- `/da room bounds` saves the current selection as the room bounds.
- `/da room door` saves the current selection as a door region using defaults.
- `/da room door [id] [socketType] [facing]` adds a socket with explicit overrides.
- `/da room marker add <name> [type]` adds a generic marker at the targeted block.
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

## Minigame launches

Open `/da` → **Minigames**, or use `/da minigame` (`/dminigame`, alias `/dmg`).

1. Author a `TEAM_SPAWN` room using the room category editor. While editing its world copy, stand at each desired player position and run `/da room marker add <name> player_spawn`, or use **Add Player Spawn Here** in the room GUI. Add one distinct marker for each supported teammate, then save the room. Markers represent players' feet and need a solid floor and clear headroom inside the room bounds.
2. Create a minigame preset. Select one `START` root, an ordinary room pool, and a team-spawn template pool. One weighted spawn template is selected per run and copied for every team. Ordinary rooms cannot be `START` or `TEAM_SPAWN` templates.
3. Set the total room count and spread settings. Defaults are 32 total rooms, at least 4 door transitions between spawns, and a longest/shortest route ratio no greater than 1.25. Counts include the root and all spawn rooms. Larger team counts may need more rooms and a root with more compatible doors; impossible layouts are refused after 20 bounded attempts. Distances estimate connected-room travel, not obstacles, difficulty, or loot quality.
4. Select allowed DungeonItems kits, or leave the list empty for no starting gear. Create a run, select its players, then assign teams manually or randomly balance the selected roster. Supports 2–8 teams with 1–4 players each; free-for-all uses teams of one. The run can override the preset's kit list.
5. **Prepare** validates and builds without moving players. **Start** sends players into their team rooms; it also prepares automatically if necessary. Settings and roster lock when preparation begins. Players stay frozen, choose among multiple kits for up to 10 seconds, and release together after a 5-second countdown. One kit is automatic; unselected choices become random. Missing kits prevent preparation.

Players use `/dplay kit [id|random]` and `/dplay exit` without admin permission. During play, enemy-team PvP and looting are enabled; friendly fire and block editing are blocked. Death leads to spectating. Disconnects do not delay launch, and participation is held for 120 seconds. Reconnects preserve progress; players who missed initial selection get a random allowed kit. Combat logging remains the existing combat-log system's responsibility.

Original player state is saved before entry and restored on exit, cancellation, run end, or reconnect after expiry. Recovery files live under `plugins/DungeonArchitect/minigames/recovery/`; restart cancels runs and restores returning players. Presets live under `minigames/presets/`. Editing presets or kits affects future runs; each prepared run uses snapshots. This foundation has no automatic winner, scoring, or objectives: admins end runs, or they end when no participants or reconnect reservations remain.

Admin command equivalents (run UUID prefixes are accepted):

```text
/da minigame preset list
/da minigame preset create <id>
/da minigame preset duplicate <source> <newId>
/da minigame preset set <id> <root|rooms|spawns|count|distance|ratio|kits> <value>
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
