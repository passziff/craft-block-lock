![Craft & Block Lock banner](assets/craft-block-lock-banner.png)

# Craft & Block Lock

Craft & Block Lock is a server-authoritative Fabric mod for Minecraft Java Edition 26.2. It turns ordinary progression into a challenge by limiting recipes and player-placed blocks per player.

## Rules

- Recipe locks are per player and persist across logouts and server restarts.
- A player can complete each non-exempt recipe only once.
- Blaze Powder and Eyes of Ender are exempt by default, so normal survival progression remains possible.
- The optional block lock allows one active player-placed block per item type.
- Breaking that tracked block unlocks its type, so the same player can place it again.
- Another player breaking the tracked block also releases the original owner's lock.
- Missing tracked blocks are reconciled when the owner next tries to place that type, covering explosions and other indirect removal.
- All enforcement is server-side. Every connecting player follows the same rules.

Blocked actions display a short action-bar message and play a denial sound by default. Both forms of feedback can be disabled independently.

## Supported crafting paths

- Player inventory 2×2 crafting grid
- Crafting table
- Furnace
- Blast furnace
- Smoker
- Stonecutter
- Smithing table
- Brewing stand transformations
- Crafter outputs
- Campfire and soul campfire cooking
- Modded recipes that use the corresponding vanilla recipe and output paths

Recipe-backed stations are keyed by recipe identifier. Brewing transformations use a stable key derived from the input item and potion, ingredient, and output item and potion. Automated and delayed outputs carry a hidden, unique provenance marker until a player acquires them, preventing separate operations from merging to bypass the lock.

Vanilla stations that do not use recipes—anvils, enchanting tables, grindstones, looms, and cartography tables—are not locked in version 0.2.0. They require action-specific keys and are planned as a separate update so their rules can be tested independently.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Install Fabric API 0.156.0+26.2 or a compatible newer 26.2 build.
3. Put `craft-block-lock-0.2.0.jar` in the server's `mods` folder. Install it locally as well for single-player or LAN worlds.
4. Start Minecraft with Java 25.

## Commands

Run `/cbl` or `/cbl status` to view the current settings. Configuration-changing commands require game-master permission (operator level 2).

| Command | Purpose |
|---|---|
| `/cbl craft on\|off` | Enable or disable recipe locking |
| `/cbl blocks on\|off` | Enable or disable block-placement locking |
| `/cbl feedback messages on\|off` | Toggle blocked-action messages |
| `/cbl feedback sounds on\|off` | Toggle blocked-action sounds |
| `/cbl reload` | Reload the JSON configuration from disk |
| `/cbl reset <player> recipes` | Clear a player's recipe history |
| `/cbl reset <player> blocks` | Clear a player's active block locks without breaking the blocks |
| `/cbl reset <player> all` | Clear all locks for a player |
| `/cbl exceptions recipe list` | List recipe exceptions |
| `/cbl exceptions recipe add <recipe>` | Add an unlimited recipe |
| `/cbl exceptions recipe remove <recipe>` | Remove a recipe exception |
| `/cbl exceptions block list` | List block exceptions |
| `/cbl exceptions block add <block>` | Add an unlimited block item |
| `/cbl exceptions block remove <block>` | Remove a block exception |

Recipe and block arguments provide in-game suggestions. Use recipe identifiers such as `minecraft:ender_eye` and block item identifiers such as `minecraft:torch`.

## Configuration file

The mod creates `config/craftblocklock.json` on first launch. Commands save changes to this file immediately.

```json
{
  "recipeLockEnabled": true,
  "blockLockEnabled": true,
  "messagesEnabled": true,
  "denialSoundsEnabled": true,
  "recipeExceptions": [
    "minecraft:blaze_powder",
    "minecraft:ender_eye"
  ],
  "blockExceptions": []
}
```

The file can also be edited manually. Run `/cbl reload` afterward, or edit it while the server is stopped.

## Build from source

Java 25 and Gradle 9.5 are required. The included Gradle wrapper can build the project:

```bash
./gradlew build
```

The installable JAR is written to `build/libs/`.

## License

MIT
