## Changelog for 1.0.0-beta.8

### **Tooltip Enhancements**
**Improvements**
- Price line for ah/bz will now show can't load data instead of dispersing if API data is unavailable

### **RRV Integration**
**Improvements/Optimizations**
- **Faster, Leaner Data Loading** — repo ZIP now writes to disk while downloading (no double read), and Hypixel API data streams instead of loading entire multi-MB JSON trees into memory
- **Thread-Safe Downloads** — concurrent refresh calls now share one download instead of racing and crashing; futures complete safely without double-completion bugs
- **Smarter Caching** — `getAll()` returns snapshot copies so iterating the item list won't break during reloads; NPC lookups use stable string keys instead of fragile Component objects
- **Background Task Isolation** — cache building runs on a dedicated thread instead of hogging the shared common pool, preventing lag spikes in other mods
- **Resilient Parsing** — cache loader no longer breaks if JSON field order changes; malformed API numbers are skipped instead of crashing the parser

