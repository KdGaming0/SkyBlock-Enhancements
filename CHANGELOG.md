## Changelog for 1.0.12

### **RRV Enhancements**
**Improvements/Fixes**
- Improved RRV cross-mod compatibility with a defensive container facade that prevents crashes when other mods interact with the recipe viewer.
  - **Container ID Isolation**: RRV's `RecipeViewMenu` now uses unique negative container IDs via `@ModifyArg` on the constructor's `super()` call. Servers never emit negative container IDs, so no server packet targets the recipe viewer directly.
  - **Slot List Padding**: Before `updateReferences()` runs inside `updateByPage()`, the slot list is padded with inactive dummy slots up to a standard chest size (54). This prevents `IndexOutOfBoundsException` crashes from mods that assume stable inventory layouts and access slots by hardcoded indices (e.g. slot 29).
  - **Packet Suppression for Parent Menu**: While the recipe viewer is open, `initializeContents` and `setItem` calls for the active parent container menu are silently dropped. This prevents mod event cascades from firing on the parent menu while RRV is on screen.
  - **Allocation reduction**: `SbeSafeDummySlot` now shares a single `SimpleContainer` instance across all dummy slots, reducing GC pressure during slot rebuilds.
- Improved Advanced Item Search functionality, allowing for more accurate and efficient searches.
  - Quick Use Guide:
    - *Keywords*: `zombie slayer` finds items matching all words (AND logic).
    - *Stat thresholds*: `mining_speed>50`, `health<=100`, `damage=50`. You can also use short aliases like `ms>50` (mining speed), `cc>=10` (crit chance), `cd` (crit damage), `hp`, `def`, `str`, `speed` (walk speed), etc.
    - *Rarity*: `rarity:legendary` or `r:mythic`.
    - *Item type*: `type:sword`, `type:helmet`, `t:bow`.
    - *Slayer requirements*: `slayer:zombie>3` (Zombie Slayer 4+), `slayer:eman`, `sl>5`.
    - *Skill requirements*: `skill:combat>20`, `sk:farming>10`.
    - *Catacombs*: `catacombs>10`, `cata>=15`.
    - *Boolean flags*: `soulbound`, `dungeon`, `rift`.
- Fixed mob drop recipes not showing more than 12 drops, even though the mob has more drops.

### **Skyblock Enhancements**
**Additions**
- Added **Potion Effect Status Overlay** for the Hypixel "Toggle Potion Effects" GUI.
  - Disabled effects are tinted in a configurable red overlay; enabled effects are tinted in configurable green.