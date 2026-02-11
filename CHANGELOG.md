Changes:

- perf: cached built Missing Enchants tooltip blocks per `CacheKey + Shift`, so sorting/wrapping/font-width work is not repeated every render pass
- perf: cached normalized enchant tokens for `findInsertIndex(...)` instead of rebuilding them on every tooltip callback
- perf: added an identity cache for `CustomData` enchant reads to avoid running `copyTag()`/NBT parsing each frame

New Features:

- feat: added a Reminder for when your pet is done upgrading at Kat. Thanks to Pankraz01 for the contribution!