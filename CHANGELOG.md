## Changelog for 1.0.1

### **RRV Enhancements**
**Improvements**
- **Advanced Search** — Replaced the basic display-name-only filter with a full inverted search index.
  - Search now matches lore, item categories, pet skill types, reforge names, stats, slayer requirements, and craft text.
  - Supports **prefix matching**: typing `min` finds `mining`, `minion`, etc.
  - Supports **stat threshold queries**: `mining_speed>50`, `health<=100`, `damage=50`.
  - Multiple terms are AND-ed: `farming pets` shows only Farming skill-type pets.
  - Results are ranked: name matches appear first, followed by lore/metadata matches.
  - Works with **1-character queries**: typing `a` or `b` matches items starting with that letter.
  - **Fuzzy fallback** for typos: typing `uncommen` still finds Uncommon items.
  - Usage examples:
    - `farming pets` — find all Farming pets.
    - `fleet` — find reforge stones like Diamonite (Fleet reforge).
    - `mining speed` — find items that grant Mining Speed.
    - `mining_speed>50` — find items with a Mining Speed stat over 50.
    - `combat pet` — find all Combat pets.
    - `wolf slayer` — find items requiring Wolf Slayer.

- **Search Performance Optimizations**
  - **Category filter integration** — Category filtering is applied at the BitSet level inside the inverted index, eliminating the post-search `removeIf` scan.
  - **BitSet thread-local pool** — Intermediate BitSets are reused across searches instead of being re-allocated on every keystroke, dramatically reducing GC pressure.
  - **Double lowercasing eliminated** — The search parser skips redundant `toLowerCase()` when the mixin has already lowercased the query.
  - **Empty-query caching** — The "return all items" result is pre-computed once at index build time.

- **Autocomplete Ghost Text** — As you type, a faint completion hint appears behind the cursor.
  - Example: typing `aspect of the bo` draws `aspect of the bow` in gray behind the white text, so only the `w` peeks through.
  - Suggestions are drawn from **item display names** first, then search tokens.
  - Triggers on a **single character**.
  - Press **Tab** or **Right Arrow** (at end of text) to accept the completion.
  - **Calculator takes priority**: math expressions like `10+10` show `= 20` instead of a ghost.
