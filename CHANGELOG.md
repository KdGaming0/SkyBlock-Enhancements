## Reminder GUI
A new interactive screen to manage all your reminders without touching the chat console.
* **Dual-Tab Interface:** Easily switch between your active reminder list and a visual "Create" form.
* **Visual Management:** Each entry shows a live countdown, name, and status badge (Real Time vs. Play Time).
* **Quick Actions:** Reset, Edit, Pause, and Delete buttons are available directly on each row for instant control.

**Custom reminder names**
You can now give reminders a label that's separate from the message, making the list easier to scan.
`/remindme create 1 hour real_time chat name KatTimer Check your Kat pet!`

**Rename reminders**
Change the name of an existing reminder without deleting and recreating it.
`/remindme rename <id> <new name>`

**Pause & resume**
Toggle a reminder on or off directly from the list, or via command. While-playing reminders freeze their countdown when paused.
`/remindme toggle <id>`

**Snooze**
Delay a reminder's next fire time by any amount.
`/remindme snooze <id> 10 minutes`

**Sound-only output**
A new output type that plays the alert sound with no chat or title message.
`/remindme create 30 minutes real_time sound_only Ping`

**Remove-all confirmation**
`/remindme remove all` now shows a `[confirm]` button before deleting anything.

## Improvements

- **Late Notifications:** If a real-time reminder fires while you are offline or lagging, the chat message notes exactly how late it was (e.g., "fired 12m late").

## New Feature

**Fullbright**
- Added a dedicated Key Binding to toggle Fullbright on and off.
- Integrated Gamma Adjustment settings to fine-tune brightness levels.