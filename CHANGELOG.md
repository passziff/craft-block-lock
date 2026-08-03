# Changelog

## 0.9.0 - 2026-08-03

### Added

- A 128 by 128 mod icon for Mod Menu and launchers that display Fabric metadata icons.
- `/cbl blocks list [page]` for viewing your own active block locks.
- `/cbl recipes list [page]` for viewing your own locked recipe IDs.
- Recipe and block exception management in the Mod Menu settings screen.
- Recipe and block screenshots in the README.
- Creative mode bypass for recipe and block locks.
- `/cbl help` with a shorter list for regular players and the full operator command list for operators.
- A 15-second clickable confirmation for all reset commands.
- Optional Mod Menu 20 configuration for local and single-player settings.

### Fixed

- Active locks now transfer when a tracked block changes type, such as dirt becoming grass.
- World changes are reconciled before stale client feedback can affect the next placement.
- Multiple transformed placements of the same resulting block type remain tracked correctly.
- Falling blocks keep their lock while moving, transfer it to the landing position and release it if the entity is destroyed.
- Piston pushes and pulls transfer tracked placements, including slime and honey chains; piston-destroyed blocks are released.
- The locked-block README screenshot now shows the complete message and HUD.
- All equivalent potion bottles produced by one brewing cycle can be collected before that transformation is locked.
- Falling-block landing transfers are limited to the block type actually carried by the tracked entity.

### Changed

- Locked recipe buttons now play only the denial sound instead of the normal click and denial sounds together.
- Block-lock feedback now uses a shorter message.
- The displayed author name is now passo.
- The README is shorter, clearer and includes the current multiplayer testing status.
- Block tracking now reacts to actual world changes instead of scanning every placement every server tick.
- A capped background reconciliation pass remains as a low-cost safety net for unusual or modded changes.
- Recipe locking is documented as one use per Minecraft recipe ID.
- Denial sound behavior is documented under Minecraft's Players sound category.
- GitHub artifact uploads now use `actions/upload-artifact@v7`.
- Build metadata and documentation now identify this as the complete single-player-tested 0.9.0 release; multiplayer remains untested until 1.0.0.
- Gradle task configuration no longer uses the two deprecated task-time project lookups.
- The optional Mod Menu dependency uses the shared version property consistently.

## 0.2.1 - 2026-08-03

### Added

- Client-side block-lock synchronization to stop predicted duplicate placements before they appear.
- Client-side result checks that stop locked output clicks before the item reaches the cursor.
- Darkened locked recipes with a small barrier icon in the recipe book and result slots.
- An `Already crafted - recipe locked` tooltip for marked recipes and results.
- A command and configuration setting for turning locked recipe visuals on or off.

### Changed

- Blocked actions now use Minecraft's built-in note-block bass sound.
- Utility stations such as anvils and enchanting tables remain freely usable by design.
- Resetting a player's block locks now updates that player's client immediately.

## 0.2.0 - 2026-08-03

### Added

- Operator commands for changing lock settings without restarting the server.
- Recipe and block exception lists with in-game add, remove, and list commands.
- Blaze Powder and Eyes of Ender as default recipe exceptions.
- Optional denial sounds and configurable action-bar messages.
- Per-player reset commands for recipe history and active block locks.
- Repository banner and expanded configuration documentation.

### Fixed

- Resynchronized the player's inventory immediately after a blocked placement.
- Allowed exempt furnace recipes to produce normal stackable output.
