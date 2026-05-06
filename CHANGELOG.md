## Changelog for 1.0.0-beta.8

### **Storage Enhancements**
**Added**

- **Storage Dashboard Overlay** - A comprehensive UI overlay for your storage screen that displays all your items at a glance:
  - Automatic snapshot capture of Storage, Ender Chest, and Backpack contents whenever you open these screens
  - Persistent item history with configurable retention (default: 10 pages)
  - Smart search and filtering by item display name with visual highlighting
  - Automatic backups and cleanup of old snapshots
  - Per-profile storage data (supports multiple SkyBlock profiles)
  - Clean, organized mini-grid display of your inventory contents

**Features:**
- **Auto-capture**: Snapshots are automatically taken each time you view your storage/ender chest/backpack
- **Persistent Storage**: All snapshots are saved locally and survive game restarts
- **Search Functionality**: Quickly find items by typing in the search bar (matches highlighted in gold)
- **Profile Support**: Data is stored separately for each of your SkyBlock profiles
- **Easy Management**: New command `/sbe storage clear-cache` to manage your storage snapshots

**Configuration:**
New config options added:
- `enableStorageDashboard` - Toggle the storage overlay on/off (default: true)
- `persistStorageSnapshots` - Enable/disable snapshot persistence (default: true)
- `storageSnapshotHistoryPages` - Number of snapshot history pages to keep (default: 10)

**Known Limitations:**
- Custom textured sprites will be added in a future update (currently uses colored backgrounds)

### **Tooltip Enhancements**
**Fixed**

### **RRV Integration**
**Improvements/Optimizations**
- **Faster, Leaner Data Loading** — repo ZIP now writes to disk while downloading (no double read), and Hypixel API data streams instead of loading entire multi-MB JSON trees into memory
- **Thread-Safe Downloads** — concurrent refresh calls now share one download instead of racing and crashing; futures complete safely without double-completion bugs
- **Smarter Caching** — `getAll()` returns snapshot copies so iterating the item list won't break during reloads; NPC lookups use stable string keys instead of fragile Component objects
- **Background Task Isolation** — cache building runs on a dedicated thread instead of hogging the shared common pool, preventing lag spikes in other mods
- **Resilient Parsing** — cache loader no longer breaks if JSON field order changes; malformed API numbers are skipped instead of crashing the parser

