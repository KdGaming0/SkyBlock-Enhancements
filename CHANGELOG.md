## Changelog for 0.10.0

### Added
- **Reliable Recipe Viewer (RRV) Integration**: Full support for viewing SkyBlock item recipes directly in-game via the RRV mod.
  - **SkyBlock Crafting**: Displays 3×3 crafting grid recipes parsed from the NEU repository, including both legacy and modern recipe formats.
  - **SkyBlock Forge**: Displays forge recipes with ingredient slots, output, and a formatted duration label (e.g. "2h 30m", "45m", "10s").
  - **SkyBlock NPC Shop**: Displays NPC shop recipes with up to 5 cost items and a result slot. Includes an "NPC Info" button that navigates to the NPC's info card.
  - **SkyBlock NPC Info**: Visual info cards for every NPC, showing their head, island location, coordinates, and lore. Includes a "⬈ Navigate" button for SkyHanni users.
  - **SkyBlock Mob Drops**: Displays mob drop tables in a 4×3 grid with per-item drop chance tooltips and the mob's name as a header.
  - **SkyBlock Trade**: Displays simple 1:1 trade recipes.
  - **Kat Pet Upgrade**: Displays Kat pet upgrade recipes showing the input pet, up to 4 material slots, coin cost, upgrade time, and the resulting pet.
  - **SkyBlock Wiki**: Fallback card for items with wiki URLs but no other recipe data — keeps every item clickable in the recipe viewer.
  - **Search Calculator**: Evaluate math expressions directly in the RRV search bar with real-time "ghost text" results.
    - Supports basic operators (+, -, *, /, %), exponentiation (^), and parentheses.
    - SkyBlock Suffixes: Understands magnitude abbreviations (k, m, b, t) and the stack suffix (st) for bulk calculations (e.g., 1st * 25k).
    - Advanced Math: Support for scientific notation (e.g., 1.5e6) and functions like sqrt(), abs(), floor(), ceil(), and round().
  - All recipe types include an optional **Wiki** button that opens the item's page on the SkyBlock wiki.
  - Category filtering decides to only search and see items for a specific category, like armor or weapons.
  - Recipe data is sourced live from the **NEU repository**, downloaded and cached on first launch and refreshed automatically when the repo updates.
  - Cache is stored locally and checked against the latest GitHub commit SHA to avoid unnecessary re-downloads.
  - Use `/skyblockenhancements refresh repoData` to manually force a cache refresh.


- **Compact Duplicate Messages**: Repeated chat messages are merged into a single line with an occurrence counter (×N).
  - Option to only compact consecutive duplicates.
- **Centered Hypixel Text**: Space-padded Hypixel messages are now properly centered in the chat window.
- **Smooth Separators**: Dash/line separator characters are replaced with clean horizontal lines, with support for centered separator text.
- **Chat Tabs**: Hypixel channel tab buttons (All, Party, Guild, PM, Co-op) above the chat input for quick channel switching. (Button textures were made by [Bentcheesee](https://modrinth.com/user/Bentcheesee). Huge thanks!)
- **Extended Chat History**: Chat history limit is now configurable up to 2048 messages (default: 1024).
- **Chat Animation**: Smooth slide-up animation for new messages and the chat input bar.
- New "Chat Enhancements" category in the config menu for all chat-related settings.
- Prevent weapons like Spirit Sceptre from being placed.

### Changed
- Horizontal scroll when Shift is pressed is on by default, now off by default.
- UI Lib not being listed as a dependency on Modrinth.