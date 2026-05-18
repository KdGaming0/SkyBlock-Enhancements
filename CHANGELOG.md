## Changelog for 1.0.2

### **RRV Enhancements — Advanced Search Overhaul**
**Improvements**
- **Advanced Search** — Replaced the basic display-name-only filter with a full inverted search index.
  - Search now matches lore, item categories, pet skill types, reforge names, stats, slayer requirements, and craft text.
  - Supports **stat threshold queries**: `mining_speed>50`, `health<=100`, `damage=50`.
  - Multiple terms are AND-ed: `farming pets` shows only Farming skill-type pets.
  - **Fuzzy fallback** for typos: typing `uncommen` still finds Uncommon items.
  - Usage examples:
    - `farming pets` — find all Farming pets.
    - `fleet` — find reforge stones like Diamonite (Fleet reforge).
    - `mining speed` — find items that grant Mining Speed.
    - `mining_speed>50` — find items with a Mining Speed stat over 50.
    - `combat pet` — find all Combat pets.
    - `wolf slayer` — find items requiring Wolf Slayer.

- **Dynamic acronym autocomplete** — Typing the first letters of item words suggests the full name 
  - (e.g. aote → Aspect of the End, hpb → Hot Potato Book).
  - Acronyms replace the entire query on Tab / Right Arrow.

- **Autocomplete Ghost Text** — As you type, a faint completion hint appears behind the cursor.
  - Suggestions are drawn from **item display names** first, then search tokens.
  - Press **Tab** or **Right Arrow** (at end of text) to accept the completion.

### **RRV Enhancements**
**Improvements**
- Category buttons are now hidden when the item view is disabled.
