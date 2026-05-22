## Changelog for 1.0.9

### **RRV Enhancements**
**Improvements/Fixes**
- Hardened RRV overlay tooltip generation against exceptions thrown by other mods during `ItemTooltipCallback`. The client no longer crashes; the error is logged and the tooltip falls back to the item's display name.