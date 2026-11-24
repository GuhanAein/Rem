# Plan: Shopping, Wishlist and Work spaces

Each space is its own tab with its own items, layout and reminder rules, separate from everyday reminders.

## Navigation
A bottom tab bar in the Noir style: **Today · Shopping · Wishlist · Work**. The extra spaces (Life, People, Insights) stay on Today.

## 1. Shopping: buying reminders
**Lists:** Groceries, Household and Pharmacy, plus your own (one per shop).

**Items:** a name, quantity and unit ("2 kg"), an optional price and a note.
- **Fast add:** "milk, eggs, 2 bread, 1kg rice" becomes four items. Voice works too.
- **Auto-sorted by aisle** with keyword rules (milk → Dairy, onion → Vegetables), so the list follows the shop.
- **Tap to tick.** Ticked items slide to the bottom, and "Clear bought" empties them.
- **Buy again:** your usual items as one-tap chips.
- **Staples:** "milk every 3 days" adds itself to the list automatically.
- **Estimated total**, from item prices.

**Reminders**
- **At the shop:** "When I arrive at DMart, show the Groceries list". The notification lists the items.
- **On a day:** "Saturday 10 AM: groceries".
- **When the list gets long:** "5+ items on Groceries, time for a run?"
- **Running low:** mark an item as running low and it appears on the next list.

**Sharing:** send the list as text to WhatsApp. A live shared list waits for sync in v0.5.

## 2. Wishlist
**Items:** a title, link, photo, price, target price, priority and notes.
- **Add from any shopping app:** share a product from Amazon, Flipkart or Myntra and Rem saves the link and title.
- **For whom:** me, or a gift for someone in People, linked to their birthday or anniversary.
- **Status:** Wanted → Bought → Received.

**Reminders**
- **30-day cool-off:** "Still want the headphones?" a month after adding. Keep it, buy it, or let it go. This guards against impulse buys.
- **Payday:** "You get paid today. 3 wishlist items fit your budget."
- **Sale days:** the big sale dates you add, such as Diwali or Prime Day.
- **Buy before:** gifts remind you 2 weeks before the occasion.
- **Price check:** you enter the current price and Rem tells you when it's at or below your target. Automatic price tracking would need a server, so it isn't planned offline.

**Saving goals:** "Saved ₹6,000 of ₹15,000", with a progress ring and a monthly suggestion.

## 3. Work
**Projects** with their own tasks. Each task has a status (**To do · Doing · Waiting · Done**), shown as a list or a board.
- **Waiting on someone:** "Waiting on Ravi for the report" reminds you to follow up after 2 days.
- **Meeting follow-ups:** after a meeting time, "Any action items?" turns into tasks in one tap.
- **Recurring work:** weekly reports and month-end tasks.

**Work-life boundary**
- **Work hours,** such as Mon–Fri 9:30–6:30. Work reminders are held outside these hours and delivered at the start of the next work day.
- **Personal reminders are never held by work hours.**

**Daily rhythm**
- **Morning agenda:** today's tasks and follow-ups due.
- **Evening wrap-up:** what you finished and what moves to tomorrow, in one tap.
- **Focus timer** linked to a task.

## Data (database v3, migrated, nothing lost)
- `shopping_lists` (name, place, sort order) and `shopping_items` (list, name, qty, unit, aisle, price, checked, staple frequency, running low, last bought)
- `wishes` (title, url, photo, price, target, saved, person, occasion, status, added, cool-off date)
- `projects` (name, colour); work tasks reuse reminders with `space = WORK`, `projectId`, `status` and `waitingOn`
- Settings: work hours, payday, sale dates

## Build order
1. **Shopping**: the most-used daily feature.
2. **Work**, including work hours: the clearest separation from personal reminders.
3. **Wishlist**.

Each step includes tests, screenshots and a migration test.
