## Changelog for 0.11.2

### Improvements
- **Chat Enhancements**
  - Improved chat interaction handling, ensuring more accurate click detection on messages
  - Improved tooltip rendering when hovering clickable parts of chat messages
  - Enhanced chat copy functionality with better feedback and selection handling
  - General internal improvements to chat tabs and compact message handling

### Fixes
- **Chat Enhancements**
  - Fixed incorrect click positions on certain chat messages
  - Fixed tooltips not appearing when hovering over interactive text
  - Fixed inconsistent chat tab filtering caused by message ordering and equality issues
  - Fixed separator messages briefly appearing and disappearing
  - Fixed incorrect match count in chat search (now counts messages instead of display lines)
  - Fixed focus conflicts between chat search and chat tabs
  - Fixed Escape key behavior when search is active with an empty query
