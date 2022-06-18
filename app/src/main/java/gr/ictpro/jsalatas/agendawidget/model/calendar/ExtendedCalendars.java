package gr.ictpro.jsalatas.agendawidget.model.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.Events;
import gr.ictpro.jsalatas.agendawidget.model.settings.Settings;
import gr.ictpro.jsalatas.agendawidget.utils.DateUtils;

public class ExtendedCalendars extends Calendars {

    public static List<EventItem> getEvents(int appWidgetId) {
        return(getEvents(appWidgetId,new ExtendedCalendarFetchAdapter()));
    }
}

class ExtendedCalendarFetchAdapter implements CalendarFetchAdapter {

    /* parameters for debugging */
    final static boolean useDateRange=false; // only fetch events from the given date range

    public List<EventItem> fetchCalendarEvents(int appWidgetId,String[] calendarsList,Date selectedRangeStart,Date selectedRangeEnd) {
        List<EventItem> calendarEvents = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        for (String calendar : calendarsList) {
            if (!sb.toString().isEmpty()) {
                sb.append(" OR ");
            }
            sb.append(CalendarContract.Events.CALENDAR_ID).append(" = ").append(calendar);
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
        };

        long id;
        @ColorInt int color;
        String title;
        String location;
        String description;
        Date startDate;
        Date endDate;
        boolean allDay;


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
            title = cur.getString(2);
            location = cur.getString(3);
            description = cur.getString(4);
            allDay = cur.getInt(9) == 1;
            calendarInstance.setTimeInMillis(cur.getLong(6));
            startDate = calendarInstance.getTime();
            calendarInstance.setTimeInMillis(cur.getLong(8));
            endDate = calendarInstance.getTime();

            CalendarEvent e = new CalendarEvent(id, color, title, location, description, startDate, endDate, allDay);
            Events.adjustAllDayEvents(e);

            if ((allDay && now.compareTo(e.getEndDate()) < 0) || (!allDay && now.compareTo(e.getEndDate()) <= 0)) {
                if (Settings.getBoolPref(AgendaWidgetApplication.getContext(), "repeatMultidayEvents", appWidgetId) && e.isMultiDay()) {
                    calendarEvents.addAll(e.getMultidayEventsList(selectedRangeEnd));
                } else {
                    calendarEvents.add(e);
                }
            }
        }
        cur.close();

        return(calendarEvents);
    }
}
