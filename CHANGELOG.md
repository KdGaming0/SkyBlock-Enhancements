## Changelog for 1.0.0-beta.5

### **Tooltip Enhancements**
**Added**
- Added an option to display the Bazaar spread price in item tooltips, along with toggles to independently show or hide the buy/sell and spread values.

**Fixes**
- Fixed tooltip scroll position resetting when a mod injects dynamic/updating tooltip lines (e.g., live timers, counters, or fluctuating stats). Scroll offsets now persist until you hover over a new item.

### **Chat Enhancements**
**Added**
- Added a new "User" chat tab that filters specifically for player-sent messages.

**Fixes**
- Fixed `IndexOutOfBoundsException` crash during chat render when `trimmedMessages` is empty but `chatScrollbarPos` holds a stale index (e.g. after compact chat refresh, tab/search filter, or vanilla clear). Scroll position is now clamped to valid bounds before every render and after every list rebuild/clear.