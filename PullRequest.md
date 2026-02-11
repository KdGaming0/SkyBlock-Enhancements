# Suggested PR Title
perf: optimize MissingEnchants tooltip hot path

# Pull Request Description
## Summary
`MissingEnchants.onTooltip(...)` is executed on tooltip render while hovering an item, so it can run very frequently.  
This PR reduces per-render overhead by caching stable computed data, without intentionally changing feature behavior.

## Changes
- Added a cache for fully built tooltip blocks keyed by `CacheKey + shift state`, so sorting, wrapping, and font-width calculation are not repeated every render.
- Added a cache for normalized enchant tokens used by `findInsertIndex(...)`.
- Added an identity cache for `CustomData -> enchant list` to avoid repeated `copyTag()`/NBT parsing in the hot path.
- Updated `clearCache()` to clear all new Missing Enchants caches.
- Added inline comments explaining the hot-path optimizations.
- Updated `CHANGELOG.md` with detailed performance entries and line references.

## Behavior / Compatibility
- No intentional user-facing behavior changes.
- Tooltip content and insertion behavior remain the same.

## Validation
- Build passes with:
```powershell
.\gradlew.bat build
```

## Notes
- Version bump is not included in this PR.
