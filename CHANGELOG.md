## Changelog for 1.0.1

### **RRV Enhancements**
**Improvements**
- **Advanced Search** — Replaced the basic display-name-only filter with a full inverted search index.
  - Search now matches lore, item categories, pet skill types, reforge names, stats, slayer requirements, and craft text.
  - Supports **prefix matching**: typing `min` finds `mining`, `minion`, etc.
  - Supports **stat threshold queries**: `mining_speed>50`, `health<=100`, `damage=50`.
  - Multiple terms are AND-ed: `farming pets` shows only Farming skill-type pets.
  - Results are ranked: name matches appear first, followed by lore/metadata matches.
  - Usage examples:
    - `farming pets` — find all Farming pets.
    - `fleet` — find reforge stones like Diamonite (Fleet reforge).
    - `mining speed` — find items that grant Mining Speed.
    - `mining_speed>50` — find items with a Mining Speed stat over 50.
    - `combat pet` — find all Combat pets.
    - `wolf slayer` — find items requiring Wolf Slayer.
- 