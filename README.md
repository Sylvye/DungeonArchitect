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
- `/da feature component` is recognized but reports that feature templates have no nested editable components.
- `/da room feature <name>` adds a random feature marker at the targeted block.
- `/da room save [id]` exports `room.nbt` and `room.yml`.
- `/da room rename <oldId> <newId>` and `/da room duplicate <oldId> <newId>` manage room template ids.
- `/da room delete <id>` permanently deletes a room template.
- `/da feature create <id>`, `/da feature bounds`, `/da feature save [id]`, `/da feature rename <oldId> <newId>`, `/da feature duplicate <oldId> <newId>`, and `/da feature delete <id>` manage reusable feature templates.
- `/da door create <id>`, `/da door bounds`, `/da door gateway`, `/da door save [id]`, `/da door rename <oldId> <newId>`, `/da door duplicate <oldId> <newId>`, and `/da door delete <id>` manage reusable door templates.
- `/da gui`, `/da rooms`, `/da features`, `/da doors`, `/da config`, and `/da dungeons` open inventory GUIs.
- `/da reload` reloads templates and feature pools.
- `/da generate <roomCount> [seed]` creates an isolated dungeon world and teleports the player to the start room.
- `/da debug room`, `/da debug instance`, `/da list`, `/da teleport [instance] <roomIndex>`, `/da destroy [instance]`, and `/da exit` support debugging and dungeon management.

## Notes

- Room templates are saved under `plugins/DungeonArchitect/rooms/<id>/`.
- The first generator is deterministic, socket-based, and rejects bounding-box collisions before any blocks are placed.
- Random features are metadata-driven weighted slots configured in `feature-pools.yml`.
- If an old template was saved before size-based capture, re-save it from the original build area. `/da room inspect <id>` shows the metadata size and actual `room.nbt` size.
