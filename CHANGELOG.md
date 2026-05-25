## Changelog for 1.0.11

### **RRV Enhancements**
**Improvements/Fixes**
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