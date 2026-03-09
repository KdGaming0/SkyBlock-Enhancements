Changes:

**Custom reminder names**
You can now give reminders a label that's separate from the message, making the list easier to scan.
`/remindme create 1 hour real_time chat name KatTimer Check your Kat pet!`

**Rename reminders**
Change the name of an existing reminder without deleting and recreating it.
`/remindme rename <id> <new name>`

**Pause & resume**
Freeze a while-playing reminder's countdown and pick it back up later.
`/remindme pause <id>` / `/remindme resume <id>`

**Snooze**
Delay a reminder directly from the command or by clicking `[+5m]` / `[+1h]` buttons that appear in chat when it fires.
`/remindme snooze <id> 10 minutes`

**Sound-only output**
A new output type that plays the alert sound with no chat or title message.
`/remindme create 30 minutes real_time sound_only Ping`

**Remove-all confirmation**
`/remindme remove all` now shows a `[confirm]` button before deleting anything.

## Improvements

- The reminder list is now sorted by time remaining (soonest first), with paused reminders at the bottom.
- Repeating reminders show progress in the list — e.g. `3/5` or `∞`.
- Each list entry has clickable `[pause]` and `[remove]` buttons.
- If a real-time reminder fires late (e.g. you were offline), the message notes how late it was.