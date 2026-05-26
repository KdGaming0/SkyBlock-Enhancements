## Changelog for 1.0.12

### **RRV Enhancements**
**Improvements/Fixes**
- Improved RRV cross-mod compatibility with a defensive container facade that prevents crashes when other mods interact with the recipe viewer.
  - **Container ID Isolation**: RRV's `RecipeViewMenu` now uses unique negative container IDs, preventing server inventory packets from being misrouted to the recipe viewer and triggering mod event cascades.
  - **Slot List Padding**: After every recipe page change, the slot list is padded with inactive dummy slots up to a standard chest size (54). This prevents `IndexOutOfBoundsException` crashes from mods that assume stable inventory layouts and access slots by hardcoded indices (e.g. slot 29).
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