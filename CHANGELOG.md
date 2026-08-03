# Changelog

## 0.2.1 - 2026-08-03

### Added

- Client-side block-lock synchronization to stop predicted duplicate placements before they appear.
- A custom denial sound for blocked crafting and placement.

### Changed

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
