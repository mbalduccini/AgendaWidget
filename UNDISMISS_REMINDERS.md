# Undismissing Calendar Reminders

This is a one-off maintenance procedure for restoring a reminder that was
dismissed by AgendaWidget, without changing the app.

The app stores dismissal state in Android Calendar provider extended
properties. Removing those ACK properties makes the reminder active again
locally. To make the change reach the CalDAV server through DAVx5, the parent
event must also be uploaded by DAVx5.

These commands assume the device is available as `localhost:5555`.

## 1. Find The Event

Search by title prefix:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/events \
   --projection _id:calendar_id:title:dtstart:dtend:rrule:eventTimezone \
   --where \"title LIKE 'TITLE PREFIX%'\""
```

If the title contains an apostrophe, double it inside SQL:

```bash
--where "title LIKE 'Nicole''s 5GB freedompop SIM renews around 08/11%'"
```

Proceed only if the result identifies the intended event unambiguously.

## 2. Inspect Reminders And Dismissal Rows

Replace `EVENT_ID` with the event id from step 1.

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/reminders \
   --projection _id:event_id:minutes:method \
   --where \"event_id=EVENT_ID\""

rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/extendedproperties \
   --projection _id:event_id:name:value \
   --where \"event_id=EVENT_ID\""
```

Dismissal rows usually look like:

```text
["X-CALDAV-ANDROID-ACK","METHOD;MINUTES;TIMESTAMP"]
["X-CALDAV-ANDROID-MOZACK","METHOD;MINUTES;TIMESTAMP"]
["X-MOZ-LASTACK","99991231T235859Z"]
```

Do not delete metadata rows such as `CREATED`, `X-ALT-DESC`,
`X-MICROSOFT-CDO-BUSYSTATUS`, labels, or recurrence-related rows.

## 3. Get The Calendar Account

Use the event's `calendar_id`:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/calendars \
   --projection _id:account_name:account_type:name:calendar_displayName \
   --where \"_id=CALENDAR_ID\""
```

For the events handled on 2026-07-18, calendar `30` was:

```text
account_name=Family DavX5
account_type=bitfire.at.davdroid
```

## 4. Delete Only The Dismissal Rows

Use the sync-adapter URI. Plain writes to extended properties are rejected by
CalendarProvider.

For ACK/MOZACK rows:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content delete \
   --uri 'content://com.android.calendar/extendedproperties?caller_is_syncadapter=true&account_name=Family%20DavX5&account_type=bitfire.at.davdroid' \
   --where \"event_id=EVENT_ID AND _id IN (ACK_ROW_ID,MOZACK_ROW_ID) AND name='vnd.android.cursor.item/vnd.i4a.unkp'\""
```

For a single `X-MOZ-LASTACK` row:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content delete \
   --uri 'content://com.android.calendar/extendedproperties?caller_is_syncadapter=true&account_name=Family%20DavX5&account_type=bitfire.at.davdroid' \
   --where \"_id=LASTACK_ROW_ID AND event_id=EVENT_ID AND name='vnd.android.cursor.item/vnd.i4a.unkp'\""
```

Verify the rows are gone:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/extendedproperties \
   --projection _id:event_id:name:value \
   --where \"event_id=EVENT_ID\""
```

At this point the reminder is undismissed locally, but this alone may not be
enough for DAVx5 to upload the change.

## 5. Force DAVx5 To Upload The Event

DAVx5 did not upload direct extended-property deletions until the parent event
itself was changed. The reliable procedure is:

1. Temporarily change the event title.
2. Wait for DAVx5 to upload the event.
3. Restore the exact original title.
4. Wait for DAVx5 to upload the restore.

Use a temporary suffix that is easy to spot:

```bash
rtk proxy adb -s localhost:5555 logcat -c

rtk proxy adb -s localhost:5555 shell \
  "content update --uri content://com.android.calendar/events \
   --bind title:s:\"ORIGINAL TITLE [sync]\" \
   --where \"_id=EVENT_ID\""
```

If shell quoting is awkward because of apostrophes, temporarily use a simple
title without apostrophes, then restore the exact original title later.

If ACK rows reappear after DAVx5 pulls the server copy, repeat step 4 while the
event still has the temporary title and is dirty.

Check local state:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/events \
   --projection _id:title:dirty:deleted:calendar_id \
   --where \"_id=EVENT_ID\""
```

Android should queue a DAVx5 upload sync. You can inspect queued syncs with:

```bash
rtk proxy adb -s localhost:5555 shell dumpsys content | \
  grep -i -A3 -B3 "Family DavX5/bitfire.at.davdroid u0 \\[com.android.calendar\\] LOCAL"
```

Watch DAVx5 logs:

```bash
rtk proxy /bin/bash -lc \
  'adb -s localhost:5555 logcat -d -v time -s davx5 dav4jvm |
   grep -E "Synchronizing calendar #30|Sending local deletes/updates|--> PUT|<-- 204|Received new ETag|Sent [0-9]+ record|Calendar sync complete|Sync for \\(com.android.calendar, Account \\{name=Family DavX5" |
   tail -n 160'
```

A successful upload contains a `PUT`, an HTTP `204`, a new ETag, and a line like:

```text
Sent 1 record(s) to server
```

## 6. Restore The Original Title

After the temporary-title upload succeeds:

```bash
rtk proxy adb -s localhost:5555 logcat -c

rtk proxy adb -s localhost:5555 shell \
  "content update --uri content://com.android.calendar/events \
   --bind title:s:\"ORIGINAL TITLE\" \
   --where \"_id=EVENT_ID\""
```

Wait for DAVx5 to upload again and verify another `PUT` / `204` / `Sent N
record(s) to server` sequence.

Final local verification:

```bash
rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/events \
   --projection _id:title:dirty:deleted:calendar_id \
   --where \"_id=EVENT_ID\""

rtk proxy adb -s localhost:5555 shell \
  "content query --uri content://com.android.calendar/extendedproperties \
   --projection _id:event_id:name:value \
   --where \"event_id=EVENT_ID\""
```

The final event row should have:

```text
title=<original title>
dirty=0
deleted=0
```

The dismissal ACK rows should still be absent.

## 7. Refresh AgendaWidget Notifications

Trigger a rescan so notifications display the restored title:

```bash
rtk proxy adb -s localhost:5555 shell \
  am start-foreground-service \
  -n gr.ictpro.jsalatas.agendawidget/.ui.AgendaUpdateService \
  -a gr.ictpro.jsalatas.agendawidget.action.UPDATE
```

Verify active notifications if needed:

```bash
rtk proxy adb -s localhost:5555 shell dumpsys notification --noredact | \
  grep -iE "EVENT TITLE|event:EVENT_ID|reminders triggered"
```

## Notes From 2026-07-18

The validated events were:

```text
4038 Driveway sealcoated by Beyond Sealcoating on 08/12/2024 -- do every 2-3 years
4045 Nicole's 5GB freedompop SIM renews around 08/11
4035 Marcy's 5GB freedompop SIM renews around 07/22
```

For Driveway and Marcy, temporary title edits caused DAVx5 to upload two records.
For Nicole, the title apostrophe caused quoting trouble; using a temporary
no-apostrophe title worked. The final title-restore sync uploaded all three
events and DAVx5 logged:

```text
Sent 3 record(s) to server
```
