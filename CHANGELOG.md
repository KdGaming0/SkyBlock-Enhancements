## Changelog for 0.11.6
**Improvements**
- **Categories**
  - Added new categories: Enchantment, Minion, Potion, and Misc
  - Added search bar category filtering — type `%CATEGORY` (e.g. `%PET`) or
    `%CATEGORY/SUBCATEGORY` (e.g. `%ARMOR/DUNGEONEERING`) to filter by category
  - Fixed missing entries in Accessory, Equipment, and Material categories

  <details>
  <summary>📋 Category cheat sheet</summary>

  | Category | Search prefix | Button |
    |---|---|---|
  | Armor | `%ARMOR` | ✅ |
  | Weapon | `%WEAPON` | ✅ |
  | Tool | `%TOOL` | ✅ |
  | Accessory | `%ACCESSORY` | ✅ |
  | Pet | `%PET` | ✅ |
  | Enchantment | `%ENCHANTMENT` | ✅ |
  | Minion | `%MINION` | ✅ |
  | Equipment | `%EQUIPMENT` | ✅ |
  | Material | `%MATERIAL` | ✅ |
  | Potion | `%POTION` | ❌ search only |
  | Cosmetic | `%COSMETIC` | ❌ search only |
  | NPC | `%NPC` | ✅ |
  | Misc | `%MISC` | ❌ search only |

  Sub-categories work for **Armor** (`%ARMOR/COMBAT`, `%ARMOR/FARMING` …) and **Pet** (`%PET/COMBAT`, `%PET/MINING` …).

  </details>

- **Item List**
  - Added compact mode (on by default) — tiered items like minions collapse into a single
    entry (e.g. *Creeper Minion I–XI*). Can be toggled in settings
  - Tiered items are now sorted by tier instead of appearing in random order
- **Recipe Viewer**
  - Crafting recipes now appear before drops — tab order: Crafting → Forge → NPC Shop →
    Trade → Essence Upgrade → Kat Upgrade → Drops → Wiki
  - Added Essence Upgrade recipes — 3,500+ star upgrade recipes across 528 items and 9
    essence types (Wither, Dragon, Crimson, and more)

**Fixes**
- Fixed enhanced items in the item list not having enhanced glint
- Item list not being scrollable with the mouse wheel