Changes:

- perf: cached built Missing Enchants tooltip blocks per `CacheKey + Shift`, so sorting/wrapping/font-width work is not repeated every render pass (`MissingEnchants.java:39`, `MissingEnchants.java:123`)
- perf: cached normalized enchant tokens for `findInsertIndex(...)` instead of rebuilding them on every tooltip callback (`MissingEnchants.java:49`, `MissingEnchants.java:137`)
- perf: added an identity cache for `CustomData` enchant reads to avoid running `copyTag()`/NBT parsing each frame (`MissingEnchants.java:58`, `MissingEnchants.java:180`)
- chore: extended `clearCache()` to clear all new Missing Enchants caches (`MissingEnchants.java:74`)
- docs: documented the new Missing Enchants hot-path optimizations inline for maintainability (`MissingEnchants.java:30`)
- feat: Improved remind me with sound notification
- fix: uncommon items from having a white outline instead of a green outline

New Features:

- feat: Added Enter to confirm on Hypixel input signs. You can now type prices for Bazaar or Auction House signs and press Enter to confirm. Shift + Enter still creates a new line. An option is included to apply this to all signs.
