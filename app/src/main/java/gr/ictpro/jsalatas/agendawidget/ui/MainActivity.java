
package gr.ictpro.jsalatas.agendawidget.ui;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.app.ActivityCompat;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

public class MainActivity extends AppCompatActivity {
    private static Context context;
    static final String ACTION_FORCE_UPDATE = "gr.ictpro.jsalatas.agendawidget.action.FORCE_UPDATE";
    private static final String ACTION_PROVIDER_REMOVED = "gr.ictpro.jsalatas.agendawidget.action.PROVIDER_REMOVED";
    private static final String EXTRA_PACKAGE_NAME = "gr.ictpro.jsalatas.agendawidget.action.EXTRA_PACKAGE_NAME";

    List<EventItem> events = new ArrayList<>();
    public static int appWidgetId=1;

    // Stay open when keyboard is connected/disconnected
    // https://stackoverflow.com/questions/4116058/avoiding-application-restart-when-hardware-keyboard-opens
    @Override
    public void onConfigurationChanged(final Configuration newConfig)
    {
        // Ignore orientation change to keep activity from restarting
        super.onConfigurationChanged(newConfig);
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

            if (position>=events.size())
                return(inflater.inflate(R.layout.calendar_event_layout, parent, false));

            EventItem item = events.get(position);

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

                StringBuilder sb = new StringBuilder();
                //boolean startIsToday = DateUtils.isInSameDay(calendarEvent.getStartDate(), now);
                boolean startIsToday = false;
                boolean addSpace = false;

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
                if (!sb.toString().isEmpty()) {
                    sb.append(": ");
                }

                SpannableString spanDate = new SpannableString(sb.toString());
                SpannableString spanTitle = new SpannableString(calendarEvent.getTitle());
                if (Settings.getBoolPref(appContext, "todayBold", appWidgetId) && isToday) {
                    spanDate.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.toString().length(), 0);
                    spanTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, calendarEvent.getTitle().length(), 0);
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
                        EventItem item = events.get(position);
                    }
                });
                Button btn=((Button) v.findViewById(R.id.btn_dismiss));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dismiss button clicked for entry #"+position);
                        EventItem item = events.get(position);

                        if (item instanceof ExtendedCalendarEvent) {
                            ExtendedCalendars.dismissCalDAVEventReminders(item);
                            events.remove(position);
                            refreshListCached();
                        }
                    }
                });
                btn=((Button) v.findViewById(R.id.btn_dump));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dump button clicked for entry #"+position);
                        EventItem item = events.get(position);
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
                        EventItem item = events.get(position);
                        if (item instanceof ExtendedCalendarEvent) {
                            ((ExtendedCalendarEvent)item).runTests();
                        }
                    }
                });
            }
            return(v);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        context = getApplicationContext();

        // hide the title bar
        // https://stackoverflow.com/questions/14475109/remove-android-app-title-bar
        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        refreshList();

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

    }

    int numTriggeredEvents() {
        int cnt=0;
        for(EventItem e : events) {
            if ((!(e instanceof ExtendedCalendarEvent)) || ((ExtendedCalendarEvent)e).isTriggered()) cnt++;
        }
        return(cnt);
    }

    public void refreshList() {
        Log.w("MYCALENDAR","about to get events");
        events = Events.getEvents(appWidgetId);
        Log.w("MYCALENDAR","got events: "+events.size()+"; triggered="+numTriggeredEvents());

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

    public void refreshListCached() {
        Log.w("MYCALENDAR","got cached events: "+events.size()+"; triggered="+numTriggeredEvents());

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
        refreshList();
        Log.w("MYCALENDAR","got events: "+events.size());
    }
}
