## Changelog for 1.0.11

### **RRV Enhancements**
**Improvements/Fixes**
- Added option to display full raw numbers in search bar calculator instead of abbreviated K/M/B/T suffixes
- Search calculator now applies suffixes only to "clean" multiples to preserve accuracy (e.g., 10000 → 10K, but 10001 → 10001)
- Added an option to automatically hide the side panel when all bookmarks are removed.
- Added an option to change the left-side panel width, like what's already possible for the right side.

### **Skyblock Enhancements**
**Additions**
- Added Pickaxe Ability ready on-screen notifications.
- Added Ping Offset Mining overlay — visually indicates the exact moment you should switch to the next block while mining, factoring in both server ping and TPS.
  - In high-ping scenarios (such as playing Hypixel from outside the US), there's a significant delay between when you actually break a block server-side and when your client receives the confirmation. Without this overlay, you may keep mining a block that the server has already considered broken, losing valuable time and reducing your efficiency.
  - This overlay continuously calculates the true server-side break time using your mining speed, real-time ping, and current server TPS. It renders a colored progress bar or highlight directly on the target block, signaling the precise tick when the server will register the break, so you can optimally switch to the next block before the client’s usual confirmation arrives.
  - Heavily inspired by Ping Offset Miner by xevalia, but rebuilt from the ground up for this mod.