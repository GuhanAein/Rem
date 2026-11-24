# Rem Roadmap

## Done (v0.1)
- Reminders: plain-words quick add, every repeat type, several alerts, snooze, alarm-style ringing with "keep reminding"
- Voice input that understands spoken times
- Home-screen widgets (Today, Quick Add) and launcher shortcuts
- Reliability: exact alarms, reboot recovery, a setup wizard and battery tips for each phone brand
- Rem Noir premium design, fonts, motion and haptics
- 79 automated tests, CI and an APK build

## Done (v0.2)
Smarter reminders, context triggers, life admin, people, motivation, premium touches and trust features (see the README).

**Known limits:**
- Wired-headphone triggers only work while Rem is running, because Android restricts that signal.
- "Any pharmacy or supermarket" needs a places database, so place triggers use places you save yourself.
- Accountability and "remind them" go through your own messaging apps; there is no server yet.

## Next

### v0.3: Ship-ready (recommended next)
Make it something you can confidently put on the Play Store.
- **Real-device testing:** alarms on Xiaomi, Samsung and OnePlus; voice; widgets; lock screen
- **Search** across all reminders
- **Subtasks / checklists** inside a reminder
- **Import** from TickTick, Todoist and Google Tasks, plus Google Drive backup
- **Completed history** with restore
- **Accessibility:** large text and TalkBack
- **Play Store kit:** release signing, privacy policy (no data collected), store screenshots and listing text

### v0.3: Remind everything
The life features from the catalog.
- **Location reminders:** "when I reach the supermarket" or "when I leave the office"
- **Birthdays** imported from contacts
- **Bills:** amount, mark as paid, monthly total
- **Medicine:** dose log (taken or skipped) and refill alerts
- **Document expiry tracker** (passport, licence, insurance, warranty)
- **Scan a bill or poster** with the camera; text recognition runs on the phone (ML Kit)
- **Hindi and Tamil** voice and typing

### v0.4: Habits & insight
- Habits with streaks and a calendar heat-map
- Focus timer linked to a reminder
- Weekly review and a "reschedule all overdue" helper
- Morning briefing notification
- Stats: completion rate and missed reminders

### v0.5: Together (needs a small server)
- Optional account with end-to-end encrypted sync across devices
- Shared lists (family groceries, chores)
- **Remind someone else**, who taps "Got it"
- **Caregiver alerts:** notify a family member if a medicine dose is missed

### v1.0: Launch
- Wear OS app, Google Calendar sync, polish, Play Store launch
