
package gr.ictpro.jsalatas.agendawidget.ui;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gr.ictpro.jsalatas.agendawidget.R;
import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.Events;
import gr.ictpro.jsalatas.agendawidget.model.calendar.CalendarEvent;
import gr.ictpro.jsalatas.agendawidget.model.calendar.DayGroup;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendars;
import gr.ictpro.jsalatas.agendawidget.model.settings.*;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskContract;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskEvent;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskProvider;
import gr.ictpro.jsalatas.agendawidget.utils.DateUtils;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.Calendars.refreshCalendarList;
import static gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendars.refreshOneEvent;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.secondsToDate;

public class MainActivity extends AppCompatActivity {
    private Context context;
    private static final String ACTION_FORCE_UPDATE = "gr.ictpro.jsalatas.agendawidget.action.FORCE_UPDATE";
    private static final String ACTION_PROVIDER_REMOVED = "gr.ictpro.jsalatas.agendawidget.action.PROVIDER_REMOVED";
    private static final String EXTRA_PACKAGE_NAME = "gr.ictpro.jsalatas.agendawidget.action.EXTRA_PACKAGE_NAME";

    public static int appWidgetId = 1;

    public static boolean NEW_INTENTS = true;//false;

    // Look in AgendaWidget for a possibly better CalendarObserver
    CalendarObserver observer = new CalendarObserver("", new Handler());
    private final static long CALENDAR_REFRESH_DELAY = 60; // how many seconds to wait before refreshing the list when a calendar change event is received
    private final static long CALENDAR_MAX_REFRESH_WAIT = 120; // how many seconds we accept to wait when change notifications keep coming in

    final static String NOTIFICATION_CHANNEL_ID = "Reminder Notification Channel";
    final static String EXTRA_EVENT_ID = "eventId";
    // ID for grouping notifications
    final static String MY_NOTIFICATION_GROUP_ID = "Notification Group";
    final static int SUMMARY_NOTIFICATION_ID = -1;
    // Notification intent actions
    final static String ACTION_VIEW = "view";
    final static String ACTION_SNOOZE = "snooze";
    final static String ACTION_DISMISS = "dismiss";

    // Stay open when keyboard is connected/disconnected
    // https://stackoverflow.com/questions/4116058/avoiding-application-restart-when-hardware-keyboard-opens
    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        // Ignore orientation change to keep activity from restarting
        super.onConfigurationChanged(newConfig);
    }

    SpannableString formatDate(Context appContext, CalendarEvent calendarEvent) {
        return(formatDate(appContext, calendarEvent, true));
    }

    SpannableString formatDate(Context appContext, CalendarEvent calendarEvent, boolean used_as_prefix) {
        StringBuilder sb = new StringBuilder();
        //boolean startIsToday = DateUtils.isInSameDay(calendarEvent.getStartDate(), now);
        boolean startIsToday = false;
        boolean addSpace = false;

        boolean isTask = calendarEvent instanceof TaskEvent;

        if (!isTask) {
            if (startIsToday && !calendarEvent.isAllDay()) {
                sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", appWidgetId), calendarEvent.getStartDate()));
            } else /*if (calendarEvent.getStartDate().compareTo(now) > 0)*/ {
                if (!Settings.getBoolPref(appContext, "groupByDate", appWidgetId)) {
                    sb.append(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", appWidgetId), calendarEvent.getStartDate()));
                }
                if (!calendarEvent.isAllDay() && DateUtils.dayFloor(calendarEvent.getStartDate()).compareTo(calendarEvent.getStartDate()) != 0) {
                    if (!Settings.getBoolPref(appContext, "groupByDate", appWidgetId)) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", appWidgetId), calendarEvent.getStartDate()));
                }
                addSpace = true;
            }
            if (calendarEvent.isAllDay()) {
                if (Settings.getBoolPref(appContext, "showAllDay", appWidgetId)) {
                    if (addSpace) {
                        sb.append(" ");
                    }
                    sb.append("(").append(appContext.getString(R.string.all_day)).append(")");
                }
            } else {
                sb.append(" -");
            }
        }

        if (calendarEvent.getEndDate().getTime() != 0) {
            //boolean endIsToday = DateUtils.isInSameDay(calendarEvent.getEndDate(), now);
            boolean endIsToday = false;
            boolean showDue = calendarEvent instanceof TaskEvent && !endIsToday;
            if (endIsToday && !calendarEvent.isAllDay()) {
                if (!isTask) {
                    sb.append(" ");
                }
                sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", appWidgetId), calendarEvent.getEndDate()));
            } else if (!calendarEvent.isAllDay() || isTask) {
                if (showDue || (!DateUtils.isInSameDay(calendarEvent.getStartDate(), calendarEvent.getEndDate()) && !Settings.getBoolPref(appContext, "repeatMultidayEvents", appWidgetId))) {
                    if (!isTask) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", appWidgetId), calendarEvent.getEndDate()));
                }
                if (DateUtils.dayFloor(calendarEvent.getEndDate()).compareTo(calendarEvent.getEndDate()) != 0) {
                    if (!sb.toString().isEmpty()) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", appWidgetId), calendarEvent.getEndDate()));
                }
            }

            if (sb.toString().endsWith("-")) {
                sb.append(" ");
            }
        }
        if (used_as_prefix && !sb.toString().isEmpty()) {
            sb.append(": ");
        }

        return(new SpannableString(sb.toString()));
    }

    SpannableString formatTitle(Context appContext, CalendarEvent calendarEvent) {
        SpannableString spanTitle = new SpannableString(calendarEvent.getTitle());
        return(spanTitle);
    }

    public class MySimpleArrayAdapter extends ArrayAdapter<String> {
        private final Context context;
        private final String[] values;

        private void setOnClick(final Button btn, final int val){
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    // Do whatever you want(str can be used here)

                }
            });
        }

        public MySimpleArrayAdapter(Context context,String[] values) {
            super(context, R.layout.calendar_event_layout, values);
            this.context = context;
            this.values = values;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            /*
            if (position==0) {
                newNotifications=new ArrayList<>();
            }
             */

            if (position>=AgendaUpdateService.events.size())
                return(inflater.inflate(R.layout.calendar_event_layout, parent, false));

            EventItem item = AgendaUpdateService.events.get(position);

            Context appContext = context;
            View v;

            if (item instanceof DayGroup) {
                v = inflater.inflate(R.layout.calendar_event_header_layout, parent, false);
                ((TextView) v.findViewById(R.id.tvDate)).setText(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", appWidgetId), item.getStartDate()));
            } else {
                // FIXME: This is a mess. I wish I knew how to make it cleaner :*
                CalendarEvent calendarEvent = (CalendarEvent) item;
                boolean isTask = calendarEvent instanceof TaskEvent;
                if (isTask) {
                    v = inflater.inflate(R.layout.task_event_layout, parent, false);
                    TaskContract tasks = TaskProvider.getTaskContract(Settings.getStringPref(AgendaWidgetApplication.getContext(), "taskProvider", appWidgetId));
                    //v.setInt(R.id.imgTaskPriority, "setColorFilter", tasks.getPriorityColor((TaskEvent)calendarEvent));
                    ((ImageButton) v.findViewById(R.id.imgTaskPriority)).setColorFilter(tasks.getPriorityColor((TaskEvent) calendarEvent));

                } else { //item instanceof CalendarEvent
                    v = inflater.inflate(R.layout.calendar_event_layout, parent, false);
                    //v.setInt(R.id.viewCalendarColor, "setBackgroundColor", calendarEvent.getColor());
                    ((LinearLayout) v.findViewById(R.id.viewCalendarColor)).setBackgroundColor(calendarEvent.getColor());
                }

                Date now = GregorianCalendar.getInstance().getTime();

                /*
                boolean isToday = (isTask && calendarEvent.getEndDate().getTime() != 0
                        && ((Settings.getBoolPref(AgendaWidgetApplication.getContext(), "showOverdueTasks", appWidgetId) && calendarEvent.getEndDate().compareTo(now) <= 0)
                        || (calendarEvent.getEndDate().compareTo(now) <= 0 && DateUtils.isInSameDay(calendarEvent.getEndDate(), now)))) ||
                        (!isTask && (calendarEvent.containsDate(now) ||
                                DateUtils.isInSameDay(calendarEvent.getStartDate(), now) ||
                                DateUtils.isInSameDay(calendarEvent.getEndDate(), now)));
                 */
                boolean isToday=false;

                @ColorInt int dateTitleColor = Color.parseColor(isToday ?
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "todayDateTitleColor", appWidgetId) :
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "dateTitleColor", appWidgetId));

                //v.setInt(R.id.tvDate, "setTextColor", dateTitleColor);
                ((TextView)v.findViewById(R.id.tvDate)).setTextColor(dateTitleColor);
                //v.setInt(R.id.tvTitle, "setTextColor", dateTitleColor);
                ((TextView)v.findViewById(R.id.tvTitle)).setTextColor(dateTitleColor);

                SpannableString spanDate = formatDate(appContext,calendarEvent); //new SpannableString(sb.toString());
                SpannableString spanTitle = formatTitle(appContext,calendarEvent); //new SpannableString(calendarEvent.getTitle());
                if (Settings.getBoolPref(appContext, "todayBold", appWidgetId) && isToday) {
                    spanDate.setSpan(new StyleSpan(Typeface.BOLD), 0, spanDate.toString().length()/*sb.toString().length()*/, 0);
                    spanTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, spanTitle.toString().length()/*calendarEvent.getTitle().length()*/, 0);
                }
                //v.setTextViewText(R.id.tvDate, spanDate);
                ((TextView) v.findViewById(R.id.tvDate)).setText(spanDate);
                //v.setTextViewText(R.id.tvTitle, spanTitle);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(spanTitle);

                @ColorInt int locationNotesColor = Color.parseColor(isToday ?
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "todayLocationNotesColor", appWidgetId) :
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "locationNotesColor", appWidgetId));

                //v.setInt(R.id.tvLocation, "setTextColor", locationNotesColor);
                ((TextView) v.findViewById(R.id.tvLocation)).setTextColor(locationNotesColor);
                //v.setInt(R.id.imgLocation, "setColorFilter", locationNotesColor);
                ((ImageButton) v.findViewById(R.id.imgLocation)).setColorFilter(locationNotesColor);
                //v.setInt(R.id.tvNotes, "setTextColor", locationNotesColor);
                ((TextView)v.findViewById(R.id.tvNotes)).setTextColor(locationNotesColor);
                //v.setInt(R.id.imgNotes, "setColorFilter", locationNotesColor);
                ((ImageButton) v.findViewById(R.id.imgNotes)).setColorFilter(locationNotesColor);


                boolean showLocation = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", appWidgetId) ? Settings.getBoolPref(appContext, "showLocationTasks", appWidgetId) : Settings.getBoolPref(appContext, "showLocation", appWidgetId);
                if (showLocation && calendarEvent.getLocation() != null && !calendarEvent.getLocation().isEmpty()) {
                    //v.setTextViewText(R.id.tvLocation, calendarEvent.getLocation());
                    ((TextView) v.findViewById(R.id.tvLocation)).setText(calendarEvent.getLocation());
                    //v.setInt(R.id.tvLocation, "setVisibility", View.VISIBLE);
                    ((TextView) v.findViewById(R.id.tvLocation)).setVisibility(View.VISIBLE);
                    //v.setInt(R.id.imgLocation, "setVisibility", View.VISIBLE);
                    ((ImageButton) v.findViewById(R.id.imgLocation)).setVisibility(View.VISIBLE);
                } else {
                    //v.setTextViewText(R.id.tvLocation, "");
                    ((TextView) v.findViewById(R.id.tvLocation)).setText("");
                    //v.setInt(R.id.tvLocation, "setVisibility", View.GONE);
                    ((TextView) v.findViewById(R.id.tvLocation)).setVisibility(View.GONE);
                    //v.setInt(R.id.imgLocation, "setVisibility", View.GONE);
                    ((ImageButton) v.findViewById(R.id.imgLocation)).setVisibility(View.GONE);
                }

                boolean showNotes = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", appWidgetId) ? Settings.getBoolPref(appContext, "showNotesTasks", appWidgetId) : Settings.getBoolPref(appContext, "showNotes", appWidgetId);
                if (showNotes && calendarEvent.getDescription() != null && !calendarEvent.getDescription().isEmpty()) {
                    //v.setTextViewText(R.id.tvNotes, calendarEvent.getDescription());
                    ((TextView) v.findViewById(R.id.tvNotes)).setText(calendarEvent.getDescription());
                    //v.setInt(R.id.tvNotes, "setVisibility", View.VISIBLE);
                    ((TextView) v.findViewById(R.id.tvNotes)).setVisibility(View.VISIBLE);
                    //v.setInt(R.id.imgNotes, "setVisibility", View.VISIBLE);
                    ((ImageButton) v.findViewById(R.id.imgNotes)).setVisibility(View.VISIBLE);
                    int notesMaxLines = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", appWidgetId) ? Settings.getIntPref(appContext, "notesMaxLinesTasks", appWidgetId) : Settings.getIntPref(appContext, "notesMaxLines", appWidgetId);
                    //v.setInt(R.id.tvNotes, "setMaxLines", notesMaxLines);
                    ((TextView) v.findViewById(R.id.tvNotes)).setMaxLines(notesMaxLines);
                } else {
                    //v.setTextViewText(R.id.tvNotes, "");
                    ((TextView) v.findViewById(R.id.tvNotes)).setText("");
                    //v.setInt(R.id.tvNotes, "setVisibility", View.GONE);
                    ((TextView) v.findViewById(R.id.tvNotes)).setVisibility(View.GONE);
                    //v.setInt(R.id.imgNotes, "setVisibility", View.GONE);
                    ((ImageButton) v.findViewById(R.id.imgNotes)).setVisibility(View.GONE);
                }

                if (calendarEvent instanceof TaskEvent) {
                    TaskContract tasks = TaskProvider.getTaskContract(Settings.getStringPref(AgendaWidgetApplication.getContext(), "taskProvider", appWidgetId));
                    //v.setOnClickFillInIntent(R.id.viewCalendarEvent, tasks.getIntent((TaskEvent) calendarEvent));
                } else {
                    Uri contentUri = CalendarContract.Events.CONTENT_URI;
                    Uri uri = ContentUris.withAppendedId(contentUri, calendarEvent.getId());
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
                    //v.setOnClickFillInIntent(R.id.viewCalendarEvent, intent);

                }

                Button ibtn=v.findViewById(R.id.btn_snooze);
                ibtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","snooze button clicked for entry #"+position);
                        EventItem item = AgendaUpdateService.events.get(position);
                    }
                });
                Button btn=((Button) v.findViewById(R.id.btn_dismiss));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dismiss button clicked for entry #"+position);
                        EventItem item = AgendaUpdateService.events.get(position);

                        if (item instanceof ExtendedCalendarEvent) {
                            ExtendedCalendars.dismissCalDAVEventReminders(item);
                            //events.remove(position);
                            removeNotification(context,((ExtendedCalendarEvent) item).getId());
                            synchronized(this) {
                                AgendaUpdateService.events = refreshOneEvent(appWidgetId, ((ExtendedCalendarEvent) item).getId(), AgendaUpdateService.events);
                                refreshListCached();
                            }
                        }
                    }
                });
                btn=((Button) v.findViewById(R.id.btn_dump));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dump button clicked for entry #"+position);
                        EventItem item = AgendaUpdateService.events.get(position);
                        if (item instanceof ExtendedCalendarEvent) {
                            ((ExtendedCalendarEvent)item).dumpExtendedPropertiesForEvent();
                        }
                    }
                });
                btn=((Button) v.findViewById(R.id.btn_test));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!AgendaWidgetConfigureActivity.checkForPermission(AgendaWidgetApplication.getActivity(context), Manifest.permission.READ_CALENDAR, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_READ_CALENDAR, false)) {
                            ActivityCompat.requestPermissions(AgendaWidgetApplication.getActivity(context), new String[]{Manifest.permission.READ_CALENDAR}, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_READ_CALENDAR);
                            return;
                        }
                        if (!AgendaWidgetConfigureActivity.checkForPermission(AgendaWidgetApplication.getActivity(context), Manifest.permission.WRITE_CALENDAR, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_WRITE_CALENDAR, false)) {
                            ActivityCompat.requestPermissions(AgendaWidgetApplication.getActivity(context), new String[]{Manifest.permission.WRITE_CALENDAR}, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_WRITE_CALENDAR);
                            return;
                        }

                        Log.w("MYCALENDAR","run-tests button clicked for entry #"+position);
                        EventItem item = AgendaUpdateService.events.get(position);
                        if (item instanceof ExtendedCalendarEvent) {
                            ((ExtendedCalendarEvent)item).runTests();
                        }
                    }
                });

                /*
                // create the notification
                SpannableString spanDate = formatDate(appContext,calendarEvent); //new SpannableString(sb.toString());
                SpannableString spanTitle = formatTitle(appContext,calendarEvent); //new SpannableString(calendarEvent.getTitle());
                createNotification(calendarEvent.getId(),spanTitle.toString(),spanDate.toString());
                newNotifications.add(calendarEvent.getId());
                 */
            }

            /*
            if (position==events.size()-1) {
                // We are done with the last element. Remove all leftover notifications
                for(Long eventId : currentNotifications) {
                    if (!newNotifications.contains(eventId)) {
                        removeNotification(eventId.longValue());
                    }
                }
                currentNotifications=new ArrayList<>(newNotifications);
                if (currentNotifications.size()>1) {
                    createSummaryNotification();
                }
                else {
                    removeSummaryNotification();
                }
            }
            */

            return(v);
        }
    }

    public class CalendarObserver extends ContentObserver
    {   String calendar;
        CalendarObserver myself;
        Timer scheduledTimer;
        long curTimeSecAtWaitStart;

        public CalendarObserver(String calendar,Handler handler) {
            super(handler);
            this.calendar=calendar;
            this.myself=this;
            this.scheduledTimer=null;
            curTimeSecAtWaitStart=-1;
        }

        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public void clearTimer() {
            Log.v("MYCALENDAR", "clearing any pending CalendarObserver timers");
            if (scheduledTimer!=null) {
                scheduledTimer.cancel();
                scheduledTimer=null;
            }
        }

        void executeRefresh() {
          Log.v("MYCALENDAR", "Inside timer's async task");
          //getContentResolver().unregisterContentObserver(myself);
          refreshList();
          //getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false/*true*/, myself);
          Log.v("MYCALENDAR", "Done with timer's async task");
        }

        // Implement the onChange(boolean, Uri) method to take advantage of the new Uri argument.
        @Override
        public synchronized void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange);
            if (!selfChange) {
                Log.v("MYCALENDAR", "Calendar changes; selfChange=" + selfChange + "; uri=" + uri + "; calendar=" + calendar);
                if (scheduledTimer==null) {
                    curTimeSecAtWaitStart=System.currentTimeMillis() / 1000L;
                    Log.v("MYCALENDAR", "First notification of a change at "+secondsToDate(curTimeSecAtWaitStart)+"; will wait for "+CALENDAR_MAX_REFRESH_WAIT+" sec max");
                }
                else {
                    clearTimer();
                    if (((System.currentTimeMillis() / 1000L)-curTimeSecAtWaitStart)>=CALENDAR_MAX_REFRESH_WAIT) {
                        Log.v("MYCALENDAR", "We waited more than "+CALENDAR_MAX_REFRESH_WAIT+" sec for changes to stop. Canceling timer and forcing a refresh");
                        executeRefresh();
                        return;
                    }
                }
                scheduledTimer=new Timer();
                Log.v("MYCALENDAR", "Scheduling timer for "+CALENDAR_REFRESH_DELAY+" sec");
                scheduledTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.v("MYCALENDAR", "Timer run method is starting. About to execute async task");
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                clearTimer();
                                executeRefresh();
                            }
                        });
                    }
                }, CALENDAR_REFRESH_DELAY*1000L);
            }
        }
    }

    void setupObserver() {
        /*
            https://stackoverflow.com/questions/9440993/how-to-get-calendar-change-events-is-calendar-observer-working
            https://stackoverflow.com/questions/22245604/detect-changes-on-a-native-android-calendar
            https://www.grokkingandroid.com/use-contentobserver-to-listen-to-changes/
            https://stackoverflow.com/questions/4618591/android-cursor-registercontentobserver
            https://stackoverflow.com/questions/34724101/cursor-registercontentobserver-vs-contentresolver-registercontentobserver
         */

        /*
            IMPORTANT: to set up notifications: https://stackoverflow.com/questions/21168766/listen-for-new-calendar-events
         */

        /*
        refreshCalendarList();

        String[] calendarsList = Settings.getStringPref(AgendaWidgetApplication.getContext(), "calendars", appWidgetId).split("@@@");
        if (calendarsList.length == 1 && calendarsList[0].isEmpty()) {
            Log.v("MYCALENDAR","No calendars -- not registering any observers");
            return;
        }
        for (String calendar : calendarsList) {
            CalendarObserver observer = new CalendarObserver(calendar,new Handler());
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    Uri MYAPP_CONTENT_URI = CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.Calendars._ID, calendar).build();
                    Log.v("MYCALENDAR","Registering for "+MYAPP_CONTENT_URI);
                    getContentResolver().registerContentObserver(MYAPP_CONTENT_URI, false, observer);
                }
            });
        }
        */
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false/*true*/, observer);
            }
        });

        /*
        String[] projection = new String[] { "_id" };
        Uri calendars = Uri.parse("content://calendar/calendars");

        Cursor managedCursor = managedQuery(calendars, projection, null, null, null);
        managedCursor.registerContentObserver(new CalendarObserver(new Handler()));
         */

        /*
        getContentResolver().
                registerContentObserver(
                        calendars,
                        true,
                        new CalendarObserver(new Handler()));
         */

        /* FROM Simple Calendar

    fun Context.syncCalDAVCalendars(callback: () -> Unit) {
        calDAVRefreshCallback = callback
        ensureBackgroundThread {
            val uri = CalendarContract.Calendars.CONTENT_URI
            contentResolver.unregisterContentObserver(calDAVSyncObserver)
            contentResolver.registerContentObserver(uri, false, calDAVSyncObserver)
            refreshCalDAVCalendars(config.caldavSyncedCalendarIds, true)
        }
    }

    // caldav refresh content observer triggers multiple times in a row at updating, so call the callback only a few seconds after the (hopefully) last one
    private val calDAVSyncObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (!selfChange) {
                calDAVRefreshHandler.removeCallbacksAndMessages(null)
                calDAVRefreshHandler.postDelayed({
                    ensureBackgroundThread {
                        unregisterObserver()
                        calDAVRefreshCallback?.invoke()
                        calDAVRefreshCallback = null
                    }
                }, CALDAV_REFRESH_DELAY)
            }
        }
    }

         */
    }

    private void createNotificationChannel(String CHANNEL_ID) {
        if (AgendaUpdateService.notificationChannel!=null) return;
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Reminders channel";
            String description = "Channel for the reminder notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            AgendaUpdateService.notificationChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            AgendaUpdateService.notificationChannel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(AgendaUpdateService.notificationChannel);
        }
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_DISMISS)) {
                int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                if (id==-1) return;
                Log.v("MYCALENDAR","got dismiss for id="+id);
                long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                if (eventId==-1) return;
                Log.v("MYCALENDAR","got dismiss for eventid="+eventId);

                for(EventItem item : AgendaUpdateService.events) {
                    if (item instanceof ExtendedCalendarEvent) {
                        ExtendedCalendarEvent event=(ExtendedCalendarEvent)item;
                        if (event.getId()==eventId) {
                            ExtendedCalendars.dismissCalDAVEventReminders(event);
                            removeNotification(context,event.getId());
                            synchronized (this) {
                                AgendaUpdateService.events = refreshOneEvent(appWidgetId, event.getId(), AgendaUpdateService.events);
                                refreshListCached();
                            }
                            return;
                        }
                    }
                }
                // TODO: this should never happen
                Log.e("MYCALENDAR","Dismiss clicked for event with id="+eventId+", but no such id was found in events. Ignoring");
            }
            else if (intent.getAction().equals(ACTION_VIEW)) {
                //int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                //if (id==-1) return;
                long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                if (eventId==-1) return;
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                        .setData(uri);
                startActivity(viewIntent);
            }
            else {
                Log.v("MYCALENDAR","Received a broadcast!");
                StringBuilder sb = new StringBuilder();
                sb.append("Action: " + intent.getAction() + "\n");
                sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                sb.append("Extras:"+intent.getExtras());
                String log = sb.toString();
                Log.d("MYCALENDAR", log);
                Toast.makeText(context, log, Toast.LENGTH_LONG).show();
            }
        }
    }

    void setupBroadcastReceiver() {
        if (AgendaUpdateService.br==null) {
            AgendaUpdateService.br=new MyBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
//            IntentFilter filter = new IntentFilter(ACTION_VIEW);
            filter.addAction(ACTION_VIEW);
//            this.registerReceiver(AgendaUpdateService.br, filter);
//            filter = new IntentFilter(ACTION_SNOOZE);
            filter.addAction(ACTION_SNOOZE);
//            this.registerReceiver(AgendaUpdateService.br, filter);
//            filter = new IntentFilter(ACTION_DISMISS);
            filter.addAction(ACTION_DISMISS);
            this.registerReceiver(AgendaUpdateService.br, filter);
        }
    }

    PendingIntent createActionIntent(int id,long eventId,String action) {
        if (!NEW_INTENTS) setupBroadcastReceiver();

        Log.v("MYCALENDAR","creating intent with id="+id+" and event id="+eventId+" for action "+action);

        Intent actionIntent = new Intent(action);
        actionIntent.setAction(action);
        actionIntent.putExtra(EXTRA_NOTIFICATION_ID, id);
        actionIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent actionPendingIntent =
                PendingIntent.getBroadcast(context, id, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Note: In Android 10 (API level 29) and higher, the platform automatically generates notification action buttons if an app does not provide its own. If you don't want your app's notifications to display any suggested replies or actions, you can opt-out of system-generated replies and actions by using setAllowGeneratedReplies() and setAllowSystemGeneratedContextualActions().

        return(actionPendingIntent);
    }

    int generateNotificationId(long eventId) {
        synchronized(AgendaUpdateService.notifiedEventIDs) {
            int firstEmpty = -1;
            for (int i = 0; i < AgendaUpdateService.notifiedEventIDs.size(); i++) {
                if (AgendaUpdateService.notifiedEventIDs.get(i) == null) {
                    if (firstEmpty!=-1) firstEmpty = i;
                }
                else if (AgendaUpdateService.notifiedEventIDs.get(i) == eventId) {
                    return (i);
                }
            }
            if (firstEmpty == -1) {
                firstEmpty = AgendaUpdateService.notifiedEventIDs.size();
                AgendaUpdateService.notifiedEventIDs.add(null);
            }
            AgendaUpdateService.notifiedEventIDs.set(firstEmpty, eventId);
            return (firstEmpty);
        }
    }

    static void removeNotificationId(int id) {
        synchronized(AgendaUpdateService.notifiedEventIDs) {
            if (id < AgendaUpdateService.notifiedEventIDs.size()) {
                AgendaUpdateService.notifiedEventIDs.set(id, null);
            }
        }
    }

    void createNotification(long eventId,String title,String descr) {
        createNotificationChannel(NOTIFICATION_CHANNEL_ID);

        int id=generateNotificationId(eventId);
        PendingIntent tapIntent=createActionIntent(id,eventId,ACTION_VIEW);
        PendingIntent snoozePendingIntent=createActionIntent(id,eventId,ACTION_SNOOZE);
        PendingIntent dismissPendingIntent=createActionIntent(id,eventId,ACTION_DISMISS);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle(title)
                .setContentText(descr)
//                .setStyle(new NotificationCompat.BigTextStyle()
//                        .bigText("Much longer text that cannot fit one line..."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                // Do not trigger an alert if the notification is already showing and is updated
                .setOnlyAlertOnce(true)
                // Group the notifications
                // https://developer.android.com/training/notify-user/group
                // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                //
                // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                //
                .setGroup(MY_NOTIFICATION_GROUP_ID)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(tapIntent)
                .addAction(R.drawable.ic_calendar_event/*ic_snooze*/, getString(R.string.snooze),
                        snoozePendingIntent)
                .addAction(R.drawable.ic_calendar_event/*ic_snooze*/, getString(R.string.dismiss),
                        dismissPendingIntent)
                .setOngoing(true) // prevent the user from swiping it away
// Automatically remove the notification when the user clicks on it
//                .setAutoCancel(true)
                ;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // id is a unique int for each notification that you must define
        notificationManager.notify(id, builder.build());

        // Prevent app from being killed
        // https://stackoverflow.com/questions/34573109/how-to-make-an-android-app-to-always-run-in-background
        // https://developer.android.com/training/run-background-service/create-service
    }

    static void removeNotification(Context ctx,long eventId) {
        int id = -1;
        synchronized(AgendaUpdateService.notifiedEventIDs) {
            for (int i = 0; i < AgendaUpdateService.notifiedEventIDs.size(); i++) {
                if (AgendaUpdateService.notifiedEventIDs.get(i)!=null && AgendaUpdateService.notifiedEventIDs.get(i) == eventId) {
                    id=i;
                    break;
                }
            }
        }
        if (id!=-1) {
            removeNotificationId(id);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
            notificationManager.cancel(id);
        }
    }

    void createSummaryNotification() {
        Notification summaryNotification =
                new NotificationCompat.Builder(MainActivity.this, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("Reminders")
                        //set content text to support devices running API level < 24
                        .setContentText("Reminders available")
                        .setSmallIcon(R.drawable.ic_notes)
/*
                        //build summary info into InboxStyle template
                        .setStyle(new NotificationCompat.InboxStyle()
                                .addLine("Alex Faarborg  Check this out")
                                .addLine("Jeff Chang    Launch Party")
                                .setBigContentTitle("2 new messages")
                                .setSummaryText("janedoe@example.com"))
 */
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        // Do not trigger an alert if the notification is already showing and is updated
                        .setOnlyAlertOnce(true)
                        // Group the notifications
                        // https://developer.android.com/training/notify-user/group
                        // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                        //
                        // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                        //
                        .setGroup(MY_NOTIFICATION_GROUP_ID)
                        //set this notification as the summary for the group
                        .setGroupSummary(true)
                        .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification);
    }

    void removeSummaryNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID);
    }
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        context = getApplicationContext();

        // hide the title bar
        // https://stackoverflow.com/questions/14475109/remove-android-app-title-bar
        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                observer.clearTimer();
                refreshList();
            }
        });

        setupObserver();

        // TODO: restore this one once I have fixed the dismiss notification button
        // TODO: see if it's correct to call this one here. I don't think the widget does it
        updateTaskObservers();
        Log.v("MYCALENDAR","Service val="+AgendaUpdateService.VAL);
                
        // TODO: let's see if I can shut down the service that is stealing my broadcasts
        //Context context = AgendaWidgetApplication.getContext();
        //context.stopService(new Intent(context, AgendaUpdateService.class));


        /*
        new Timer().scheduleAtFixedRate(new TimerTask() {
            int elapsedMin=0;
            boolean firstRun=true;
            @Override
            public void run() {
                if (firstRun) {
                    firstRun=false;
                    return;
                }

                if (++elapsedMin>=ExtendedCalendars.LOOKAHEAD_MINUTES) {
                    elapsedMin=0;
                    refreshList();
                }
                else {
                    refreshListCached();
                }
            }
        }, 0, (60L*1000L)); // every 1 min
        */
    }

    static int numTriggeredEvents() {
        int cnt=0;
        for(EventItem e : AgendaUpdateService.events) {
            if ((!(e instanceof ExtendedCalendarEvent)) || ((ExtendedCalendarEvent)e).isTriggered()) cnt++;
        }
        return(cnt);
    }

    static boolean updateInProgress=false;
    public void refreshList() {
        synchronized(this) {
            if (updateInProgress) {
                Log.w("MYCALENDAR", "refreshList(): update already in progress; ignoring the additional refreshList request");
                return;
            }
            updateInProgress = true;
        }
        Log.w("MYCALENDAR","about to get events");
        AgendaUpdateService.events = Events.getEvents(appWidgetId);
        Log.w("MYCALENDAR","got events: "+AgendaUpdateService.events.size()+"; triggered="+numTriggeredEvents());

        // Create the notifications
        AgendaUpdateService.newNotifications=new ArrayList<>();
        for(EventItem event : AgendaUpdateService.events) {
            if (event instanceof CalendarEvent) {
                CalendarEvent calendarEvent = (CalendarEvent) event;

                SpannableString spanDate = formatDate(context, calendarEvent, false); //new SpannableString(sb.toString());
                SpannableString spanTitle = formatTitle(context, calendarEvent); //new SpannableString(calendarEvent.getTitle());
                createNotification(calendarEvent.getId(), spanTitle.toString(), spanDate.toString());
                AgendaUpdateService.newNotifications.add(calendarEvent.getId());
            }
        }
        // Remove all leftover notifications
        for(Long eventId : AgendaUpdateService.currentNotifications) {
            if (!AgendaUpdateService.newNotifications.contains(eventId)) {
                removeNotification(context,eventId.longValue());
            }
        }
        AgendaUpdateService.currentNotifications=new ArrayList<>(AgendaUpdateService.newNotifications);
        if (AgendaUpdateService.currentNotifications.size()>1) {
            createSummaryNotification();
        }
        else {
            removeSummaryNotification();
        }

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                String[] vals=new String[numTriggeredEvents()];
                for(int i=0;i<vals.length;vals[i++]="");

                ListView l = findViewById(R.id.lvEvents);
                MySimpleArrayAdapter adapter = new MySimpleArrayAdapter(context,vals);
                l.setAdapter(adapter);
            }
        });
        synchronized(this) {
            updateInProgress = false;
        }
    }

    public void refreshListCached() {
        Log.w("MYCALENDAR","got cached events: "+AgendaUpdateService.events.size()+"; triggered="+numTriggeredEvents());

        String[] vals=new String[numTriggeredEvents()];
        for(int i=0;i<vals.length;vals[i++]="");

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                ListView l = findViewById(R.id.lvEvents);
                MySimpleArrayAdapter adapter = new MySimpleArrayAdapter(context,vals);
                l.setAdapter(adapter);
            }
        });
    }

    public void startSettings(View view) {
        int appWidgetId=1;
        Uri data = Uri.withAppendedPath(Uri.parse("agenda://widget/id/"), String.valueOf(appWidgetId));

        // Bind widget configuration button
        Intent configIntent = new Intent(context, AgendaWidgetConfigureActivity.class);
        configIntent.setData(data);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivity(configIntent);
    }

    public void runGetEvents(View view) {
        Log.w("MYCALENDAR","about to get events");
        //events = Events.getEvents(appWidgetId);
        observer.clearTimer();
        refreshList();
        Log.w("MYCALENDAR","got events: "+AgendaUpdateService.events.size());
    }

    private static void sendUpdate(Context context, Intent intent) {
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), MainActivity.class.getName());
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        Intent widgetUpdateIntent = new Intent();
        // TODO: see if I need to change the classes used below
        widgetUpdateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        widgetUpdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        if (!intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
            widgetUpdateIntent.putExtra(AgendaWidget.ACTION_FORCE_UPDATE, true);
        }

        context.sendBroadcast(widgetUpdateIntent);
    }


    public static class AgendaUpdateService extends Service {
        // TODO: remove VAL, it's only to understand how the static block works
        static int VAL=9999;
        private static final String ACTION_UPDATE = "gr.ictpro.jsalatas.agendawidget.action.UPDATE";

        private final static IntentFilter intentFilter;
        private static IntentFilter intentFilter2=null;

        private final static AgendaWidget.CalendarObserver calendarObserver = new AgendaWidget.CalendarObserver(new Handler());
        private static AgendaWidget.TaskObserver[] taskObservers;

        static BroadcastReceiver br = null;
        static List<Long> currentNotifications = new ArrayList<>();
        static List<Long> newNotifications = new ArrayList<>();
        static NotificationChannel notificationChannel=null;
        static List<EventItem> events = new ArrayList<>();
        static List<Long> notifiedEventIDs = new ArrayList<>();


        static {
            intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_TIME_TICK);
            intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
            intentFilter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
            intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            intentFilter.addDataScheme("package");
            intentFilter.addAction(ACTION_UPDATE);

            if (NEW_INTENTS) {
                intentFilter2 = new IntentFilter();
                intentFilter2.addAction(ACTION_VIEW);
                intentFilter2.addAction(ACTION_SNOOZE);
                intentFilter2.addAction(ACTION_DISMISS);
            }

            Log.v("MYCALENDAR","Service: inside static{} part of AgendaUpdateService");
            updateTaskObservers();
        }

        private final BroadcastReceiver agendaChangedReceiver = new
                BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Log.v("MYCALENDAR","Service onReceive()");
                        if (intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
                            // TODO: figure out if I need to restore these lines
                            //AgendaWidget.updateTaskObservers();
                        } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                            String packageName = intent.getData().toString().replace("package:", "");
                            // TODO: figure out if I need to restore these lines
                            //updateTaskProviders(context, packageName);
                            //MainActicity.updateTaskObservers();
                        } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                            // TODO: figure out if I need to restore these lines
                            //AgendaWidget.updateTaskObservers();
                        }

                        sendUpdate(context, intent);
                    }
                };


        private final BroadcastReceiver br2 = new
            BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(ACTION_DISMISS)) {
                        int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                        if (id==-1) return;
                        Log.v("MYCALENDAR","got dismiss for id="+id);
                        long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                        if (eventId==-1) return;
                        Log.v("MYCALENDAR","got dismiss for eventid="+eventId);

                        for(EventItem item : AgendaUpdateService.events) {
                            if (item instanceof ExtendedCalendarEvent) {
                                ExtendedCalendarEvent event=(ExtendedCalendarEvent)item;
                                if (event.getId()==eventId) {
                                    ExtendedCalendars.dismissCalDAVEventReminders(event);
                                    removeNotification(context,event.getId());
                                    // TODO: find a way to update the app's main window from here. Probably an intent?
                                    /*
                                    synchronized (this) {
                                        AgendaUpdateService.events = refreshOneEvent(appWidgetId, event.getId(), AgendaUpdateService.events);
                                        refreshListCached();
                                    }
                                    */
                                    return;
                                }
                            }
                        }
                        // TODO: this should never happen
                        Log.e("MYCALENDAR","Dismiss clicked for event with id="+eventId+", but no such id was found in events. Ignoring");
                    }
                    else if (intent.getAction().equals(ACTION_VIEW)) {
                        //int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                        //if (id==-1) return;
                        long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                        if (eventId==-1) return;
                        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                                .setData(uri);
                        startActivity(viewIntent);
                    }
                    else {
                        Log.v("MYCALENDAR","Received a broadcast!");
                        StringBuilder sb = new StringBuilder();
                        sb.append("Action: " + intent.getAction() + "\n");
                        sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                        sb.append("Extras:"+intent.getExtras());
                        String log = sb.toString();
                        Log.d("MYCALENDAR", log);
                        Toast.makeText(context, log, Toast.LENGTH_LONG).show();
                    }
                }
            };

        @Override
        public void onDestroy() {
            try {
                unregisterReceiver(agendaChangedReceiver);
            } catch (IllegalArgumentException e) {
                // java.lang.IllegalArgumentException: Receiver not registered: gr.ictpro.jsalatas.agendawidget.ui.AgendaWidget$AgendaUpdateService
                // do nothing
            }
            for (AgendaWidget.TaskObserver taskObserver : taskObservers) {
                getContentResolver().unregisterContentObserver(taskObserver);
            }
            getContentResolver().unregisterContentObserver(calendarObserver);
            super.onDestroy();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.v("MYCALENDAR","Service started");
            try {
                getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver);
            } catch (SecurityException e) {
                // java.lang.SecurityException: Permission Denial: opening provider com.android.providers.calendar.CalendarProvider2
                Context context = this; //AgendaWidgetApplication.getContext();
                Toast toast = Toast.makeText(context, context.getString(R.string.select_calendars), Toast.LENGTH_LONG);
                toast.show();
            }
            for (AgendaWidget.TaskObserver taskObserver : taskObservers) {
                getContentResolver().registerContentObserver(Uri.parse("content://" + taskObserver.uri), true, taskObserver);
            }
            registerReceiver(agendaChangedReceiver, intentFilter);
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_UPDATE)) {
                    agendaChangedReceiver.onReceive(this, intent);
                }
            }

            /*
            TEMPORARY ATTEMPT WITH PERMANENT BROADCASTRECEIVER
             */
            if (NEW_INTENTS) {
                Log.v("MYCALENDAR","Registering receiver br2!!!");
                registerReceiver(br2, intentFilter2);
            }
            /*===*/
            return START_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    public synchronized static void updateTaskObservers() {
        List<TaskContract> providersInUser = TaskProvider.getProvidersInUse();
        List<String> taskProviderURIs = new ArrayList<>();
        for (TaskContract t : providersInUser) {
            try {
                if (!t.getProviderURI().isEmpty() && TaskProviderListAdapter.providerExists(t)) {
                    taskProviderURIs.add(t.getProviderURI());
                }
            } catch (SecurityException e) {
                // Do nothing
            }
        }

        Context context = AgendaWidgetApplication.getContext();
        context.stopService(new Intent(context, AgendaUpdateService.class));

        AgendaUpdateService.taskObservers = new AgendaWidget.TaskObserver[taskProviderURIs.size()];
        int i = 0;
        for (String uri : taskProviderURIs) {
            AgendaUpdateService.taskObservers[i] = new AgendaWidget.TaskObserver(new Handler(), uri);
            i++;
        }

        context.startForegroundService(new Intent(context, AgendaUpdateService.class));
        context.startService(new Intent(context, AgendaUpdateService.class));
    }
}
