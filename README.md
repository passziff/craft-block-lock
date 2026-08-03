# Craft & Block Lock

Craft & Block Lock is a server-authoritative Fabric mod for Minecraft Java Edition 26.2. Every recipe or supported crafting transformation can be completed once per player. It also includes an optional per-player block-placement lock.

## Rules

- Recipe locks are per player and persist across logouts and server restarts.
- A player can complete each recipe only once.
- The block lock allows one active player-placed block per item type.
- Breaking that tracked block unlocks its type, so the same player can place it again.
- Another player breaking the tracked block also releases the original owner's lock.
- Missing tracked blocks are reconciled when the owner next tries to place that type, covering explosions and other indirect removal.
- All enforcement is server-side. Every connecting player follows the same rules.

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

Vanilla stations that do not use recipes, such as anvils, enchanting tables, grindstones, looms, and cartography tables, are not recipe-locked.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Install Fabric API 0.156.0+26.2 or a compatible newer 26.2 build.
3. Put `craft-block-lock-0.1.0.jar` in the `mods` folder on the server. Install it on clients as well when playing single-player or opening a world to LAN.
4. Start Minecraft with Java 25.

## Configuration

The mod creates `config/craftblocklock.json` on first launch:

```json
{
  "recipeLockEnabled": true,
  "blockLockEnabled": true
}
```

Change either option while the game or server is stopped, then restart it.

## Build from source

Java 25 and Gradle 9.5 are required. The included Gradle wrapper can build the project:

```bash
./gradlew build
```

The installable JAR is written to `build/libs/`.

## License

MIT
