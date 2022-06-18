package gr.ictpro.jsalatas.agendawidget.model.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.Events;
import gr.ictpro.jsalatas.agendawidget.model.settings.Settings;


public interface CalendarFetchAdapter {
    List<EventItem> fetchCalendarEvents(int appWidgetId,String[] calendarsList,Date selectedRangeStart,Date selectedRangeEnd);
}
