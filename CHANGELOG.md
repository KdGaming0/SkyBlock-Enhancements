## Changelog for 1.0.0-beta.5

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
- Drag-and-drop and shift-click operations on the dashboard are not yet supported
- Custom textured sprites will be added in a future update (currently uses colored backgrounds)

### **Tooltip Enhancements**
**Added**
- `roundPriceNumbers` - Round price tooltip numbers for easier readability (default: false)

### **Chat Enhancements**
**Improvements**
- Messages that can be clicked now longer compact, can be disabled in settings.

