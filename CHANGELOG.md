## Changelog for 1.0.8

### **RRV Enhancements**
**Improvements/Fixes**
- Fixed an `IndexOutOfBoundsException` that could occur with RRV’s Recipe View when other mods inspect container slots (e.g. Catharsis/FurfSky GUI matching).
  - RRV’s recipe menu can temporarily rebuild its slot list when switching pages, and some mods assume certain slot indices always exist. We now guard against out-of-range slot queries to prevent the crash (workaround until RRV changes its menu implementation).
- Changed the craft button from being a large button to just a small "+" icon beside the output field. You can change it back in the options if you prefer the large button.
- Added missing language entries for the "showCollectionRequirements".