![Craft & Block Lock banner](assets/craft-block-lock-banner.png)

# Craft & Block Lock

Craft & Block Lock is a server-authoritative Fabric mod for Minecraft Java Edition 26.2. It turns ordinary progression into a challenge by limiting recipes and player-placed blocks per player.

## Rules

- Recipe locks are per player and persist across logouts and server restarts.
- A player can complete each non-exempt recipe ID only once.
- Recipes with different IDs remain separate, even when they create the same item.
- Different ingredients accepted by the same recipe ID do not provide extra uses.
- Blaze Powder and Eyes of Ender are exempt by default, so normal survival progression remains possible.
- The optional block lock allows one active player-placed block per item type.
- Breaking that tracked block unlocks its type, so the same player can place it again.
- Another player breaking the tracked block also releases the original owner's lock.
- Missing tracked blocks are reconciled when the owner next tries to place that type, covering explosions and other indirect removal.
- The server remains authoritative, while a synchronized client check prevents visual ghost placements.
- Creative mode players bypass recipe and block locks by default.

Blocked actions display a short action-bar message and play a low note-block sound by default. The sound uses Minecraft's Players sound category, so the Master Volume and Players sliders control it. Locked recipes are darkened and marked with a small barrier icon in the recipe book and result slot. Messages, sounds, and recipe visuals can be disabled independently.

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

Vanilla utility stations that do not use recipes, including anvils, enchanting tables, grindstones, looms, and cartography tables, remain freely usable by design.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Install Fabric API 0.156.0+26.2 or a compatible newer 26.2 build.
3. Put `craft-block-lock-1.0.0-rc.1.jar` in the `mods` folder on both the server and every connecting client. For single-player, install it locally as usual.
4. Start Minecraft with Java 25.

[Mod Menu](https://modrinth.com/mod/modmenu) 20.0.0 or newer is optional. When installed, it provides a settings screen for local and single-player configuration. Multiplayer server settings still require operator commands.

## Commands

Run `/cbl`, `/cbl status`, or `/cbl help` to view the current settings and commands. Configuration-changing commands require game-master permission (operator level 2).

| Command | Purpose |
|---|---|
| `/cbl help` | Show commands available to the current player |
| `/cbl craft on\|off` | Enable or disable recipe locking |
| `/cbl blocks on\|off` | Enable or disable block-placement locking |
| `/cbl creative-bypass on\|off` | Toggle the Creative mode bypass |
| `/cbl feedback messages on\|off` | Toggle blocked-action messages |
| `/cbl feedback sounds on\|off` | Toggle blocked-action sounds |
| `/cbl feedback visuals on\|off` | Toggle locked recipe shading and barrier icons |
| `/cbl reload` | Reload the JSON configuration from disk |
| `/cbl reset <player> recipes` | Ask for confirmation before clearing a player's recipe history |
| `/cbl reset <player> blocks` | Ask for confirmation before clearing a player's active block locks |
| `/cbl reset <player> all` | Ask for confirmation before clearing all locks for a player |
| `/cbl exceptions recipe list` | List recipe exceptions |
| `/cbl exceptions recipe add <recipe>` | Add an unlimited recipe |
| `/cbl exceptions recipe remove <recipe>` | Remove a recipe exception |
| `/cbl exceptions block list` | List block exceptions |
| `/cbl exceptions block add <block>` | Add an unlimited block item |
| `/cbl exceptions block remove <block>` | Remove a block exception |

Recipe and block arguments provide in-game suggestions. Use recipe identifiers such as `minecraft:ender_eye` and block item identifiers such as `minecraft:torch`.

Reset commands display a clickable confirmation that expires after 15 seconds. No progress is deleted until the reset is confirmed.

## Configuration file

The mod creates `config/craftblocklock.json` on first launch. Commands save changes to this file immediately.

```json
{
  "recipeLockEnabled": true,
  "blockLockEnabled": true,
  "creativeModeBypass": true,
  "messagesEnabled": true,
  "denialSoundsEnabled": true,
  "lockedRecipeVisualsEnabled": true,
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

The source code is available under the MIT License.
