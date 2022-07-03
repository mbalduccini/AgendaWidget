package gr.ictpro.jsalatas.agendawidget.model.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;

import org.dmfs.jems2.function.SingleFunction;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.Duration;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import gr.ictpro.jsalatas.agendawidget.R;
import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.Events;
import gr.ictpro.jsalatas.agendawidget.model.settings.Settings;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskEvent;
import gr.ictpro.jsalatas.agendawidget.utils.DateUtils;
import gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities;

import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.EXTENDED_PROPERTIES_DEFAULT_NAMESPACE;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.deleteFromMultilinePropertyValue;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.getAbsoluteExtPropID;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.infinityDateMillis;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.infinityDateSeconds;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent.infinityDateStr;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.dateToMillis;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.dateToSeconds;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.millisToDate;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.secondsToDate;
import static java.lang.Integer.min;
import static java.lang.Long.max;

public class ExtendedCalendars extends Calendars {
    public static int LOOKAHEAD_MINUTES=10;

    public static List<EventItem> getEvents(int appWidgetId) {
        return(getEvents(appWidgetId,new ExtendedCalendarFetchAdapter(LOOKAHEAD_MINUTES)));
    }

    public static List<EventItem> refreshOneEvent(int appWidgetId,long id,List<EventItem> events) {
/*        if (!checkPermissions()) {
            return calendarEvents;
        }
 */

        int pos=-1;
        for(EventItem e : events) {
            pos++;
            if (e instanceof ExtendedCalendarEvent && ((ExtendedCalendarEvent)e).getId()==id) {
                break;
            }
        }
        if (pos==-1) return(events);

        refreshCalendarList();

        String[] calendarsList = Settings.getStringPref(AgendaWidgetApplication.getContext(), "calendars", appWidgetId).split("@@@");
        if (calendarsList.length == 1 && calendarsList[0].isEmpty()) {
            return(events);
        }

        TimeZone tzLocal = TimeZone.getDefault();

        java.util.Calendar calendarInstance = GregorianCalendar.getInstance();
        Date selectedRangeStart = DateUtils.dayFloor(calendarInstance.getTime());
        calendarInstance.setTimeInMillis(selectedRangeStart.getTime() + tzLocal.getOffset(calendarInstance.getTimeInMillis()));
        selectedRangeStart = DateUtils.dayFloor(calendarInstance.getTime());


        Long searchPeriod = Settings.getLongPref(AgendaWidgetApplication.getContext(), "searchPeriod", appWidgetId);

        calendarInstance.setTimeInMillis(selectedRangeStart.getTime() + searchPeriod);
        Date selectedRangeEnd = DateUtils.dayCeil(calendarInstance.getTime());

        List<EventItem> calendarEvents = new ArrayList<>();
        calendarEvents.addAll(events);
        Log.v("MYCALENDAR","removing from event list item number "+pos);
        Log.v("MYCALENDAR","initial size of calendar list="+calendarEvents.size());
        calendarEvents.remove(pos);
        Log.v("MYCALENDAR","final size of calendar list="+calendarEvents.size());
        List<EventItem> newEvents = new ExtendedCalendarFetchAdapter(LOOKAHEAD_MINUTES).fetchSingleCalendarEvent(appWidgetId,calendarsList,id,selectedRangeStart,selectedRangeEnd);
        calendarEvents.addAll(newEvents);

        Collections.sort(calendarEvents);

        return(calendarEvents);
    }

    /*
     * DISMISSING and SNOOZING
     * [https://tools.ietf.org/html/draft-ietf-calext-valarm-extensions-00#page-5]
     * To "snooze" an alarm, clients create a new "VALARM" component within
     * the parent component of the "VALARM" that was triggered and is being
     * "snoozed" (i.e., as a "sibling" component of the "VALARM" being
     * snoozed).  The new "VALARM" MUST be set to trigger at the user's
     * chosen "snooze" interval after the original alarm triggered.  Clients
     * SHOULD use an absolute "TRIGGER" property with a "DATE-TIME" value
     * specified in UTC.
     *
     * When the "snooze" alarm is triggered and dismissed the client SHOULD
     * remove the corresponding "VALARM" component, or set the
     * "ACKNOWLEDGED" property (see Section 5.1).  Alternatively, if the
     * "snooze" alarm is itself "snoozed", the client SHOULD remove the
     * original "snooze" alarm and create a new one, with the appropriate
     * trigger time and relationship set.
     *
     * [Section 5.1]
     * Clients SHOULD set this property to the current date-time value in
     * UTC when a calendar user acknowledges a pending alarm.  Certain
     * kinds of alarm may not provide feedback as to when the calendar
     * user sees them, for example email based alerts.  For those kinds
     * of alarms, the client SHOULD set this property when the alarm is
     * triggered and the action successfully carried out.
     * When an alarm is triggered on a client, clients can check to see
     * if an "ACKNOWLEDGED" property is present.  If it is, and the value
     * of that property is greater than or equal to the computed trigger
     * time for the alarm, then the client SHOULD NOT trigger the alarm.
     * Similarly, if an alarm has been triggered and an "alert" presented
     * to a calendar user, clients can monitor the iCalendar data to
     * determine whether an "ACKNOWLEDGED" property is added or changed
     * in the alarm component.  If the value of any "ACKNOWLEDGED"
     * property in the alarm changes and is greater than or equal to the
     * trigger time of the alarm, then clients SHOULD dismiss or cancel
     * any "alert" presented to the calendar user.
     */

    // We must mark the event dirty *before* making changes to reminders or extended proeprties.
    // Marking it as dirty ensures that the information is sent to the cloud.
    // https://stackoverflow.com/questions/35736822/android-calendar-provider-only-initially-created-extended-property-syncs-across
    static void markEventDirty(ExtendedCalendarEvent event) {

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        ContentValues content = new ContentValues();
        // The following line is not needed. An empty update is sufficient
        //content.put(CalendarContract.Events.TITLE, event.getTitle());
        Uri u= ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.getId());
        Uri.Builder builder = u.buildUpon();

        cr.update(builder.build(), content, null, null);
    }

    public static void dismissCalDAVEventReminders(EventItem event) {

        String ext_selection;
        String[] ext_projection;
        Cursor cur;

        if (event instanceof ExtendedCalendarEvent && !(event instanceof TaskEvent)) {
            ExtendedCalendarEvent calendarEvent = (ExtendedCalendarEvent) event;

            // TODO: see if these are needed
            //val dateFormat: DateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
            //dateFormat.timeZone=TimeZone.getTimeZone("UTC")

            markEventDirty(calendarEvent);

            long curTimeSec=System.currentTimeMillis() / 1000L;

            boolean is_repeat=(calendarEvent.getRrule()!=null);
            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): setting properties for event "+calendarEvent.getTitle()+"; (is_repeat="+is_repeat+")");

            String dtStr=infinityDateStr;
            long ackTimeSec=infinityDateSeconds;
            if (is_repeat) {
                ackTimeSec=max(dateToSeconds(calendarEvent.getEndDate()), curTimeSec) + 1;
                dtStr=ISO8601Utilities.formatDateTimeCompact(secondsToDate(ackTimeSec));
            }

            calendarEvent.addOrUpdateExtendedPropertyJSonEncoded("X-MOZ-LASTACK",dtStr);
            // The following property is only for Google Calendars (not CalDAV). It will not be read back on other devices if we are using CalDAV
            calendarEvent.addOrUpdateExtendedProperty("private:X-MOZ-LASTACK",dtStr);

            ext_selection = CalendarContract.ExtendedProperties.EVENT_ID +" = " + calendarEvent.getId() + " AND "+ CalendarContract.ExtendedProperties.NAME +" = \"private:http://emclient.com/ns/#calendar\"";
            ext_projection = new String[]{
                    CalendarContract.ExtendedProperties._ID,
                    CalendarContract.ExtendedProperties.VALUE,
            };
            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): looking for X-MOZ-SNOOZE-TIME in private:http://emclient.com/ns/#calendar");

            ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();
            Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
            cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
            while (cur.moveToNext()) {
                String v = cur.getString(1);
                String v2=deleteFromMultilinePropertyValue(v, "X-MOZ-SNOOZE-TIME");
                if (!v.equals(v2)) {
                    if (!v2.equals("")) {
                        calendarEvent.updateExtendedProperty(cur.getLong(0), v2);
                        Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): X-MOZ-SNOOZE-TIME removed from private:http://emclient.com/ns/#calendar");
                    }
                    else {
                        calendarEvent.deleteExtendedProperty(cur.getLong(0));
                        Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): X-MOZ-SNOOZE-TIME removed (full private:http://emclient.com/ns/#calendar property removed)");
                    }
                }
            }
            cur.close();
            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): done setting properties for calendarEvent with title "+ calendarEvent.getTitle());

            // Remove X-MOZ-SNOOZE-TIME if present
            calendarEvent.deleteExtendedPropertyJSonEncoded("X-MOZ-SNOOZE-TIME", null);
            Log.v("MYCALENDAR", "X-MOZ-SNOOZE-TIME deleted if it exists");

            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): before storing ACK-C");

            for (ExtendedCalendarEvent.Reminder r : calendarEvent.getReminders()) {
                int type = (r.type == CalendarContract.Reminders.METHOD_EMAIL)? ExtendedCalendarEvent.Reminder.REMINDER_EMAIL : ExtendedCalendarEvent.Reminder.REMINDER_NOTIFICATION;
                int method=(r.type == ExtendedCalendarEvent.Reminder.REMINDER_EMAIL) ? CalendarContract.Reminders.METHOD_EMAIL : CalendarContract.Reminders.METHOD_ALERT;

                long absolute_extprop_id=getAbsoluteExtPropID(calendarEvent.getId(), method, r.minutes);
                boolean is_absolute=(absolute_extprop_id!=-1L);
                if (is_absolute) {
                    String selection = CalendarContract.Reminders.EVENT_ID + " = "+calendarEvent.getId()+" AND "+ CalendarContract.Reminders.METHOD+" = "+method+" AND "+ CalendarContract.Reminders.MINUTES+" = "+r.minutes;
                    cr.delete(CalendarContract.Reminders.CONTENT_URI, selection, null);
                    Log.v("MYCALENDAR", "done with is_absolute branch");
                }
                else {
                    Log.v("MYCALENDAR", "in !is_absolute branch");
                    ext_selection = CalendarContract.ExtendedProperties.EVENT_ID +" = " + calendarEvent.getId() + " AND "+ CalendarContract.ExtendedProperties.NAME +" = \""+EXTENDED_PROPERTIES_DEFAULT_NAMESPACE+"\"";
                    ext_projection = new String[]{
                            CalendarContract.ExtendedProperties._ID,
                            CalendarContract.ExtendedProperties.VALUE,
                    };
                    String ACK_SEP = ";";
                    String pname1 = "X-CALDAV-ANDROID-ACK";
                    String pname2 = "X-CALDAV-ANDROID-MOZACK";
                    String prefix1 = "[\"" + pname1 + "\",\"" + method + ACK_SEP + r.minutes + ACK_SEP;
                    String prefix2 = "[\"" + pname2 + "\",\"" + method + ACK_SEP + r.minutes + ACK_SEP;
                    boolean prefix1_found=false;
                    boolean prefix2_found=false;
                    Date startTS = calendarEvent.getStartDate();
                    long notifTS2=((long)ackTimeSec)*1000L;

                    cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
                    while (cur.moveToNext()) {
                        long ext_prop_id = cur.getLong(0);
                        String v = cur.getString(1);
                        Log.v("MYCALENDAR", "got reminder ext-prop; id=" + ext_prop_id + "; value=" + v);

                        Log.v("MYCALENDAR", "checking startsWith of " + pname1 + " or " + pname2);
                        int res = 0;   // 0=not found; 1=ACK; 2=MOZACK
                        String prefix = "";
                        String pname = "";
                        if (v.startsWith(prefix1)) {
                            res = 1;
                            prefix = prefix1;
                            pname = pname1;
                            prefix1_found=true;
                        } else if (v.startsWith(prefix2)) {
                            res = 2;
                            prefix = prefix2;
                            pname = pname2;
                            prefix2_found=true;
                        }
                        if (res != 0) {
                            long t = Long.parseLong(v.substring(prefix.length(), v.length() - 2)) / 1000L;
                            Log.v("MYCALENDAR", "it's a match for method+minutes!! remaining data=" + t);

                            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): ack<startTS+remind: changing ack to +inf for time " + (dateToSeconds(startTS) - r.minutes * 60));

                            calendarEvent.updateExtendedProperty(
                                    ext_prop_id,
                                    "[\"" + pname + "\",\"" + method + ACK_SEP + r.minutes + ACK_SEP + notifTS2 + "\"]");
                        }
                    }
                    cur.close();

                    if (!prefix1_found) {
                        calendarEvent.addExtendedProperty(
                                EXTENDED_PROPERTIES_DEFAULT_NAMESPACE,
                                "[\"" + pname1 + "\",\"" + method + ACK_SEP + r.minutes + ACK_SEP + notifTS2 + "\"]");
                    }
                    if (!prefix2_found) {
                        calendarEvent.addExtendedProperty(
                                EXTENDED_PROPERTIES_DEFAULT_NAMESPACE,
                                "[\"" + pname2 + "\",\"" + method + ACK_SEP + r.minutes + ACK_SEP + notifTS2 + "\"]");
                    }
                }
            }

            // TODO: figure out WHEN I should call this. For sure, I can't call it right away, because the Davx5 property are the way I communicate the info to DavX5 for sending to the calendar provider!!
            //calendarEvent.deleteDavx5PropertiesFromGoogleCalendar();  // remove davx5 properties if this is a Google Calendar

            // TODO: cause the reminder listview to be refreshed
        }
    }
}

class ExtendedCalendarFetchAdapter implements CalendarFetchAdapter {
    /* parameters for debugging */
    final static boolean useDateRange=false; // only fetch events from the given date range

    // Events that will get triggered in the future within this number of minutes
    // will be returned by fetchCalendarEvents()
    int LOOKAHEAD_MINUTES;

    public ExtendedCalendarFetchAdapter(int LOOKAHEAD_MINUTES) {
        this.LOOKAHEAD_MINUTES=LOOKAHEAD_MINUTES;
    }

    String readFromMultilinePropertyValue(String blob,String key,String defaultValue) {
        String[] lines=blob.replace("\r", "").split("\n");
        for(String line : lines) {
            if (line.startsWith(key + ":")) {
                String v=new ExtString(line).substringAfter(":", "").toString();
                Log.v("MYCALENDAR", "in readFromMultilinePropertyValue($blob): found: "+line+"; val=$v; full block=$blob");
                if (v!="") return(v);
            }
        }
        Log.v("MYCALENDAR", "in readFromMultilinePropertyValue("+blob+"): NOT found: "+key+"; returning default: $defaultValue; full block="+blob);
        return(defaultValue);
    }

    List<ExtendedCalendarEvent.Reminder> getCalDAVEventReminders(long eventId, int calendarId, Date startDate, String title) {
        List<ExtendedCalendarEvent.Reminder> reminders = new ArrayList<>();
        Cursor cur;
        long startTS=dateToSeconds(startDate);

        final String[] PROJECTION = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.EVENT_ID,
                CalendarContract.ExtendedProperties.NAME,
                CalendarContract.ExtendedProperties.VALUE,
//                CalendarContract.ExtendedProperties.CALENDAR_ID,
        };

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        // TODO: make sure that event IDs are unique across calendars. Otherwise, find a way to search for calendarId
        String selection=CalendarContract.ExtendedProperties.EVENT_ID+" = "+eventId;
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
/*
        cur = cr.query(builder.build(), PROJECTION, selection, null, null);
        while (cur.moveToNext()) {
            String n = cur.getString(2);
            String v = cur.getString(3);
            Log.v("MYCALENDAR", "got event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; title="+title);
        }
        cur.close();
*/
        /******************************************************/
        /* [MB] process private:X-MOZ-LASTACK and X-MOZ-LASTACK */
        long main_snooze=-1L;
        long main_ack=-1L;
        String ext_selection_ack=CalendarContract.ExtendedProperties.EVENT_ID+" = "+eventId;
        //val formatter = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC() //.withOffsetParsed()
        cur = cr.query(builder.build(), PROJECTION, selection, null, null);
        while (cur.moveToNext()) {
            String n = cur.getString(2);
            ExtString v = new ExtString(cur.getString(3));

            Date dt;
            if (n.equals("private:X-MOZ-LASTACK") || n.equals("X-MOZ-LASTACK")) {
                dt=ISO8601Utilities.parseDateTimeCompact(v.toString());//.plusMillis(offset)
                if (dt!=null) {
                    Log.v("MYCALENDAR", "processing event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; dt=" + dt.toString() + "; dt-mill=" + dt.getTime()+"; title="+title);
                    main_ack = max(main_ack, dateToSeconds(dt));
                }
            }
            else if (n.equals(EXTENDED_PROPERTIES_DEFAULT_NAMESPACE) && v.toString().startsWith("[\"X-MOZ-LASTACK\",\"")) {
                dt=ISO8601Utilities.parseDateTimeCompact(v.substringAfter(",").substringAfter("\"").substringBefore("\"").toString());
                if (dt!=null) {
                    Log.v("MYCALENDAR", "processing event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; dt=" + dt.toString() + "; dt-mill=" + dt.getTime()+"; title="+title);
                    main_ack = max(main_ack, dateToSeconds(dt));
                }
            }
            else if (n.equals("private:X-MOZ-SNOOZE-TIME") || n.equals("X-MOZ-SNOOZE-TIME")) {
                dt=ISO8601Utilities.parseDateTimeCompact(v.toString());
                if (dt!=null) {
                    Log.v("MYCALENDAR", "processing event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; dt=" + dt.toString() + "; dt-mill=" + dt.getTime()+"; title="+title);
                    main_snooze = max(main_snooze, dateToSeconds(dt));
                }
            }
            else if (n.equals(EXTENDED_PROPERTIES_DEFAULT_NAMESPACE) && v.toString().startsWith("[\"X-MOZ-SNOOZE-TIME\",\"")) {
                dt=ISO8601Utilities.parseDateTimeCompact(v.substringAfter(",").substringAfter("\"").substringBefore("\"").toString());
                if (dt!=null) {
                    Log.v("MYCALENDAR", "processing event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; dt=" + dt.toString() + "; dt-mill=" + dt.getTime()+"; title="+title);
                    main_snooze = max(main_snooze, dateToSeconds(dt));
                }
            }
            else if (n.equals("private:http://emclient.com/ns/#calendar")) {
                String v2=readFromMultilinePropertyValue(v.toString(), "X-MOZ-SNOOZE-TIME", "");
                Log.v("MYCALENDAR", "received from readFromMultilinePropertyValue() for event_id=" + eventId + ": "+v2);
                if (!v2.equals("")) {
                    dt=ISO8601Utilities.parseDateTimeCompact(v2);
                    if (dt!=null) {
                        Log.v("MYCALENDAR", "processing event ext-prop; event_id=" + eventId + "; name=" + n + "; value=" + v + "; dt=" + dt.toString() + "; dt-mill=" + dt.getTime());
                        main_snooze = max(main_snooze, dateToSeconds(dt));
                    }
                }
            }
        }
        cur.close();

        Log.v("MYCALENDAR", "event_id="+eventId+": X-MOZ-LASTACK processed; main_ack="+main_ack+"; X-MOZ-SNOOZE-TIME processed; main_snooze="+main_snooze);

        builder = CalendarContract.Reminders.CONTENT_URI.buildUpon();
        final String[] PROJECTION_reminders = new String[]{
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD
        };
        selection=CalendarContract.Reminders.EVENT_ID+" = "+eventId;

        boolean found_absolute=false;
        long max_ack=main_ack;
        cur = cr.query(builder.build(), PROJECTION_reminders, selection, null, null);
        while (cur.moveToNext()) {
            int minutes = cur.getInt(0);
            int method = cur.getInt(1);
            Log.v("MYCALENDAR", "got event reminder; event_id=" + eventId + "; min=" + minutes + "; method=" + method);
            if (method == CalendarContract.Reminders.METHOD_ALERT || method == CalendarContract.Reminders.METHOD_EMAIL) {
                int type = (method == CalendarContract.Reminders.METHOD_EMAIL)? ExtendedCalendarEvent.Reminder.REMINDER_EMAIL : ExtendedCalendarEvent.Reminder.REMINDER_NOTIFICATION;

                // TODO: see if I can avoid this extra lookup into ExtendedProperties, since I have already fetched the values before
                String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID+" = "+eventId+" AND "+CalendarContract.ExtendedProperties.NAME+" = \""+EXTENDED_PROPERTIES_DEFAULT_NAMESPACE+"\"";
                final String[] ext_projection = new String[]{
                        CalendarContract.ExtendedProperties.VALUE
                };
                Uri.Builder ext_builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
                long ack=main_ack;
                boolean is_absolute=false;
                String ACK_SEP=";";
                Cursor ext_cur = cr.query(ext_builder.build(), ext_projection, ext_selection, null, null);
                while (ext_cur.moveToNext()) {
                        //val n = cursor.getStringValue(ExtendedProperties.NAME)
                    String v = ext_cur.getString(0);
                    // TODO: figure out if using method and minutes below is correct. In Kotlin, I was using getStringValue() but the values are ints???
                    String prefix1="[\"X-CALDAV-ANDROID-ACK\",\""+method+ACK_SEP+minutes+ACK_SEP;
                    String prefix2="[\"X-CALDAV-ANDROID-MOZACK\",\""+method+ACK_SEP+minutes+ACK_SEP;
                    // TODO: deal with X-MOZ-SNOOZE-TIME and the Google Calendar/EMclient variants
                    //  val prefix3="[\"X-MOZ-SNOOZE-TIME\","
                    Log.v("MYCALENDAR", "got reminder ext-prop; value=" + v);
                    String prefix="";
                    if (v.startsWith(prefix1)) {
                        prefix=prefix1;
                    }
                    else if (v.startsWith(prefix2)) {
                        prefix=prefix2;
                    }
                    if (!prefix.equals("")) {
                        Log.v("MYCALENDAR", "startsWith " + prefix);
                        long t=Long.parseLong(v.substring(prefix.length(), v.length() - 2))/1000L;
                        Log.v("MYCALENDAR", "it's a match for method+minutes!! remaining=" + t + " i.e. date "+secondsToDate(t));
                        ack=max(ack, t);
                    }
                    else {
                        if (v.equals("[\"X-CALDAV-ANDROID-ABSOLUTE\",\"" + method + ACK_SEP + minutes + "\"]")) {
                            Log.v("MYCALENDAR", "it's absolute!");
                            is_absolute=true;
                            found_absolute=true;
                        }
                    }
                }
                ext_cur.close();
                boolean ack_is_inf=(ack==infinityDateSeconds);
                Log.v("MYCALENDAR", "ack=" + ack + "; startTS=" + startTS + "; remind time=" + (startTS - minutes * 60) + "; is_absolute=" + is_absolute + "; ack_is_inf?" + ack_is_inf);
                if (is_absolute && !ack_is_inf && main_snooze!=-1L) {
                    int oldminutes=minutes;
                    minutes=min(minutes, (int)((startTS - main_snooze) / 60));
                    Log.v("MYCALENDAR", "ack=" + ack + "; startTS=" + startTS + "; remind time=" + (startTS - oldminutes * 60) + "; is_absolute=" + is_absolute + "; ack_is_inf?" + ack_is_inf + "; minutes corrected to "+minutes+" due to main_snooze of "+main_snooze);
                }
                // TODO: remove the line below. Apparently it's only for debugging
                if ((!is_absolute && !ack_is_inf) || ack<startTS-minutes*60) // for debugging only
                    Log.v("MYCALENDAR", "(!is_absolute && !ack_is_inf) || ack<startTS+remind (is_absolute=" + is_absolute + "): keeping reminder for time " + (startTS - minutes * 60));
                else
                    Log.v("MYCALENDAR", "(is_absolute || ack_is_inf) && ack>=startTS+remind (is_absolute=" + is_absolute + "; ack_is_inf="+ack_is_inf+"): dropping reminder for time " + (startTS - minutes * 60));
                if ((!is_absolute && !ack_is_inf) || ack<startTS-minutes*60) {
                    /*
                     * https://tools.ietf.org/id/draft-daboo-valarm-extensions-04.html#rfc.section.8.1
                     * When an alarm is triggered on a client, clients can check to see if an
                     * "ACKNOWLEDGED" property is present. If it is, and the value of that property
                     * is greater than or equal to the computed trigger time for the alarm, then the
                     * client SHOULD NOT trigger the alarm. Similarly, if an alarm has been triggered
                     * and an "alert" presented to a calendar user, clients can monitor the iCalendar
                     * data to determine whether an "ACKNOWLEDGED" is added or changed in the alarm
                     * component. If the value of any "ACKNOWLEDGED" in the alarm changes and is
                     * greater than or equal to the trigger time of the alarm, then clients SHOULD
                     * dismiss or cancel any "alert" presented to the calendar user.
                     */
                    // end [MB]
                    if (ack==-1L) Log.v("MYCALENDAR", "creating a reminder with ack=-1 for an event!!!");
                    max_ack=max(max_ack, ack);
                    ExtendedCalendarEvent.Reminder reminder = new ExtendedCalendarEvent.Reminder(minutes, type, ack,(is_absolute) ? (startTS-minutes*60) : -1L); // [MB] ack added and absoluteTS added; see Reminder class for details
                    reminders.add(reminder);
                }
            }
        }
        if (main_snooze!=-1L && !found_absolute) {
            int minutes=(int)((startTS-main_snooze)/60);
            Log.v("MYCALENDAR", "creating a reminder for main_snooze with ack="+max_ack+", minutes="+minutes+"; absoluteTS="+main_snooze);
            ExtendedCalendarEvent.Reminder reminder = new ExtendedCalendarEvent.Reminder(minutes, ExtendedCalendarEvent.Reminder.REMINDER_NOTIFICATION, max_ack, main_snooze);  // [MB] ack added and absoluteTS added; see Reminder class for details
            reminders.add(reminder);
        }

        class sortByMinutes implements Comparator<ExtendedCalendarEvent.Reminder>
        {
            public int compare(ExtendedCalendarEvent.Reminder a, ExtendedCalendarEvent.Reminder b)
            {
                return(a.minutes - b.minutes);
            }
        }

        Collections.sort(reminders,new sortByMinutes());
        return(reminders);
    }

    Date getFirstActiveInstanceViaLibrary(Date startDate,String rrule,ExtendedCalendarEvent.Reminder r) {
        /*
         * I was unable to import lib-recur (https://github.com/dmfs/lib-recur) as a module.
         * So I did the following:
         *  1. I added it as a git module with "git submodule add <github repo URL>"
         *  2. I also added this one: https://github.com/dmfs/rfc5545-datetime
         *     and BRANCH "jems1" of https://github.com/dmfs/jems.git to folder jems
         *     and the default branch of https://github.com/dmfs/jems.git to folder jems2
         *  3. On command line, I ran:
         *     cd app/src/main/java
         *     mkdir -p org/dmfs/rfc5545
         *     cd org/dmfs/rfc5545
         *     ln -s ../../../../../../../rfc5545-datetime/src/main/java/org/dmfs/rfc5545/* .
         *     ln -s ../../../../../../../lib-recur/src/main/java/org/dmfs/rfc5545/* .
         *     cd ..
         *     ln -s ../../../../../../jems/src/main/java/org/dmfs/* .
         *     UNNECESSARY >>> ln -s ../../../../../../jems2/src/main/java/org/dmfs/jems2/ .
         */
        try {
            //Log.w("MYCALENDAR","refDate calc; rrule="+rrule);
            RecurrenceRule rule = new RecurrenceRule(rrule);
            //DateTime start = new DateTime(2022, 5 /* 0-based month numbers! */, 19, 20, 0, 0);
            DateTime start=new DateTime(dateToMillis(startDate));
            //Log.w("MYCALENDAR","refDate calc; start TS="+dateToMillis(startDate));

            // NOTE: it is ok if the date provided is not one of the instances
            //       of the recurrence rule. The iterator will return the first
            //       instance ON or AFTER the date provided.
            //       Notice that you need to be careful that the current date/time
            //       is what you intend. For instance, using now() may cause today's
            //       instance to be skipped because of the time difference of UTC.
            //       Using nowAndHere() works well, as does specifying the date/time
            //       as individual year, month, etc.
            //       The iterator also has a fastForward() method that may be useful.
            RecurrenceRuleIterator it = rule.iterator(start);

            DateTime ff=new DateTime(((long)r.ack)*1000L);
            it.fastForward(ff);
            //Log.w("MYCALENDAR","refDate calc; ff to TS="+((long)r.ack)*1000L);

            long secondsTS;
            while (it.hasNext()) {
                DateTime dt=it.nextDateTime();
                secondsTS=dt.getTimestamp()/1000L;
                //Log.w("MYCALENDAR","refDate calc; considering TS (sec)="+secondsTS);
                if (secondsTS - r.minutes*60 > r.ack)
                    return(secondsToDate(secondsTS));
            }
        }
        catch (InvalidRecurrenceRuleException x) {
            //Log.w("MYCALENDAR","Invalid recurrence rule!!");
        }
        return(null);
    }

    // Get first active instance using Android
    // https://developer.android.com/guide/topics/providers/calendar-provider#java
    // Section: Query the instances table
    Date getFirstActiveInstanceAndroid(long id, Date startDate, ExtendedCalendarEvent.Reminder r) {
        final String DEBUG_TAG = "MYCALENDAR";
        final String[] INSTANCE_PROJECTION = new String[] {
                CalendarContract.Instances.BEGIN,
        };

// The indices for the projection array above.
        final int PROJECTION_BEGIN_INDEX = 0;

// Specify the date range you want to search for recurring
// event instances
        // Use Long.MIN_VALUE for -inf
        long startMillis = max(Long.MIN_VALUE,((long)r.ack)*1000L);
        long endMillis = Long.MAX_VALUE; // end date

        Cursor cur;
        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

// The ID of the recurring event whose instances you are searching
// for in the Instances table
        String selection = CalendarContract.Instances.EVENT_ID + " = ?";
        String[] selectionArgs = new String[] {""+id};

// Construct the query with the desired date range.
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startMillis);
        ContentUris.appendId(builder, endMillis);

// Submit the query
        cur =  cr.query(builder.build(),
                INSTANCE_PROJECTION,
                selection,
                selectionArgs,
                null);

        while (cur.moveToNext()) {
            //long eventID = 0;
            long beginVal = 0;

            // Get the field values
            //eventID = cur.getLong(PROJECTION_ID_INDEX);
            beginVal = cur.getLong(PROJECTION_BEGIN_INDEX);

            if ((beginVal/1000L) - r.minutes*60 > r.ack) {
                return(millisToDate(beginVal));
            }
        }
        return(null);
    }

    Date getFirstActiveInstance(long id, Date startDate, String rrule, ExtendedCalendarEvent.Reminder r, String title) {
        Date refStartDate=getFirstActiveInstanceAndroid(id,startDate,r);
        if (refStartDate==null) {
            // TODO: find if this one a one-time problem. Note that the extra computation does not seem to affect the refresh time
            // In some very rare cases (an Outlook entry), Android returned null when there should have been instance.
            // To be safe, we fall back to using the library.
            Log.v("MYCALENDAR", "Android computed a null recurrence. Falling back to using the library");
            refStartDate=getFirstActiveInstanceViaLibrary(startDate,rrule,r);
        }
        Log.v("MYCALENDAR", "got refDate="+refStartDate+"; startDate TS="+startDate.getTime() + "; ack (min)=" +r.ack+ " for "+title);
        return(refStartDate);
    }

    boolean isForcedDisplayEvent(String title) {
        String[] prefixes=new String[] {
                "Test ",
                "Contact Ivan",
                "AskLab 1",
                "Call Modern Ext.",
                "ITAC Meeting",
                "Replacement meeting",
                "Paper recycling week",
                "Office Hours",
                "Lock office hours",
                "Rev run",
                "Mist cuttings",
                "DSS315-D01",
                "DSS315-D02",
                "Niki game",
                "ASKLab Weekly Meeting",
                "SDGII Weekly Sync",
                "ASP Application Deli",
                "Cancel walmart+",
                "ASP Application Des",
                "Open house Ard",
                "Invoice Tufts",
                "Internal meeting",
                "TiAS cybersecurity",
                "Spray for deer",
                "SDG Dashboard wee",
                "Work on ISO docu",
                "Ly's paper",
                "Chiamata",
                "Tufts: papers, ",

        };

        for (String p : prefixes) {
            if (title.startsWith(p)) return(true);
        }
        return(false);
    }

    public List<EventItem> fetchSingleCalendarEvent(int appWidgetId,String[] calendarsList,long id,Date selectedRangeStart,Date selectedRangeEnd) {
        return(fetchCalendarEvents(appWidgetId,calendarsList,id,selectedRangeStart,selectedRangeEnd));
    }

    public List<EventItem> fetchCalendarEvents(int appWidgetId,String[] calendarsList,Date selectedRangeStart,Date selectedRangeEnd) {
        return(fetchCalendarEvents(appWidgetId,calendarsList,-1L,selectedRangeStart,selectedRangeEnd));
    }

    List<EventItem> fetchCalendarEvents(int appWidgetId,String[] calendarsList,long restrict_to_id,Date selectedRangeStart,Date selectedRangeEnd) {
        List<EventItem> calendarEvents = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        for (String calendar : calendarsList) {
            if (sb.toString().isEmpty()) {
                sb.append("(");
            }
            else {
                sb.append(" OR ");
            }
            sb.append(CalendarContract.Events.CALENDAR_ID).append(" = ").append(calendar);
        }
        if (!sb.toString().isEmpty()) {
            sb.append(")");
        }

        if (restrict_to_id!=-1L) {
            if (!sb.toString().isEmpty()) sb.append(" AND ");
            sb.append(CalendarContract.Events._ID).append(" = ").append(restrict_to_id);
        }

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        final String[] PROJECTION = new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_COLOR,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_END_TIMEZONE,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.EXDATE,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
                CalendarContract.Events.CALENDAR_TIME_ZONE,
                CalendarContract.Events.DELETED,
                CalendarContract.Events.HAS_ALARM, // [MB]
                CalendarContract.Events.CALENDAR_ID,
        };

        long id;
        @ColorInt int color;
        String title;
        String location;
        String description;
        String eventTimezone;
        Date startDate;
        Date endDate;
        String duration;
        boolean allDay;
        String rrule;
        String originalId;
        long originalInstanceTime;
        String calendarTimezone;
        int hasAlarm;
        int calendarId;


        String selectedAccountsFilter = sb.toString();

        String selection = "(" + selectedAccountsFilter + ")";
        if (useDateRange) {
            selection = "(" + selection + " AND ((" + CalendarContract.Events.DTSTART
                    + " >= " + selectedRangeStart.getTime() + ") AND (" + CalendarContract.Events.DTEND + " <= " + selectedRangeEnd.getTime() + "))" + ")";
        }

        Log.w("MYCALENDAR","selection="+selection);

        Uri.Builder builder = CalendarContract.Events.CONTENT_URI.buildUpon();

        Cursor cur = cr.query(builder.build(), PROJECTION, selection, null, null);

        java.util.Calendar calendarInstance = GregorianCalendar.getInstance();
        Date now = GregorianCalendar.getInstance().getTime();
        while (cur.moveToNext()) {
            id = cur.getLong(0);
            color = cur.getInt(1);
            calendarId = cur.getInt(18);
            title = cur.getString(2);
            if (title==null) {
                Log.e("MYCALENDAR","Found event with null title in calendar with ID="+calendarId+". Ignoring it");
                continue;
            }
            location = cur.getString(3);
            description = cur.getString(4);
            eventTimezone = cur.getString(5);
            allDay = cur.getInt(9) == 1;
            duration = cur.getString(10);
            calendarInstance.setTimeInMillis(cur.getLong(6));
            startDate = calendarInstance.getTime();
            if (cur.getLong(8)!=0) {
                calendarInstance.setTimeInMillis(cur.getLong(8));
                endDate = calendarInstance.getTime();
            }
            else if (duration==null) {
                endDate=startDate;
                Log.e("MYCALENDAR","Found event with null duration and endDate. Using startDate as endDate. Event title is "+title);
            }
            else {
                Log.w("MYCALENDAR","endDate is null. Will work with dur="+duration+" for title="+title);
                Duration d=null;
                boolean failed=true;
                try {
                    d = Duration.parse(duration);
                    failed=false;
                }
                catch (IllegalArgumentException x) {
                    int len=duration.length();
                    if (duration.substring(0,1).toLowerCase().equals("p") && duration.charAt(1)>='0' && duration.charAt(1)<='9' &&
                        (duration.substring(len-1,len).toLowerCase().equals("s") || duration.substring(len-1,len).toLowerCase().equals("m") || duration.substring(len-1,len).toLowerCase().equals("h"))) {
                        // some duration strings lack the "T" after the initial "P". Let's try to fix it
                        duration="PT"+duration.substring(1);
                        try {
                            d = Duration.parse(duration);
                            failed=false;
                        }
                        catch (IllegalArgumentException x2) {
                        }
                    }
                }
                if (!failed) {
                    calendarInstance.setTimeInMillis(startDate.getTime() + d.toMillis());
                    endDate = calendarInstance.getTime();
                }
                else {
                    // TODO: we must do something about this one!!
                    Log.e("MYCALENDAR", "endDate is null and duration "+duration+" fails parsing. title="+title);
                    continue;
                }
            }
            rrule = cur.getString(12);
            originalId = cur.getString(13);
            originalInstanceTime = cur.getLong(14);
            calendarTimezone = cur.getString(15);
            hasAlarm = cur.getInt(17);

            String eventTZ;
            if (eventTimezone!=null)
                eventTZ=eventTimezone;
            else if (calendarTimezone!=null)
                eventTZ=calendarTimezone;
            else
                eventTZ=TimeZone.getDefault().getID();
            // TODO: I think I should add the timezone to the event

            // TODO: see if we need to use this piece of code
            /* [MB] in some cases, existing notifications are not cleared if the reminders disappear from the server */
            /*
            if (reminders.size==0) {
                Log.v("MINE", "in Context.fetchCalDAVCalendarEvents(" + title + "; id=${id}); no reminders, setting up for cancelNotification()")
                EventsHelper.events_without_reminder.add(id)
            }
            */
            /* end [MB] */

            List<ExtendedCalendarEvent.Reminder> reminders=getCalDAVEventReminders(id, calendarId, startDate, title);
            for(ExtendedCalendarEvent.Reminder r : reminders) {
                Log.w("MYCALENDAR","has reminder: type="+r.type+"; minutes="+r.minutes+"; absoluteTS="+r.absoluteTS+"; ack="+r.ack);
            }
            Log.w("MYCALENDAR","working on reminders for calendarEvent title="+title+"; allDay="+allDay+"; startDate="+startDate+"; endDate="+cur.getLong(8)+"/"+endDate+"/"+endDate.getTime()+"; rrule="+rrule+"; originalId="+originalId+"; hasAlarm="+hasAlarm+"; calendarId="+calendarId+"; eventTZ="+eventTimezone+"; calendarTZ="+ calendarTimezone+"; # reminders="+reminders.size());

            // testing reminders
            // assumption: no RRULE
            // FROM SOMEWHERE ELSE:             if (eventStartTS - currEvent.reminder1Minutes * 60 > currentSeconds) {

            // [MB] added extraction of acks
            //val reminderSeconds = validReminders.reversed().map { Pair(it.minutes * 60, it.ack)  }
            long min_notifTS=-1L;

            for(ExtendedCalendarEvent.Reminder r : reminders) {
                int reminderSeconds = r.minutes * 60;
                long reminderAck = r.ack;

                if (r.type != ExtendedCalendarEvent.Reminder.REMINDER_NOTIFICATION) continue;

                if (r.absoluteTS == -1L) {
                    Date refStartDate;
                    Date refEndDate;
                    if (rrule!=null) {
                        refStartDate=getFirstActiveInstance(id,startDate,rrule,r,title);
                        if (refStartDate==null) continue;
                        refEndDate=secondsToDate(dateToSeconds(refStartDate)+(dateToSeconds(endDate)-dateToSeconds(startDate)));
                    }
                    else {
                        refStartDate = startDate;
                        refEndDate = endDate;
                        // [MB] check if the reminder has already been ack'ed
                        if (dateToSeconds(refStartDate) - reminderSeconds <= reminderAck) {
                            Log.v("MYCALENDAR", "in Context.scheduleNextEventReminder(" + title + "; id=" + id + " in curReminder: start-currem is leq ack. Skipping");
                            Log.v("MYCALENDAR", "   info: type=" + r.type + "; minutes=" + r.minutes + "; absoluteTS=" + r.absoluteTS + "; ack=" + r.ack + ";title=" + title);
                            continue;
                        }
                    }
                    long calc_notif_time = (dateToSeconds(refStartDate) - reminderSeconds) * 1000L;
                    if (min_notifTS == -1L || calc_notif_time < min_notifTS) {
                        min_notifTS = calc_notif_time;
                        startDate=refStartDate;
                        endDate=refEndDate;
                        Log.v("MYCALENDAR", "in Context.scheduleNextEventReminder(" + title + "; id=" + id + "): found currently-best notif TS=" + min_notifTS + " via relative reminder seconds=" + reminderSeconds);
                    }
                } else if (r.absoluteTS > r.ack &&
                           (min_notifTS == -1L || r.absoluteTS * 1000L < min_notifTS)) {
                    min_notifTS = r.absoluteTS * 1000L;
                    if (rrule!=null) {
                        Date refStartDate=getFirstActiveInstance(id,startDate,rrule,r,title);
                        if (refStartDate!=null) {
                            // if we can find a start date for a valid recurrence, let's use that
                            startDate=refStartDate;
                            endDate = secondsToDate(dateToSeconds(refStartDate) + (dateToSeconds(endDate) - dateToSeconds(startDate)));
                        }
                    }
                    Log.v("MYCALENDAR", "in Context.scheduleNextEventReminder(" + title + "; id=" + id + "): found currently-best notif TS=" + min_notifTS + " via absolute reminder");
                }
            }
            boolean create_notification=false;
            if (min_notifTS != -1L) {
                Log.w("MYCALENDAR", "found min reminder with TS=" + min_notifTS+"; title="+title);
                if ((dateToSeconds(now)+(LOOKAHEAD_MINUTES*60)>=(min_notifTS/1000L))) {
                    Log.w("MYCALENDAR", "found TRIGGERABLE (within lookahead) reminder with TS=" + millisToDate(min_notifTS) + "; title=" + title);
                    create_notification = true;
                }
            }

            if (Settings.getBoolPref(AgendaWidgetApplication.getContext(), "debug", appWidgetId) && !create_notification && isForcedDisplayEvent(title)) {
                create_notification=true; // if we are debugging, force the displaying of all "Test xxx" events
                title="*"+title;
                min_notifTS=dateToMillis(now); // Make sure it is triggerable right now
            }

            if (!create_notification) continue; // no need to add to the calendar

            Log.w("MYCALENDAR","ADDING calendarEvent title="+title+"; allDay="+allDay+"; startDate="+startDate+"; endDate="+cur.getLong(8)+"/"+endDate+"/"+endDate.getTime()+"; duration="+duration+"; rrule="+rrule+"; originalId="+originalId+"; hasAlarm="+hasAlarm+"; calendarId="+calendarId+"; eventTZ="+eventTimezone+"; calendarTZ="+ calendarTimezone+"; # reminders="+reminders.size());

                /*

=============
            // I MUST FOLLOW: Context.scheduleNextEventReminder
            // May need to use: it.minutes != REMINDER_OFF

common branch
        if (min_notifTS!=-1L) {
            notificationScheduled = true  // no matter what happens next, we have effectively scheduled a notification for the event
            val notif_present = event.id!! in EventsHelper.notif_list && EventsHelper.notif_list[event.id!!] == min_notifTS
            Log.v("MINE", "in Context.scheduleNextEventReminder(" + event.title + "; id=${event.id}): notification already present?" + notif_present + "; id=" + event.id!!)
            Log.v("MINE", "in Context.scheduleNextEventReminder(" + event.title + "; id=${event.id}): (list has " + EventsHelper.notif_list.size + " elements)")
            if (!notif_present) {
                if (event.id!! in EventsHelper.notif_list) {
                    // remove earlier notifications if they exist
                    cancelNotification(event.id!!)
            }
            Log.v("MINE", "in Context.scheduleNextEventReminder(" + event.title + "; id=${event.id})  WILL schedule reminder!! For " + min_notifTS + "; curr time=" + System.currentTimeMillis() + "; event start=" + event.getEventStartTS() + "; notif TS=" + min_notifTS)
            scheduleEventIn(min_notifTS, event, showToasts)
        }
    }


            */

            ExtendedCalendarEvent e = new ExtendedCalendarEvent(id, color, title, location, description, startDate, endDate, allDay, calendarId, rrule, reminders, millisToDate(min_notifTS));
            Events.adjustAllDayEvents(e);
            calendarEvents.add(e);

/*
            if ((allDay && now.compareTo(e.getEndDate()) < 0) || (!allDay && now.compareTo(e.getEndDate()) <= 0)) {
                if (Settings.getBoolPref(AgendaWidgetApplication.getContext(), "repeatMultidayEvents", appWidgetId) && e.isMultiDay()) {
                    calendarEvents.addAll(e.getMultidayEventsList(selectedRangeEnd));
                } else {
                    calendarEvents.add(e);
                }
            }
*/
        }
        cur.close();

        Log.w("MYCALENDAR","calendarEvents size="+calendarEvents.size());

        return(calendarEvents);
    }
}

class ExtString {
    String s;
    public ExtString(String s) {
        this.s=s;
    }

    public ExtString substringAfter(String x,String fallback) {
        int p=s.indexOf(x);
        if (p==-1) return(new ExtString(fallback));
        return(new ExtString(s.substring(p+x.length())));
    }

    public ExtString substringAfter(String x) {
        return(substringAfter(x,s));
    }

    public ExtString substringBefore(String x,String fallback) {
        int p=s.indexOf(x);
        if (p==-1) return(new ExtString(fallback));
        return(new ExtString(s.substring(0,p)));
    }

    public ExtString substringBefore(String x) {
        return(substringBefore(x,s));
    }

        public String toString() {
        return(s);
    }
}