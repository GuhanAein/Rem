# Rem — Product Spec

**One-liner:** The reminder app you *can't* miss: the organisation of TickTick, the natural language of Todoist, and the persistence of an alarm app, all private and offline-first.

## Core principle: no AI APIs
Rem uses **no AI or LLM APIs**. Every "smart" feature is built from rules that run on the phone:
- Quick add uses a rule-based natural-language parser (pattern matching plus date words), written in plain Kotlin.
- Voice uses Android's built-in `SpeechRecognizer`; photo/screenshot text uses on-device ML Kit Text Recognition (free, offline, no key).
- Suggestions and rescheduling use keyword rules, the user's own history, and a free-slot-filling algorithm.
- v0.1–v0.4 need **no server at all**. Free non-AI services are added only where the data must come from outside: Open-Meteo for weather, OSRM/OpenRouteService for traffic, and Firebase/Supabase free tiers for sync in v0.5.

## Target users
- People who forget things even when they have reminders set (they need persistence).
- Students and professionals juggling tasks, deadlines and habits.
- Families who need to remind each other (medicine, bills, pick-ups).

## Differentiators
1. **Escalating reminders.** Notification → heads-up → full-screen alarm → repeat until you mark it done, snooze it, or dismiss it. You choose how hard each reminder pushes.
2. **Reliability engine.** Uses exact alarms plus a foreground-service fallback, walks the user through OEM battery settings (Xiaomi, Samsung, OnePlus and so on), and shows a "missed reminder" audit.
3. **Smart overdue handling.** One-tap "reschedule all overdue" with suggested slots.
4. **Remind others.** Send a reminder to a contact, who must acknowledge it (like Galarm).
5. **Privacy first.** Works fully offline with no account. Cloud sync is optional and end-to-end encrypted.
6. **Generous free tier.** Multiple reminders, location reminders and recurrence are free.

## Full feature list

### Core reminders (MVP)
- [ ] Quick add with natural-language parsing ("call mom tomorrow 6pm", "gym every Mon Wed Fri 7am")
- [ ] One-time, recurring and custom-recurrence reminders (every N days/weeks, Nth weekday of the month, last day of the month, workdays only)
- [ ] Multiple reminders per item (for example a day before, 1 hour before, and at the time)
- [ ] Snooze presets (5m, 15m, 1h, tonight, tomorrow morning, custom)
- [ ] Notification actions: Done, Snooze, Reschedule
- [ ] Alarm-style full-screen reminders for important items
- [ ] Persistent "nag" mode that repeats every X minutes until done
- [ ] Per-reminder sound, vibration and priority
- [ ] Reboot-safe rescheduling and the battery-optimisation setup wizard

### Organisation
- [ ] Lists/projects, tags, priorities (P1–P4)
- [ ] Subtasks and checklists, notes and attachments (images, voice notes)
- [ ] Smart views: Today, Upcoming, Overdue, Someday, Completed
- [ ] Search and filters
- [ ] Calendar view (day/week/month) with drag to reschedule

### Context-aware reminders
- [ ] Location reminders (arrive at or leave a place) using geofences
- [ ] Wi-Fi/Bluetooth triggers ("when I connect to car Bluetooth")
- [ ] Time-window reminders ("sometime this afternoon")

### Life features
- [ ] Birthdays and anniversaries (import from contacts)
- [ ] Bill and subscription reminders with amounts
- [ ] Medicine schedules with a dose log
- [ ] Habit tracker with streaks
- [ ] Focus/Pomodoro timer linked to tasks

### Social
- [ ] Shared lists
- [ ] Send a reminder to a contact, with acknowledgement and an escalation if it's ignored
- [ ] Assign tasks to family members

### Input & integrations
- [ ] Voice add (speech-to-text plus the NLP parser)
- [ ] Share-sheet: turn any text or link into a reminder
- [ ] Google Calendar two-way sync
- [ ] Home-screen widgets (list, quick add, today)
- [ ] Quick Settings tile
- [ ] Wear OS app: view, complete, snooze
- [ ] Android Auto: read reminders aloud

### Smart / AI (later)
- [ ] Auto-suggest due date, list and priority from the text
- [ ] "Reschedule overdue" assistant
- [ ] Daily briefing notification each morning
- [ ] Break big tasks into subtasks

### Extra features (v2 ideas)
**Smarter reminders**
- [ ] "Don't disturb me" awareness: hold reminders while driving, in a meeting or during sleep hours, then deliver them right after
- [ ] Travel and timezone mode: choose per reminder whether it keeps local time or a fixed time
- [ ] Leave-by reminders: "Leave now, there's 25 min of traffic to reach the 6pm appointment"
- [ ] Weather-aware: "Take an umbrella" if it's raining at the reminder time
- [ ] Battery/charging triggers: "When the phone is charging tonight, remind me to back up"
- [ ] App-open trigger: "When I open WhatsApp, remind me to reply to Ravi"
- [ ] Proof of completion: require a photo, a QR/NFC scan or a maths puzzle to dismiss (as in alarm apps)
- [ ] Countdowns: days left until exams, trips or events, also shown on a widget
- [ ] Recurring checklists that reset each time (morning routine, packing list)

**Input**
- [ ] Screenshot/photo OCR: snap a bill, poster or ticket and create a dated reminder from it
- [ ] Turn SMS and email into reminders (OTP-style detection of bills, flight times, appointments)
- [ ] Multilingual natural-language input (Hindi, Tamil, Spanish and so on)
- [ ] Templates: "Doctor visit", "Rent", "Car service" with preset repeats

**Organisation & review**
- [ ] Eisenhower matrix (urgent/important) view
- [ ] Kanban board view
- [ ] Weekly review screen: what you finished, what slipped, what to plan
- [ ] Time blocking: drag tasks into calendar slots
- [ ] Estimated duration plus a "Do I have time today?" load meter
- [ ] Archive and completion history with an undo timeline

**Family & social**
- [ ] Family space: shared calendar, chores rotation, kids' tasks with rewards
- [ ] Caregiver mode: alert a family member if a medicine reminder isn't acknowledged
- [ ] Location sharing for reminders ("Remind Dad when he reaches the shop")
- [ ] Comments and mentions on shared tasks
- [ ] Accountability buddy: a friend sees if you skip a habit

**Health & life**
- [ ] Water intake, posture and eye-break (20-20-20) reminders
- [ ] Refill alerts when medicine stock runs low
- [ ] Document expiry tracker: passport, licence, insurance, warranty
- [ ] Plant watering, pet care and vehicle service schedules
- [ ] Important-dates gift suggestions for birthdays

**Gamification**
- [ ] Streaks, XP, levels and achievement badges
- [ ] Mood check-in after completing tasks

**Platform**
- [ ] Desktop/web companion and a browser extension
- [ ] Lock-screen and Always-On-Display reminders
- [ ] Smart-home hooks: Google Home/Alexa announcements, flash smart lights
- [ ] Tasker/automation intents and an open API
- [ ] Import from TickTick, Todoist, Google Tasks and Microsoft To Do (CSV/JSON/ICS)
- [ ] Senior mode: extra-large text, simple UI, loud repeating alarms

### Platform & trust
- [ ] Offline-first local DB
- [ ] Optional E2E-encrypted cloud sync and multi-device support
- [ ] Backup/restore (local file and Google Drive)
- [ ] Material You dynamic theming, dark mode, accessibility (TalkBack, large text)
- [ ] App lock (biometric)
- [ ] Stats: completion rate, streaks, missed reminders

## Recommended tech stack

| Layer | Choice | Reason |
|---|---|---|
| Language | **Kotlin** | Native Android, first-class support |
| UI | **Jetpack Compose + Material 3** | Modern UI, Material You theming |
| Architecture | MVVM + Clean Architecture, multi-module | Testable and scalable |
| DI | Hilt | Standard for Android |
| Local DB | Room (SQLite) + Flow | Offline-first, reactive |
| Scheduling | `AlarmManager.setExactAndAllowWhileIdle` / `setAlarmClock` for exact reminders; WorkManager for sync and maintenance | Reliable firing in Doze |
| Full-screen alarms | `USE_FULL_SCREEN_INTENT` + foreground service | Alarm-style reminders |
| Location | Google Play Services Geofencing API | Arrive/leave reminders |
| NLP date parsing | Custom parser (or a port of [Natty](https://github.com/joestelmach/natty) / Duckling rules) | Quick add |
| Widgets | Jetpack Glance | Compose-style widgets |
| Sync (later) | Supabase or Firebase, with client-side encryption | Fast to build |
| Wear OS | Compose for Wear OS | Code reuse |
| Testing | JUnit5, Turbine, Robolectric, Compose UI tests | |

### Key Android permissions
`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (only for location reminders, requested when the user first sets one), `READ_CONTACTS` (optional, for birthdays).

## Data model (core)

```
Reminder(id, title, notes, listId, priority, dueAt, timezone,
         recurrenceRule /* RFC 5545 RRULE */, isAllDay,
         alertStyle /* NOTIFICATION | HEADS_UP | ALARM */, nagIntervalMin,
         status /* ACTIVE | DONE | ARCHIVED */, createdAt, updatedAt, completedAt)
Alert(id, reminderId, offsetMinutes)          // multiple alerts per reminder
Trigger(id, reminderId, type /* LOCATION | WIFI | BT */, payload)
ListEntity(id, name, color, icon, sortOrder)
Tag(id, name) / ReminderTag(reminderId, tagId)
Subtask(id, reminderId, title, done, sortOrder)
Habit(id, name, schedule, target) / HabitLog(habitId, date, value)
FiredLog(id, reminderId, scheduledAt, firedAt, action)  // reliability audit
```

Store recurrence as an **RRULE** string so it maps directly to Google Calendar and iCal.

## Roadmap

| Phase | Scope | Goal |
|---|---|---|
| **v0.1 MVP** | Quick add (basic NLP), one-time/recurring reminders, multiple alerts, snooze, notification actions, lists, Today/Upcoming/Overdue, alarm-style mode, boot persistence | Reminders that never miss |
| **v0.2** | Subtasks, tags, priorities, calendar view, widgets, search, backup/restore | Full task manager |
| **v0.3** | Location/Wi-Fi/BT triggers, birthdays, bills, medicine | Context and life features |
| **v0.4** | Habits, Pomodoro, stats | Productivity suite |
| **v0.5** | Account and E2E sync, shared lists, remind others | Social |
| **v1.0** | Wear OS, Google Calendar sync, AI assistant, polish | Launch |

## Monetisation idea
- **Free:** everything in v0.1–v0.4, with no ads in core flows.
- **Rem Pro (small yearly fee):** cloud sync, shared lists, remind others, AI assistant, extra themes.

## Success metrics
- Missed-reminder rate under 0.5% (from the FiredLog audit)
- D30 retention above 30%
- Median time to add a reminder under 5 seconds
