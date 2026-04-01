## Changelog for 0.10.0

### Added
- **Compact Duplicate Messages**: Repeated chat messages are merged into a single line with an occurrence counter (×N).
    - Option to only compact consecutive duplicates.
- **Centered Hypixel Text**: Space-padded Hypixel messages are now properly centered in the chat window.
- **Smooth Separators**: Dash/line separator characters are replaced with clean horizontal lines, with support for centered separator text.
- **Chat Tabs**: Hypixel channel tab buttons (All, Party, Guild, PM, Co-op) above the chat input for quick channel switching.
- **Extended Chat History**: Chat history limit is now configurable up to 2048 messages (default: 1024).
- **Chat Animation**: Smooth slide-up animation for new messages and the chat input bar.
- New "Chat Enhancements" category in the config menu for all chat-related settings. 
- Prevent weapons like Spirit Sceptre from being placed.

### Changed
- Horizontal scroll when Shift is pressed is on by default.
- UI Lib not being listed as a dependency on Modrinth.